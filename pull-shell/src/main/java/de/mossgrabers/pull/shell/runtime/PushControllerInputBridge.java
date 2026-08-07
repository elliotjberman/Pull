// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwContinuousControl;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerActionIntent;
import de.mossgrabers.pull.core.api.ControllerActionBinding;
import de.mossgrabers.pull.core.api.DesiredInputRoutes;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.shell.input.InputKind;
import de.mossgrabers.pull.shell.input.InputPhase;
import de.mossgrabers.pull.shell.input.PhysicalControlRegistry;
import de.mossgrabers.pull.shell.input.PhysicalInputAddress;
import de.mossgrabers.pull.shell.input.PhysicalInputEvent;
import de.mossgrabers.pull.shell.input.PhysicalInputRouter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;


/**
 * Installs one permanent normalized input seam around the already-bound Push commands.
 *
 * <p>Absent routes are a single bounded lookup followed by the original command. Observed and
 * exclusive motion is coalesced until the controller tick; edge gestures retain their begin-time
 * ownership through release.</p>
 */
final class PushControllerInputBridge implements PushDebugNavigationHost.GestureAdmission
{
    private static final int REGISTRY_CAPACITY = 256;
    private static final int MIDI_POLY_PRESSURE = 0xA0;
    private static final int MIDI_CC = 0xB0;
    private static final int MIDI_CHANNEL_PRESSURE = 0xD0;
    private static final int SUSTAIN_CC = 64;
    private static final Set<PhysicalInputAddress<ControlId>> CORE_OWNED_INPUTS = coreOwnedInputs ();

    private static final List<ContinuousID> CONTINUOUS_CONTROLS = List.of (
        ContinuousID.KNOB1,
        ContinuousID.KNOB2,
        ContinuousID.KNOB3,
        ContinuousID.KNOB4,
        ContinuousID.KNOB5,
        ContinuousID.KNOB6,
        ContinuousID.KNOB7,
        ContinuousID.KNOB8,
        ContinuousID.MASTER_KNOB,
        ContinuousID.TEMPO,
        ContinuousID.PLAY_POSITION,
        ContinuousID.TOUCHSTRIP);

    private final PushControlSurface surface;
    private final IValueChanger valueChanger;
    private final ParameterMutationDispatcher parameterMutations;
    private final Consumer<PhysicalInputEvent<ControlId>> eventSink;
    private final Supplier<DesiredInputRoutes> routes;
    private final PhysicalControlRegistry<ControlId> registry;
    private final PhysicalInputRouter<ControlId> router;
    private final StableControllerActionResolver stableActions;


    /**
     * Construct and install the bridge after normal Push command registration has completed.
     *
     * @param surface Stable surface
     * @param valueChanger Relative-value decoder
     * @param parameterMutations Controller parameter-mutation seam
     * @param routes Complete committed route supplier
     * @param stableActionBarrier Semantic stable-action barrier
     * @param eventSink Normalized event sink
     * @param activeGeneration Current active reloadable-core generation
     */
    PushControllerInputBridge (final PushControlSurface surface, final IValueChanger valueChanger, final ParameterMutationDispatcher parameterMutations, final Supplier<DesiredInputRoutes> routes, final PhysicalInputRouter.StableActionBarrier<ControlId> stableActionBarrier, final Consumer<PhysicalInputEvent<ControlId>> eventSink, final LongSupplier activeGeneration)
    {
        this.surface = Objects.requireNonNull (surface, "surface");
        this.valueChanger = Objects.requireNonNull (valueChanger, "valueChanger");
        this.parameterMutations = Objects.requireNonNull (parameterMutations, "parameterMutations");
        this.eventSink = Objects.requireNonNull (eventSink, "eventSink");
        this.routes = Objects.requireNonNull (routes, "routes");
        this.stableActions = new StableControllerActionResolver (surface);
        this.registry = this.createRegistry ();
        this.router = new PhysicalInputRouter<> (this.registry, this::resolveRoute, this.eventSink, Objects.requireNonNull (stableActionBarrier, "stableActionBarrier"), System::nanoTime, Objects.requireNonNull (activeGeneration, "activeGeneration"));
        this.installWrappers ();
    }


    /**
     * Drain bounded coalesced motion.
     */
    void flush ()
    {
        this.router.flush ();
    }


    /** Release stable input commands whose semantic-action barrier has completed. */
    void releaseDeferredStableDispatches ()
    {
        this.router.releaseDeferredStableDispatches ();
    }


