// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreCapabilities;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredInputRoutes;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.effect.ClipLaunchPolicy;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
import de.mossgrabers.pull.core.api.effect.SetTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.SetTransportValueEffect;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Predicate;


/**
 * Stable-shell state and effect boundary for reloadable selected-track controller behavior.
 */
final class DrumFillRuntimeEnvironment implements CoreRuntimeEnvironment
{
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final ShellCapabilities CAPABILITIES = new ShellCapabilities (Map.ofEntries (
        Map.entry (CoreCapabilities.INPUT_DRUM_FILL, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.SNAPSHOT_SELECTED_TRACK_CLIPS, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.BINDING_CLIP_TARGET, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.SNAPSHOT_CLIP_LAUNCH_SESSION, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.EFFECT_CLIP_LAUNCH_HOLD, Integer.valueOf (4)),
        Map.entry (CoreCapabilities.OUTPUT_RGB_LIGHT, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.INPUT_CONTROLLER, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.ROUTING_CONTROLLER_INPUT, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.SNAPSHOT_CONTROLLER_BRIDGE, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.SUBSCRIPTION_CONTROLLER_BRIDGE, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.EFFECT_TRANSPORT, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.EFFECT_SELECTED_TRACK, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.EFFECT_DRUM_PAD, Integer.valueOf (1)),
        Map.entry (CoreCapabilities.EFFECT_NOTE_INPUT_MIDI, Integer.valueOf (1))));

    private final DrumFillClipHost clipHost;
    private final BoundedControllerBridge controllerBridge;
    private final RuntimeLog log;
    private final LongSupplier clock;
    private final long timeOrigin;
    private final Set<ControlId> pressedControls = new LinkedHashSet<> ();
    private final Set<ControlId> touchedControls = new LinkedHashSet<> ();
    private final FillLaunchSession fillSession = new FillLaunchSession ();

    private ClipCatalogSnapshot clipCatalog;
    private Map<ControlId, ClipTargetId> armedClipTargets;
    private FillSessionView lastObservedSession;
    private Map<ControlId, RgbColor> fillLightColors = offLights ();
    private DesiredInputRoutes desiredInputRoutes = DesiredInputRoutes.empty ();
    private DesiredBridgeSubscriptions desiredBridgeSubscriptions = DesiredBridgeSubscriptions.empty ();
    private Predicate<de.mossgrabers.pull.core.api.InputRoute> inputRouteValidator = route -> false;
    private long pendingSnapshotRevision = -1;
    private long hostSampleRevision;
    private long lastTime;
    private long revision;
    private long eventSequence;
    private long outputGeneration;
    private long committedGeneration;
    private PreparedResult committedResult;


    /**
     * Constructor using the system monotonic clock.
     *
     * @param clipHost Stable selected-track clip host
     * @param log Stable runtime log
     */
    DrumFillRuntimeEnvironment (final DrumFillClipHost clipHost, final RuntimeLog log)
    {
        this (clipHost, log, System::nanoTime);
    }


    /**
     * Constructor with a deterministic clock seam.
     *
     * @param clipHost Stable selected-track clip host
     * @param log Stable runtime log
     * @param clock Monotonic clock
     */
    DrumFillRuntimeEnvironment (final DrumFillClipHost clipHost, final RuntimeLog log, final LongSupplier clock)
    {
        this (clipHost, null, log, clock);
    }


    /**
     * Constructor with the permanent common-controller bridge.
     *
     * @param clipHost Stable selected-track clip host
     * @param controllerBridge Bounded common controller bridge
     * @param log Stable runtime log
     * @param clock Monotonic clock
     */
    DrumFillRuntimeEnvironment (final DrumFillClipHost clipHost, final BoundedControllerBridge controllerBridge, final RuntimeLog log, final LongSupplier clock)
    {
        this.clipHost = Objects.requireNonNull (clipHost, "clipHost");
        this.controllerBridge = controllerBridge;
        this.log = Objects.requireNonNull (log, "log");
        this.clock = Objects.requireNonNull (clock, "clock");
        this.timeOrigin = clock.getAsLong ();
        this.clipCatalog = Objects.requireNonNull (clipHost.clipCatalog (), "initial clip catalog");
        this.armedClipTargets = copyHostBindings (clipHost.armedClipTargets ());
        this.lastObservedSession = this.fillSession.view ();
    }


