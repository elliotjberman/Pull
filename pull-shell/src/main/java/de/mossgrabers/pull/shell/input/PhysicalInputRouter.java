// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.input;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * Stable-shell router for a fixed physical-control canopy.
 * <p>
 * Edge ownership is sampled at BEGIN and retained through LONG and END. In particular, an
 * EXCLUSIVE press remains exclusive across a core reload or route-map change, so its release can
 * never leak into a stable command which did not receive the press. The active core generation is
 * captured with that ownership, so a gesture cannot complete against a core loaded after its
 * press. Motion is bounded to one
 * pending sample per registered control-and-kind pair: relative deltas are summed and absolute
 * values keep only their latest sample.
 * </p>
 * <p>
 * This class is intentionally independent of Bitwig hardware classes and reloadable-core DTOs.
 * The controller setup can pass a button's complete stable dispatch or a continuous control's
 * existing command into the {@link #route} callback, then adapt the event sink and route resolver
 * at the permanent shell/API boundary. It is controller-thread confined.
 * </p>
 *
 * @param <C> Control key type
 */
public final class PhysicalInputRouter<C>
{
    private final PhysicalControlRegistry<C> registry;
    private final BiFunction<? super C, ? super InputKind, InputRoute> routeResolver;
    private final Consumer<? super PhysicalInputEvent<C>> eventSink;
    private final LongSupplier nanoTime;
    private final LongSupplier ownerGeneration;
    private final Map<PhysicalInputAddress<C>, GestureBinding> gestureBindings;
    private final Map<PhysicalInputAddress<C>, PhysicalInputEvent<C>> pendingMotion;
    private long nextSequence = 1;


    /**
     * Create a router using {@link System#nanoTime()} for sample timestamps.
     *
     * @param registry Fixed physical-control registry
     * @param routeResolver Current route-map adapter; null results are treated as NONE
     * @param eventSink Reloadable-consumer event adapter
     */
    public PhysicalInputRouter (final PhysicalControlRegistry<C> registry, final BiFunction<? super C, ? super InputKind, InputRoute> routeResolver, final Consumer<? super PhysicalInputEvent<C>> eventSink)
    {
        this (registry, routeResolver, eventSink, System::nanoTime, () -> 0);
    }


    PhysicalInputRouter (final PhysicalControlRegistry<C> registry, final BiFunction<? super C, ? super InputKind, InputRoute> routeResolver, final Consumer<? super PhysicalInputEvent<C>> eventSink, final LongSupplier nanoTime)
    {
        this (registry, routeResolver, eventSink, nanoTime, () -> 0);
    }


    /**
     * Create a router with explicit clocks for deterministic generation-fenced delivery.
     *
     * @param registry Fixed physical-control registry
     * @param routeResolver Current route-map adapter; null results are treated as NONE
     * @param eventSink Reloadable-consumer event adapter
     * @param nanoTime Monotonic timestamp supplier
     * @param ownerGeneration Current active reloadable-core generation
     */
    public PhysicalInputRouter (final PhysicalControlRegistry<C> registry, final BiFunction<? super C, ? super InputKind, InputRoute> routeResolver, final Consumer<? super PhysicalInputEvent<C>> eventSink, final LongSupplier nanoTime, final LongSupplier ownerGeneration)
    {
        this.registry = Objects.requireNonNull (registry, "registry");
        this.routeResolver = Objects.requireNonNull (routeResolver, "routeResolver");
        this.eventSink = Objects.requireNonNull (eventSink, "eventSink");
        this.nanoTime = Objects.requireNonNull (nanoTime, "nanoTime");
        this.ownerGeneration = Objects.requireNonNull (ownerGeneration, "ownerGeneration");
        this.gestureBindings = new HashMap<> (registry.capacity ());
        this.pendingMotion = new HashMap<> (registry.capacity ());
    }


    /**
     * Route one physical sample and invoke the existing stable command according to current input
     * ownership. The stable callback runs exactly once for NONE and OBSERVE, and never for
     * EXCLUSIVE. Edge events are delivered immediately; motion is delivered by {@link #flush()}.
     *
     * @param control Registered control
     * @param kind Registered input kind
     * @param phase Input phase
     * @param value Raw or decoded value
     * @param stableCommand Existing stable controller command
     * @return The route applied to this sample
     */
    public InputRoute route (final C control, final InputKind kind, final InputPhase phase, final long value, final Runnable stableCommand)
    {
        Objects.requireNonNull (phase, "phase");
        Objects.requireNonNull (stableCommand, "stableCommand");
        final PhysicalInputAddress<C> input = this.registry.require (control, kind);
        if (kind.isEdge () == (phase == InputPhase.CHANGE))
            throw new IllegalArgumentException ("phase " + phase + " is invalid for " + kind);
        return kind.isEdge () ? this.routeEdge (input, phase, value, stableCommand) : this.routeMotion (input, phase, value, stableCommand);
    }


    /**
     * Deliver every pending coalesced motion sample in physical sequence order. If a consumer
     * throws, the failed sample remains consumed while later samples remain pending for retry.
     */
    public void flush ()
    {
        final List<PhysicalInputEvent<C>> samples = new ArrayList<> (this.pendingMotion.values ());
        samples.sort (Comparator.comparingLong (PhysicalInputEvent::sequence));
        for (final PhysicalInputEvent<C> sample: samples)
        {
            final PhysicalInputAddress<C> input = this.registry.require (sample.control (), sample.kind ());
            if (this.pendingMotion.remove (input, sample))
                this.eventSink.accept (sample);
        }
    }


    /**
     * Deliver one control's pending motion, if any. This is useful before routing a related touch
     * edge whose ordering must follow the last encoder or ribbon value.
     *
     * @param control Registered control
     * @param kind Registered input kind
     */
    public void flush (final C control, final InputKind kind)
    {
        final PhysicalInputAddress<C> input = this.registry.require (control, kind);
        final PhysicalInputEvent<C> sample = this.pendingMotion.remove (input);
        if (sample != null)
            this.eventSink.accept (sample);
    }


    /**
     * Get the number of bounded motion entries awaiting delivery.
     *
     * @return Pending motion-control count
     */
    public int pendingMotionCount ()
    {
        return this.pendingMotion.size ();
    }


    /**
     * Get the number of currently leased edge gestures.
     *
     * @return Active gesture count
     */
    public int activeGestureCount ()
    {
        return this.gestureBindings.size ();
    }


    private PhysicalInputEvent<C> newEvent (final PhysicalInputAddress<C> input, final InputPhase phase, final long value, final long generation)
    {
        if (this.nextSequence == Long.MAX_VALUE)
            throw new IllegalStateException ("Physical input sequence exhausted");
        return new PhysicalInputEvent<> (this.nextSequence++, this.nanoTime.getAsLong (), generation, input.control (), input.kind (), phase, value);
    }


    private InputRoute routeEdge (final PhysicalInputAddress<C> input, final InputPhase phase, final long value, final Runnable stableCommand)
    {
        final GestureBinding binding;
        if (phase == InputPhase.BEGIN)
        {
            final GestureBinding existing = this.gestureBindings.get (input);
            binding = existing == null ? new GestureBinding (this.resolveRoute (input), this.ownerGeneration.getAsLong ()) : existing;
            if (existing == null)
                this.gestureBindings.put (input, binding);
        }
        else
            binding = this.gestureBindings.getOrDefault (input, GestureBinding.NONE);

        final PhysicalInputEvent<C> event = this.newEvent (input, phase, value, binding.generation ());
        try
        {
            this.deliverEdge (binding.route (), event, stableCommand);
            return binding.route ();
        }
        finally
        {
            if (event.phase () == InputPhase.END)
                this.gestureBindings.remove (input);
        }
    }


    private InputRoute routeMotion (final PhysicalInputAddress<C> input, final InputPhase phase, final long value, final Runnable stableCommand)
    {
        final InputRoute route = this.resolveRoute (input);
        if (route != InputRoute.EXCLUSIVE)
            stableCommand.run ();
        if (route != InputRoute.NONE)
            this.coalesce (input, this.newEvent (input, phase, value, this.ownerGeneration.getAsLong ()));
        return route;
    }


    private void deliverEdge (final InputRoute route, final PhysicalInputEvent<C> event, final Runnable stableCommand)
    {
        if (route != InputRoute.EXCLUSIVE)
            stableCommand.run ();
        if (route != InputRoute.NONE)
            this.eventSink.accept (event);
    }


    private void coalesce (final PhysicalInputAddress<C> input, final PhysicalInputEvent<C> event)
    {
        if (event.kind ().sumsRelativeValues ())
        {
            final PhysicalInputEvent<C> previous = this.pendingMotion.get (input);
            final long value = previous == null || previous.ownerGeneration () != event.ownerGeneration () ? event.value () : Math.addExact (previous.value (), event.value ());
            if (value == 0)
            {
                this.pendingMotion.remove (input);
                return;
            }
            this.pendingMotion.put (input, event.withValue (value));
            return;
        }
        if (event.kind ().keepsLatestValue ())
        {
            this.pendingMotion.put (input, event);
            return;
        }
        throw new IllegalStateException ("No coalescing policy for " + event.kind ());
    }


    private InputRoute resolveRoute (final PhysicalInputAddress<C> input)
    {
        final InputRoute route = this.routeResolver.apply (input.control (), input.kind ());
        return route == null ? InputRoute.NONE : route;
    }


    private record GestureBinding (InputRoute route, long generation)
    {
        private static final GestureBinding NONE = new GestureBinding (InputRoute.NONE, 0);


        private GestureBinding
        {
            Objects.requireNonNull (route, "route");
            if (generation < 0)
                throw new IllegalArgumentException ("generation must not be negative");
        }
    }
}