    /** Test whether no physical input lifecycle crosses a core generation boundary. */
    boolean inputLifecycleIdle ()
    {
        return this.isIdle ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean isIdle ()
    {
        return this.router.isIdle ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean trySubmit (final Runnable gesture)
    {
        if (!this.router.isIdle ())
            return false;
        Objects.requireNonNull (gesture, "gesture").run ();
        return true;
    }


    /**
     * Validate one requested API route against the fixed physical registry.
     *
     * @param route Requested route
     * @return True if registered
     */
    boolean supports (final de.mossgrabers.pull.core.api.InputRoute route)
    {
        Objects.requireNonNull (route, "route");
        final InputKind kind = toShellKind (route.kind ());
        if (!this.registry.contains (route.controlId (), kind))
            return false;
        if (route.mode () == InputRouteMode.EXCLUSIVE)
            return CORE_OWNED_INPUTS.contains (new PhysicalInputAddress<> (route.controlId (), kind));
        return true;
    }


    /** Validate one view-owned semantic action against the fixed physical registry. */
    boolean supports (final ControllerActionBinding action)
    {
        Objects.requireNonNull (action, "action");
        return this.registry.contains (action.controlId (), toShellKind (action.inputKind ())) && action.inputKind ().isEdge ();
    }


    /**
     * Normalize pressure and sustain messages which do not have ordinary command wrappers.
     *
     * @param status MIDI status
     * @param data1 First MIDI byte
     * @param data2 Second MIDI byte
     * @param stableDispatch Existing stable surface MIDI handling
     * @return True when this method invoked or intentionally suppressed stable handling
     */
    boolean routeMidi (final int status, final int data1, final int data2, final Runnable stableDispatch)
    {
        final int command = status & 0xF0;
        if (command == MIDI_POLY_PRESSURE)
        {
            final int padIndex = data1 - this.surface.getPadGrid ().getStartNote ();
            if (padIndex < 0 || padIndex >= 64)
                return false;
            this.router.route (PushControlIds.pad (padIndex + 1), InputKind.POLY_PRESSURE, InputPhase.CHANGE, data2, stableDispatch);
            return true;
        }
        if (command == MIDI_CHANNEL_PRESSURE)
        {
            this.router.route (PushControlIds.CHANNEL_PRESSURE, InputKind.CHANNEL_PRESSURE, InputPhase.CHANGE, data1, stableDispatch);
            return true;
        }
        if (command == MIDI_CC && data1 == SUSTAIN_CC)
        {
            final boolean pressed = data2 > 0;
            this.router.route (PushControlIds.SUSTAIN_PEDAL, InputKind.PEDAL, pressed ? InputPhase.BEGIN : InputPhase.END, pressed ? data2 : 0, stableDispatch);
            return true;
        }
        return false;
    }


    private PhysicalControlRegistry<ControlId> createRegistry ()
    {
        final PhysicalControlRegistry.Builder<ControlId> builder = PhysicalControlRegistry.builder (REGISTRY_CAPACITY);
        for (final Map.Entry<ButtonID, IHwButton> entry: this.surface.getButtons ().entrySet ())
        {
            if (entry.getValue ().getCommand () == null)
                continue;
            final int padIndex = padIndex (entry.getKey ());
            final ControlId control = padIndex < 0 ? PushControlIds.button (entry.getKey ().name ()) : PushControlIds.pad (padIndex + 1);
            builder.register (control, padIndex < 0 && entry.getKey () != ButtonID.FOOTSWITCH2 ? InputKind.BUTTON : padIndex < 0 ? InputKind.PEDAL : InputKind.PAD);
        }
        for (int index = 0; index < 64; index++)
            builder.register (PushControlIds.pad (index + 1), InputKind.POLY_PRESSURE);
        builder.register (PushControlIds.CHANNEL_PRESSURE, InputKind.CHANNEL_PRESSURE);
        builder.register (PushControlIds.SUSTAIN_PEDAL, InputKind.PEDAL);

        for (final ContinuousID id: CONTINUOUS_CONTROLS)
        {
            final IHwContinuousControl control = this.surface.getContinuous (id);
            if (control == null)
                continue;
            final ControlId controlID = PushControlIds.continuous (id.name ());
            if (control.getCommand () != null)
                builder.register (controlID, InputKind.RELATIVE);
            if (control.getPitchbendCommand () != null)
                builder.register (controlID, InputKind.ABSOLUTE);
            if (control.getTouchCommand () != null)
                builder.register (controlID, InputKind.TOUCH);
        }
        return builder.build ();
    }


    private void installWrappers ()
    {
        for (final Map.Entry<ButtonID, IHwButton> entry: this.surface.getButtons ().entrySet ())
        {
            final IHwButton button = entry.getValue ();
            if (button.getCommand () == null)
                continue;
            final int padIndex = padIndex (entry.getKey ());
            final ControlId control = padIndex < 0 ? PushControlIds.button (entry.getKey ().name ()) : PushControlIds.pad (padIndex + 1);
            final InputKind kind = padIndex < 0 && entry.getKey () != ButtonID.FOOTSWITCH2 ? InputKind.BUTTON : padIndex < 0 ? InputKind.PEDAL : InputKind.PAD;
            button.installEventArbitrator ( (event, velocity, stableDispatch) -> {
                if (entry.getKey () == ButtonID.SHIFT && event == ButtonEvent.UP)
                    this.router.flush ();
                final ControllerActionIntent stableAction = this.stableActions.resolve (button.getCommand (), event);
                this.router.route (control, kind, toShellPhase (event), velocity, stableAction, stableDispatch);
            });
        }

        for (final ContinuousID id: CONTINUOUS_CONTROLS)
        {
            final IHwContinuousControl control = this.surface.getContinuous (id);
            if (control == null)
                continue;
            final ControlId controlID = PushControlIds.continuous (id.name ());
            final boolean isRelative = control.getCommand () != null;
            final boolean isAbsolute = control.getPitchbendCommand () != null;
            if (isRelative || isAbsolute)
            {
                final InputKind kind = isRelative ? InputKind.RELATIVE : InputKind.ABSOLUTE;
                control.installValueArbitrator ( (value, stableMutation) -> this.router.route (
                    controlID,
                    kind,
                    InputPhase.CHANGE,
                    isRelative ? this.valueChanger.decode (value) : value,
                    () -> this.parameterMutations.mutate (id, control, stableMutation)));
            }

            if (control.getTouchCommand () != null)
            {
                control.installTouchEventArbitrator ( (event, velocity, stableDispatch) -> {
                    if (event == ButtonEvent.UP)
                    {
                        if (isRelative)
                            this.router.flush (controlID, InputKind.RELATIVE);
                        if (isAbsolute)
                            this.router.flush (controlID, InputKind.ABSOLUTE);
                    }
                    this.router.route (controlID, InputKind.TOUCH, toShellPhase (event), velocity, stableDispatch);
                });
            }
        }
    }


    private de.mossgrabers.pull.shell.input.InputRoute resolveRoute (final ControlId control, final InputKind kind)
    {
        final InputRouteMode route = this.routes.get ().modeOrNull (control, toApiKind (kind));
        return route == null ? de.mossgrabers.pull.shell.input.InputRoute.NONE : toShellRoute (route);
    }


    private static de.mossgrabers.pull.shell.input.InputRoute toShellRoute (final InputRouteMode route)
    {
        return switch (route)
        {
            case OBSERVE -> de.mossgrabers.pull.shell.input.InputRoute.OBSERVE;
            case EXCLUSIVE -> de.mossgrabers.pull.shell.input.InputRoute.EXCLUSIVE;
        };
    }


    private static Set<PhysicalInputAddress<ControlId>> coreOwnedInputs ()
    {
        final java.util.LinkedHashSet<PhysicalInputAddress<ControlId>> inputs = new java.util.LinkedHashSet<> ();
        inputs.add (new PhysicalInputAddress<> (PushControlIds.button (ButtonID.RECORD.name ()), InputKind.BUTTON));
        for (int index = 1; index <= 8; index++)
            inputs.add (new PhysicalInputAddress<> (PushControlIds.continuous ("KNOB" + index), InputKind.RELATIVE));
        return Set.copyOf (inputs);
    }


    private static de.mossgrabers.pull.core.api.event.InputKind toApiKind (final InputKind kind)
    {
        return de.mossgrabers.pull.core.api.event.InputKind.valueOf (kind.name ());
    }


    private static InputKind toShellKind (final de.mossgrabers.pull.core.api.event.InputKind kind)
    {
        return InputKind.valueOf (kind.name ());
    }


    private static InputPhase toShellPhase (final ButtonEvent event)
    {
        return switch (event)
        {
            case DOWN -> InputPhase.BEGIN;
            case LONG -> InputPhase.LONG;
            case UP -> InputPhase.END;
        };
    }


    private static int padIndex (final ButtonID buttonID)
    {
        for (int index = 0; index < 64; index++)
        {
            if (ButtonID.get (ButtonID.PAD1, index) == buttonID)
                return index;
        }
        return -1;
    }


    @FunctionalInterface
    interface ParameterMutationDispatcher
    {
        void mutate (ContinuousID controlID, IHwContinuousControl control, Runnable stableMutation);
    }
}