    /** {@inheritDoc} */
    @Override
    public ControllerSnapshot snapshot ()
    {
        return this.createSnapshot (this.now ());
    }


    /**
     * Advance the stable scanner, actuator pool, and retained cleanup work.
     *
     * @return True when the immutable public snapshot changed
     */
    boolean refresh ()
    {
        this.clipHost.refresh ();
        this.hostSampleRevision = Math.incrementExact (this.hostSampleRevision);
        this.fillSession.advance (this.hostSampleRevision);
        final ClipCatalogSnapshot refreshedCatalog = Objects.requireNonNull (this.clipHost.clipCatalog (), "refreshed clip catalog");
        final Map<ControlId, ClipTargetId> refreshedArmedTargets = copyHostBindings (this.clipHost.armedClipTargets ());
        if (!refreshedCatalog.equals (this.clipCatalog) || !refreshedArmedTargets.equals (this.armedClipTargets))
        {
            this.clipCatalog = refreshedCatalog;
            this.armedClipTargets = refreshedArmedTargets;
            this.recordSnapshotChange ();
        }
        this.recordSessionChange ();
        if (this.controllerBridge != null && this.controllerBridge.refresh (this.now (), this.desiredBridgeSubscriptions))
            this.recordSnapshotChange ();

        return this.hasPendingSnapshotChange ();
    }


    /**
     * Create an event for a previously observed snapshot change.
     *
     * @return The event
     */
    SnapshotChangedEvent snapshotChangedEvent ()
    {
        return new SnapshotChangedEvent (this.nextEventSequence (), this.now ());
    }


    /**
     * Get the latest immutable snapshot revision available for delivery.
     *
     * @return Current snapshot revision
     */
    long snapshotRevision ()
    {
        return this.revision;
    }


    /**
     * Acknowledge that a core accepted a snapshot containing the supplied revision. A newer
     * change raised while that core result was applied remains pending for a later delivery.
     *
     * @param deliveredRevision Latest revision contained in the accepted snapshot
     */
    void acknowledgeSnapshotChange (final long deliveredRevision)
    {
        if (deliveredRevision < 0)
            throw new IllegalArgumentException ("deliveredRevision must not be negative");
        if (this.hasPendingSnapshotChange () && deliveredRevision >= this.pendingSnapshotRevision)
            this.pendingSnapshotRevision = -1;
    }


    /**
     * Update one authoritative drum-fill input and create its normalized event.
     *
     * @param owner Logical fill-pad owner
     * @param pressed True while that physical pad is held
     * @return The event
     */
    ButtonInputEvent setFillPressed (final ControlId owner, final boolean pressed)
    {
        final ControlId fillOwner = requireFillOwner (owner);
        final boolean changed = pressed ? this.pressedControls.add (fillOwner) : this.pressedControls.remove (fillOwner);
        if (changed)
            this.advanceSnapshotRevision ();
        return new ButtonInputEvent (this.nextEventSequence (), this.now (), fillOwner, pressed);
    }


    /**
     * Apply one normalized generic physical-input sample to parent-owned gesture state.
     *
     * @param control Physical control
     * @param kind Input kind
     * @param phase Input phase
     * @param value Normalized kind-specific value
     * @return Core event with the environment's globally monotonic sequence and clock
     */
    ControllerInputEvent controllerInput (final ControlId control, final InputKind kind, final InputPhase phase, final long value)
    {
        final ControlId checkedControl = Objects.requireNonNull (control, "control");
        final InputKind checkedKind = Objects.requireNonNull (kind, "kind");
        final InputPhase checkedPhase = Objects.requireNonNull (phase, "phase");
        final Set<ControlId> state = checkedKind == InputKind.TOUCH ? this.touchedControls : checkedKind == InputKind.BUTTON || checkedKind == InputKind.PAD || checkedKind == InputKind.PEDAL ? this.pressedControls : null;
        if (state != null)
        {
            final boolean changed;
            if (checkedPhase == InputPhase.BEGIN)
                changed = state.add (checkedControl);
            else if (checkedPhase == InputPhase.END)
                changed = state.remove (checkedControl);
            else
                changed = false;
            if (changed)
                this.advanceSnapshotRevision ();
        }
        return new ControllerInputEvent (this.nextEventSequence (), this.now (), checkedControl, checkedKind, checkedPhase, value);
    }


    /**
     * Test whether one physical fill pad is held.
     *
     * @param owner Logical fill-pad owner
     * @return True when held
     */
    boolean isFillPressed (final ControlId owner)
    {
        return this.pressedControls.contains (requireFillOwner (owner));
    }


    /**
     * Safely release an active or pending fill even when the reloadable core did not handle the
     * physical UP event.
     *
     * @param owner Logical owner to release
     */
    void safetyRelease (final ControlId owner)
    {
        final ControlId fillOwner = requireFillOwner (owner);
        this.fillSession.requestRelease (fillOwner, "Safety release", this.hostSampleRevision);
        this.recordSessionChange ();

        if (this.pressedControls.remove (fillOwner))
            this.advanceSnapshotRevision ();
    }


    /**
     * Get one buffered, hardware-independent fill light color.
     *
     * @param owner Logical fill-pad owner
     * @return Desired color
     */
    RgbColor fillLightColor (final ControlId owner)
    {
        return this.fillLightColors.get (requireFillOwner (owner));
    }


    /**
     * Get the core generation that last replaced the complete output buffer.
     *
     * @return The output generation
     */
    long outputGeneration ()
    {
        return this.outputGeneration;
    }


    /**
     * Get the complete committed generic input routing table.
     *
     * @return Desired input routes
     */
    DesiredInputRoutes desiredInputRoutes ()
    {
        return this.desiredInputRoutes;
    }


    /**
     * Install validation against the fixed physical registry before the runtime starts.
     *
     * @param validator True only for registered control-and-kind pairs
     */
    void setInputRouteValidator (final Predicate<de.mossgrabers.pull.core.api.InputRoute> validator)
    {
        if (this.committedGeneration != 0)
            throw new IllegalStateException ("Input-route validation must be installed before core activation");
        this.inputRouteValidator = Objects.requireNonNull (validator, "validator");
    }


    /** {@inheritDoc} */
    @Override
    public PreparedCoreResult prepare (final CoreResult result)
    {
        Objects.requireNonNull (result, "result");
        final Map<ControlId, RgbColor> preparedColors = prepareOutput (result);
        for (final de.mossgrabers.pull.core.api.InputRoute route: result.desiredInputRoutes ().routes ())
        {
            if (!this.inputRouteValidator.test (route))
                throw new IllegalArgumentException ("Core requested an unregistered controller input route");
        }
        final Map<ControlId, ClipTargetId> preparedBindings = prepareBindings (result.desiredClipBindings (), this.clipCatalog);
        final List<PreparedAction> preparedActions = this.prepareEffects (result.effects (), preparedBindings);
        return new PreparedResult (preparedColors, result.desiredInputRoutes (), result.desiredBridgeSubscriptions (), this.clipCatalog.generation (), preparedBindings, preparedActions);
    }


    /** {@inheritDoc} */
    @Override
    public void commit (final long generation, final PreparedCoreResult result)
    {
        final PreparedResult prepared = (PreparedResult) result;
        this.committedResult = prepared;
        this.committedGeneration = generation;
        this.fillLightColors = prepared.fillLightColors ();
        this.desiredInputRoutes = prepared.desiredInputRoutes ();
        this.desiredBridgeSubscriptions = prepared.desiredBridgeSubscriptions ();
        this.outputGeneration = generation;
        if (this.controllerBridge != null)
            this.controllerBridge.activateCoreGeneration (generation);
    }


    /** {@inheritDoc} */
    @Override
    public void apply (final long generation)
    {
        if (generation != this.committedGeneration)
            return;

        final PreparedResult prepared = this.committedResult;
        if (prepared == null)
            return;
        this.committedResult = null;

        this.clipHost.setDesiredBindings (prepared.catalogGeneration (), prepared.desiredClipBindings ());
        boolean acquisitionFailed = false;
        for (final PreparedAction action: prepared.actions ())
        {
            if (action instanceof final PreparedPress press)
            {
                if (!acquisitionFailed)
                    acquisitionFailed = !this.applyPress (press);
            }
            else if (action instanceof final PreparedRelease release)
                this.applyRelease (release);
            else if (action instanceof final PreparedBridgeAction bridgeAction && this.controllerBridge != null)
                this.controllerBridge.apply (bridgeAction.action ());
        }
        this.recordSessionChange ();
    }


    /** {@inheritDoc} */
    @Override
    public void invalidate (final long generation)
    {
        this.committedResult = null;
        this.committedGeneration = generation;
        this.fillLightColors = offLights ();
        this.desiredInputRoutes = DesiredInputRoutes.empty ();
        this.desiredBridgeSubscriptions = DesiredBridgeSubscriptions.empty ();
        this.outputGeneration = generation;

        if (this.controllerBridge != null)
            this.controllerBridge.invalidate ();

        try
        {
            this.clipHost.setDesiredBindings (this.clipCatalog.generation (), Map.of ());
        }
        catch (final RuntimeException failure)
        {
            this.warn ("Clearing fill bindings during invalidation failed: " + sanitize (failure));
        }

        // Runtime invalidation is terminal extension shutdown, not an ordinary child-core reload.
        // API 21 offers no post-exit observation window, so this can only submit one best-effort
        // Return for the active fill; normal running handoffs finish from later refreshes.
        this.fillSession.invalidate (this.hostSampleRevision);
        this.recordSessionChange ();

        if (!this.pressedControls.isEmpty ())
        {
            this.pressedControls.clear ();
            this.advanceSnapshotRevision ();
        }
        if (!this.touchedControls.isEmpty ())
        {
            this.touchedControls.clear ();
            this.advanceSnapshotRevision ();
        }
    }


    private static Map<ControlId, RgbColor> prepareOutput (final CoreResult result)
    {
        final Map<ControlId, RgbColor> colors = new LinkedHashMap<> (offLights ());
        for (final Map.Entry<ControlId, RgbColor> light: result.desiredOutput ().lights ().entrySet ())
        {
            final ControlId owner = requireFillOwner (light.getKey ());
            final RgbColor requested = Objects.requireNonNull (light.getValue (), "fill light color");
            colors.put (owner, new RgbColor (requested.red (), requested.green (), requested.blue ()));
        }
        return Map.copyOf (colors);
    }


    private Map<ControlId, ClipTargetId> prepareBindings (final Map<ControlId, ClipTargetId> bindings, final ClipCatalogSnapshot clipCatalog)
    {
        final Map<ControlId, ClipTargetId> copy = new LinkedHashMap<> ();
        final Set<ClipTargetId> catalogTargets = new HashSet<> ();
        clipCatalog.clips ().forEach (clip -> catalogTargets.add (clip.targetId ()));
        final Set<ClipTargetId> targets = new HashSet<> ();
        for (final Map.Entry<ControlId, ClipTargetId> binding: bindings.entrySet ())
        {
            final ControlId owner = requireFillOwner (binding.getKey ());
            final ClipTargetId requested = Objects.requireNonNull (binding.getValue (), "desired clip target");
            final ClipTargetId target = new ClipTargetId (requested.value ());
            if (!catalogTargets.contains (target))
                throw new IllegalArgumentException ("Desired binding targets an unknown catalog clip");
            if (!targets.add (target))
                throw new IllegalArgumentException ("Desired clip targets must be unique");
            copy.put (owner, target);
        }

        for (final Map.Entry<ControlId, ClipTargetId> retained: this.fillSession.targets ().entrySet ())
        {
            for (final Map.Entry<ControlId, ClipTargetId> binding: copy.entrySet ())
            {
                if (!retained.getKey ().equals (binding.getKey ()) && retained.getValue ().equals (binding.getValue ()))
                    throw new IllegalArgumentException ("A retained clip target cannot be rebound to another fill control");
            }
        }
        return Map.copyOf (copy);
    }


    private List<PreparedAction> prepareEffects (final List<CoreEffect> effects, final Map<ControlId, ClipTargetId> desiredBindings)
    {
        final Set<ControlId> owners = new HashSet<> ();
        final Set<String> bridgeTargets = new HashSet<> ();
        final List<PreparedAction> actions = new ArrayList<> (effects.size ());
        for (final CoreEffect effect: effects)
        {
            if (effect instanceof final PressClipTargetEffect press)
            {
                if (!owners.add (requireFillOwner (press.owner ())))
                    throw new IllegalArgumentException ("Core requested multiple clip effects for one owner");
                actions.add (this.preparePress (press, desiredBindings));
            }
            else if (effect instanceof final ReleaseClipTargetsEffect release)
            {
                if (!owners.add (requireFillOwner (release.owner ())))
                    throw new IllegalArgumentException ("Core requested multiple clip effects for one owner");
                actions.add (this.prepareRelease (release));
            }
            else
            {
                final CoreEffect checkedEffect = Objects.requireNonNull (effect, "effect");
                final String target = bridgeEffectTarget (checkedEffect);
                if (target != null && !bridgeTargets.add (target))
                    throw new IllegalArgumentException ("Core requested multiple effects for " + target);
                final BoundedControllerBridge.PreparedAction action = this.controllerBridge == null ? null : this.controllerBridge.prepare (checkedEffect);
                if (action == null)
                    throw new IllegalArgumentException ("Core requested an unsupported effect " + effect.getClass ().getSimpleName ());
                actions.add (new PreparedBridgeAction (action));
            }
        }
        return List.copyOf (actions);
    }


    private static String bridgeEffectTarget (final CoreEffect effect)
    {
        if (effect instanceof final SetTransportStateEffect state)
            return "transport state " + state.state ();
        if (effect instanceof final SetTransportValueEffect value)
            return "transport value " + value.value ();
        return null;
    }


    private PreparedPress preparePress (final PressClipTargetEffect effect, final Map<ControlId, ClipTargetId> desiredBindings)
    {
        final ControlId owner = requireFillOwner (effect.owner ());
        if (!this.pressedControls.contains (owner))
            throw new IllegalStateException ("The fill control is not physically held");
        if (effect.catalogGeneration () != this.clipCatalog.generation ())
            throw new IllegalArgumentException ("Clip-catalog generation is stale");

        final ClipTargetId targetId = new ClipTargetId (effect.target ().value ());
        for (final Map.Entry<ControlId, ClipTargetId> retained: this.fillSession.targets ().entrySet ())
        {
            if (!owner.equals (retained.getKey ()) && targetId.equals (retained.getValue ()))
                throw new IllegalStateException ("A retained clip target cannot be acquired by another fill control");
        }

        if (!targetId.equals (desiredBindings.get (owner)))
            throw new IllegalArgumentException ("New clip press must match this result's desired binding");
        if (!targetId.equals (this.armedClipTargets.get (owner)))
            throw new IllegalArgumentException ("Clip target is not armed for this fill control");

        // Preparing a Bitwig proxy here would reserve the replacement while the current fill is
        // still returning. Keep only an opaque intent; resolve the fresh proxy after base playback
        // has been observed again.
        return new PreparedPress (owner, effect.catalogGeneration (), targetId, effect.launchPolicy ());
    }


    private PreparedRelease prepareRelease (final ReleaseClipTargetsEffect effect)
    {
        final ControlId owner = requireFillOwner (effect.owner ());
        return new PreparedRelease (owner);
    }


    private static ControlId requireFillOwner (final ControlId owner)
    {
        Objects.requireNonNull (owner, "fill owner");
        if (!CoreControls.DRUM_FILLS.contains (owner))
            throw new IllegalArgumentException ("Unsupported fill owner");
        return owner;
    }


    private boolean applyPress (final PreparedPress press)
    {
        if (this.clipCatalog.generation () != press.catalogGeneration ())
        {
            this.warn ("Prepared fill press was discarded after the clip catalog changed");
            return false;
        }
        return this.fillSession.requestLaunch (new PendingLaunch (press.owner (), press.catalogGeneration (), press.targetId (), press.launchPolicy ()), this.hostSampleRevision);
    }


    private void applyRelease (final PreparedRelease release)
    {
        this.fillSession.requestRelease (release.owner (), "Fill-session release", this.hostSampleRevision);
    }


    private void advanceSnapshotRevision ()
    {
        this.revision = Math.incrementExact (this.revision);
    }


    private void recordSessionChange ()
    {
        final FillSessionView observed = this.fillSession.view ();
        if (observed.equals (this.lastObservedSession))
            return;

        this.lastObservedSession = observed;
        this.recordSnapshotChange ();
    }


    private void recordSnapshotChange ()
    {
        this.advanceSnapshotRevision ();
        this.pendingSnapshotRevision = this.revision;
    }


    private boolean hasPendingSnapshotChange ()
    {
        return this.pendingSnapshotRevision >= 0;
    }


    private ControllerSnapshot createSnapshot (final long monotonicTimeNanos)
    {
        final FillSessionView session = this.fillSession.view ();
        final ControllerBridgeSnapshot bridge = this.controllerBridge == null ? ControllerBridgeSnapshot.empty () : this.controllerBridge.snapshot ();
        return new ControllerSnapshot (this.revision, monotonicTimeNanos, CAPABILITIES, bridge, this.clipCatalog, this.armedClipTargets, session.targets (), session.activeOwner (), this.pressedControls, this.touchedControls);
    }


    private long nextEventSequence ()
    {
        this.eventSequence = Math.incrementExact (this.eventSequence);
        return this.eventSequence;
    }


    private long now ()
    {
        final long elapsed = Math.max (0, this.clock.getAsLong () - this.timeOrigin);
        this.lastTime = Math.max (this.lastTime, elapsed);
        return this.lastTime;
    }


    private void warn (final String message)
    {
        try
        {
            this.log.warn (message);
        }
        catch (final RuntimeException ignored)
        {
            // Logging cannot change stable-shell ownership or cleanup.
        }
    }


    private static Map<ControlId, ClipTargetId> copyHostBindings (final Map<ControlId, ClipTargetId> bindings)
    {
        final Map<ControlId, ClipTargetId> copy = new LinkedHashMap<> ();
        for (final Map.Entry<ControlId, ClipTargetId> binding: Objects.requireNonNull (bindings, "armed clip targets").entrySet ())
        {
            final ControlId owner = requireFillOwner (binding.getKey ());
            final ClipTargetId target = Objects.requireNonNull (binding.getValue (), "armed clip target");
            copy.put (owner, new ClipTargetId (target.value ()));
        }
        return Map.copyOf (copy);
    }


    private static Map<ControlId, RgbColor> offLights ()
    {
        final Map<ControlId, RgbColor> colors = new LinkedHashMap<> ();
        for (final ControlId owner: CoreControls.DRUM_FILLS)
            colors.put (owner, OFF);
        return Map.copyOf (colors);
    }


    private static String sanitize (final Throwable failure)
    {
        final String message = failure.getMessage ();
        return failure.getClass ().getSimpleName () + (message == null || message.isBlank () ? "" : ": " + message);
    }


    private static void rethrowFatal (final Throwable failure)
    {
        if (failure instanceof final VirtualMachineError virtualMachineError)
            throw virtualMachineError;
    }


    /**
     * One active fill above the opaque playback state that Bitwig owned before the gesture. A
     * replacement waits for the active fill to Return completely before it launches, so every
     * fill inherits the same Bitwig-owned base instead of another fill.
     */
    private final class FillLaunchSession
    {
        private LaunchLease active;
        private PendingLaunch pending;
        private boolean returnRequested;
        private boolean activeObservedBusy;
        private long launchAfterSample = -1;
        private DrumFillClipHost.PlaybackState activePlayback = new DrumFillClipHost.PlaybackState (false, false, false);
        private PendingRelease pendingRelease;
        private String pendingOperation = "Fill-session return";


        private Map<ControlId, ClipTargetId> targets ()
        {
            final Map<ControlId, ClipTargetId> targets = new LinkedHashMap<> ();
            if (this.active != null)
                targets.put (this.active.owner (), this.active.targetId ());
            return Map.copyOf (targets);
        }


        private boolean requestLaunch (final PendingLaunch requested, final long sampleRevision)
        {
            this.pending = Objects.requireNonNull (requested, "requested");
            if (this.active == null)
                return sampleRevision <= this.launchAfterSample || this.launchPending (sampleRevision);

            this.requestActiveReturn ("Fill replacement", sampleRevision);
            return true;
        }


        private void requestRelease (final ControlId owner, final String operation, final long sampleRevision)
        {
            if (this.pending != null && owner.equals (this.pending.owner ()))
                this.pending = null;
            if (this.active != null && owner.equals (this.active.owner ()))
                this.requestActiveReturn (operation, sampleRevision);
        }


        private void invalidate (final long sampleRevision)
        {
            this.pending = null;
            if (this.active != null)
            {
                this.returnRequested = true;
                this.pendingOperation = "Runtime invalidation";
                if (this.pendingRelease == null)
                    this.requestActiveRelease (sampleRevision);
            }
        }


        private void requestActiveReturn (final String operation, final long sampleRevision)
        {
            if (this.active == null)
            {
                this.finishReturn ();
                return;
            }

            this.returnRequested = true;
            this.pendingOperation = Objects.requireNonNull (operation, "operation");
            if (this.pendingRelease == null && this.activeObservedBusy)
                this.requestActiveRelease (sampleRevision);
        }


        private void advance (final long sampleRevision)
        {
            if (this.active == null)
            {
                if (this.pending != null && sampleRevision > this.launchAfterSample)
                    this.launchPending (sampleRevision);
                return;
            }

            this.activePlayback = this.active.target ().playbackState ();
            if (isBusy (this.activePlayback))
                this.activeObservedBusy = true;

            if (!this.returnRequested)
                return;

            final PendingRelease release = this.pendingRelease;
            if (release == null)
            {
                // Let Bitwig publish the launch before submitting Return. Sending both commands
                // in one host turn can be coalesced into a permanently idle read-back, which
                // provides no safe acknowledgement for retiring this exact actuator.
                if (!this.activeObservedBusy)
                    return;
                this.requestActiveRelease (sampleRevision);
                return;
            }

            if (release.lease () != this.active)
                throw new IllegalStateException ("The pending fill release no longer owns the active target");
            if (sampleRevision <= release.issuedAfterSample ())
                return;

            // A target starts with a stale false sample until Bitwig publishes the launch. Never
            // mistake that pre-launch value for a completed Return.
            if (!this.activeObservedBusy || isBusy (this.activePlayback))
                return;

            this.retireActive ();
            this.launchAfterSample = sampleRevision;
            this.finishReturn ();
        }


        private void requestActiveRelease (final long sampleRevision)
        {
            final LaunchLease lease = this.active;
            if (lease == null)
            {
                this.finishReturn ();
                return;
            }

            try
            {
                lease.target ().release ();
                this.pendingRelease = new PendingRelease (lease, sampleRevision);
            }
            catch (final Throwable failure)
            {
                rethrowFatal (failure);
                DrumFillRuntimeEnvironment.this.warn (this.pendingOperation + " failed for " + lease.owner ().value () + ": " + sanitize (failure));
            }
        }


        private boolean launchPending (final long sampleRevision)
        {
            final PendingLaunch requested = this.pending;
            if (requested == null)
                return true;
            if (sampleRevision <= this.launchAfterSample)
                return true;
            if (!DrumFillRuntimeEnvironment.this.pressedControls.contains (requested.owner ()))
            {
                this.pending = null;
                return true;
            }
            if (DrumFillRuntimeEnvironment.this.clipCatalog.generation () != requested.catalogGeneration () || !requested.targetId ().equals (DrumFillRuntimeEnvironment.this.armedClipTargets.get (requested.owner ())))
            {
                this.pending = null;
                DrumFillRuntimeEnvironment.this.warn ("Pending fill press was discarded after its catalog binding changed");
                return false;
            }

            final DrumFillClipHost.LaunchTarget target;
            try
            {
                target = Objects.requireNonNull (DrumFillRuntimeEnvironment.this.clipHost.prepare (requested.owner (), requested.catalogGeneration (), requested.targetId ()), "prepared launch target");
            }
            catch (final Throwable failure)
            {
                rethrowFatal (failure);
                this.pending = null;
                DrumFillRuntimeEnvironment.this.warn ("Fill target preparation failed: " + sanitize (failure));
                return false;
            }
            if (!requested.targetId ().equals (target.targetId ()))
            {
                this.pending = null;
                DrumFillRuntimeEnvironment.this.warn ("Fill target preparation resolved a different target");
                return false;
            }

            final LaunchLease lease = new LaunchLease (requested.owner (), requested.catalogGeneration (), requested.targetId (), requested.launchPolicy (), target);
            this.pending = null;
            this.active = lease;
            this.returnRequested = false;
            this.activeObservedBusy = false;
            this.activePlayback = new DrumFillClipHost.PlaybackState (false, false, false);
            this.launchAfterSample = -1;
            this.pendingRelease = null;
            try
            {
                target.press (requested.launchPolicy ());
                return true;
            }
            catch (final Throwable failure)
            {
                rethrowFatal (failure);
                DrumFillRuntimeEnvironment.this.warn ("Fill target press failed: " + sanitize (failure));
                this.requestActiveReturn ("Partial fill acquisition rollback", sampleRevision);
                return false;
            }
        }


        private void retireActive ()
        {
            final LaunchLease lease = this.active;
            if (lease == null)
                return;
            try
            {
                lease.target ().retire ();
            }
            catch (final Throwable failure)
            {
                rethrowFatal (failure);
                DrumFillRuntimeEnvironment.this.warn ("Fill target retirement failed for " + lease.owner ().value () + ": " + sanitize (failure));
            }
            finally
            {
                this.active = null;
                this.activeObservedBusy = false;
                this.activePlayback = new DrumFillClipHost.PlaybackState (false, false, false);
            }
        }


        private void finishReturn ()
        {
            this.returnRequested = false;
            this.pendingRelease = null;
            this.pendingOperation = "Fill-session return";
        }


        private FillSessionView view ()
        {
            final Optional<ControlId> activeOwner = this.active != null && this.activePlayback.playing () ? Optional.of (this.active.owner ()) : Optional.empty ();
            return new FillSessionView (FillSessionBase.BITWIG_OWNED_CLIP_OR_ARRANGEMENT, this.targets (), activeOwner);
        }


        private static boolean isBusy (final DrumFillClipHost.PlaybackState state)
        {
            return state.playing () || state.playbackQueued () || state.stopQueued ();
        }
    }


    private enum FillSessionBase
    {
        BITWIG_OWNED_CLIP_OR_ARRANGEMENT
    }


    private record FillSessionView (FillSessionBase base, Map<ControlId, ClipTargetId> targets, Optional<ControlId> activeOwner)
    {
        private FillSessionView
        {
            base = Objects.requireNonNull (base, "base");
            targets = Map.copyOf (Objects.requireNonNull (targets, "targets"));
            activeOwner = Objects.requireNonNull (activeOwner, "activeOwner");
        }
    }


    private record PendingRelease (LaunchLease lease, long issuedAfterSample)
    {
        private PendingRelease
        {
            lease = Objects.requireNonNull (lease, "lease");
            if (issuedAfterSample < 0)
                throw new IllegalArgumentException ("issuedAfterSample must not be negative");
        }
    }


    private record PendingLaunch (ControlId owner, long catalogGeneration, ClipTargetId targetId, ClipLaunchPolicy launchPolicy)
    {
        private PendingLaunch
        {
            owner = requireFillOwner (owner);
            if (catalogGeneration < 0)
                throw new IllegalArgumentException ("catalogGeneration must not be negative");
            targetId = Objects.requireNonNull (targetId, "targetId");
            launchPolicy = Objects.requireNonNull (launchPolicy, "launchPolicy");
        }
    }


    private record PreparedResult (Map<ControlId, RgbColor> fillLightColors, DesiredInputRoutes desiredInputRoutes, DesiredBridgeSubscriptions desiredBridgeSubscriptions, long catalogGeneration, Map<ControlId, ClipTargetId> desiredClipBindings, List<PreparedAction> actions) implements PreparedCoreResult
    {
        private PreparedResult
        {
            fillLightColors = Map.copyOf (fillLightColors);
            desiredInputRoutes = Objects.requireNonNull (desiredInputRoutes, "desiredInputRoutes");
            desiredBridgeSubscriptions = Objects.requireNonNull (desiredBridgeSubscriptions, "desiredBridgeSubscriptions");
            desiredClipBindings = Map.copyOf (desiredClipBindings);
            actions = List.copyOf (actions);
        }
    }


    private interface PreparedAction
    {
        // Closed stable-shell action set.
    }


    private record PreparedPress (ControlId owner, long catalogGeneration, ClipTargetId targetId, ClipLaunchPolicy launchPolicy) implements PreparedAction
    {
    }


    private record PreparedRelease (ControlId owner) implements PreparedAction
    {
    }


    private record PreparedBridgeAction (BoundedControllerBridge.PreparedAction action) implements PreparedAction
    {
        private PreparedBridgeAction
        {
            action = Objects.requireNonNull (action, "action");
        }
    }


    private record LaunchLease (ControlId owner, long catalogGeneration, ClipTargetId targetId, ClipLaunchPolicy launchPolicy, DrumFillClipHost.LaunchTarget target)
    {
    }


}
