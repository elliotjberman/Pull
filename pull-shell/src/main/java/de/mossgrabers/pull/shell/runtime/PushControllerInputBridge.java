// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.hardware.BindType;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwContinuousControl;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerActionIntent;
import de.mossgrabers.pull.core.api.ControllerActionBinding;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.DesiredInputRoutes;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.shell.input.InputKind;
import de.mossgrabers.pull.shell.input.InputPhase;
import de.mossgrabers.pull.shell.input.PhysicalControlRegistry;
import de.mossgrabers.pull.shell.input.PhysicalInputAddress;
import de.mossgrabers.pull.shell.input.PhysicalInputEvent;
import de.mossgrabers.pull.shell.input.PhysicalInputRouter;

import java.util.LinkedHashSet;
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
    private static final int PAD_COUNT = 64;
    private static final int REGISTRY_CAPACITY = 256;
    private static final int MIDI_NOTE_OFF = 0x80;
    private static final int MIDI_NOTE_ON = 0x90;
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
    private final Supplier<DesiredControllerMappings> activeMappings;
    private final PhysicalControlRegistry<ControlId> registry;
    private final PhysicalInputRouter<ControlId> router;
    private final StableControllerActionResolver stableActions;
    private final HardwareMappingActivationHost mappingActivation;
    private final List<PhysicalPadAddress> physicalPads;
    private final Set<ControlId> heldPhysicalPads = new LinkedHashSet<> ();
    private boolean debugInputActive;


    /**
     * Construct and install the bridge after normal Push command registration has completed.
     *
     * @param surface Stable surface
     * @param valueChanger Relative-value decoder
     * @param parameterMutations Controller parameter-mutation seam
     * @param routes Complete committed route supplier
     * @param activeMappings Complete committed physical-to-semantic mapping lease
     * @param physicalPadButtons Original grid-pad actions used only for ordinary raw dispatch
     * @param semanticMappingButtons Permanent semantic Bitwig mapping endpoints
     * @param stableActionBarrier Semantic stable-action barrier
     * @param eventSink Normalized event sink
     * @param activeGeneration Current active reloadable-core generation
     */
    PushControllerInputBridge (final PushControlSurface surface, final IValueChanger valueChanger, final ParameterMutationDispatcher parameterMutations, final Supplier<DesiredInputRoutes> routes, final Supplier<DesiredControllerMappings> activeMappings, final Map<ControlId, IHwButton> physicalPadButtons, final Map<ControllerMappingId, IHwButton> semanticMappingButtons, final PhysicalInputRouter.StableActionBarrier<ControlId> stableActionBarrier, final Consumer<PhysicalInputEvent<ControlId>> eventSink, final LongSupplier activeGeneration)
    {
        this.surface = Objects.requireNonNull (surface, "surface");
        this.valueChanger = Objects.requireNonNull (valueChanger, "valueChanger");
        this.parameterMutations = Objects.requireNonNull (parameterMutations, "parameterMutations");
        this.eventSink = Objects.requireNonNull (eventSink, "eventSink");
        this.routes = Objects.requireNonNull (routes, "routes");
        this.activeMappings = Objects.requireNonNull (activeMappings, "activeMappings");
        this.stableActions = new StableControllerActionResolver (surface);
        this.physicalPads = physicalPads (surface);
        this.registry = this.createRegistry ();
        this.router = new PhysicalInputRouter<> (this.registry, this::resolveRoute, this.eventSink, Objects.requireNonNull (stableActionBarrier, "stableActionBarrier"), System::nanoTime, Objects.requireNonNull (activeGeneration, "activeGeneration"));
        this.installWrappers ();
        this.mappingActivation = new HardwareMappingActivationHost (
            Objects.requireNonNull (physicalPadButtons, "physicalPadButtons"),
            Objects.requireNonNull (semanticMappingButtons, "semanticMappingButtons"),
            control -> !this.heldPhysicalPads.contains (control) && this.router.gesturesIdle (input -> isPadGesture (input, control)),
            this::bindMappingMatcher);
        this.mappingActivation.request (this.activeMappings.get ());
    }


    /**
     * Drain bounded coalesced motion.
     */
    void flush ()
    {
        this.router.flush ();
        this.mappingActivation.request (this.activeMappings.get ());
    }


    /** Release stable input commands whose semantic-action barrier has completed. */
    void releaseDeferredStableDispatches ()
    {
        this.router.releaseDeferredStableDispatches ();
        this.mappingActivation.request (this.activeMappings.get ());
    }


    /** Get semantic Bitwig mapping actions currently admitting new note-on presses. */
    DesiredControllerMappings activeControllerMappings ()
    {
        return this.mappingActivation.activeMappings ();
    }


    /** Test whether no pad or sustain lifecycle can outlive the selected Note route. */
    boolean musicalInputLifecycleIdle ()
    {
        return this.router.gesturesIdle (PushControllerInputBridge::isMusicalGesture);
    }


    /** {@inheritDoc} */
    @Override
    public boolean isIdle ()
    {
        return this.router.isIdle () && !this.debugInputActive;
    }


    /** {@inheritDoc} */
    @Override
    public boolean trySubmit (final Runnable gesture)
    {
        if (!this.isIdle ())
            return false;
        Objects.requireNonNull (gesture, "gesture").run ();
        return true;
    }


    /** {@inheritDoc} */
    @Override
    public boolean tryBeginDebugInput (final Runnable press)
    {
        if (!this.isIdle ())
            return false;
        this.debugInputActive = true;
        boolean submitted = false;
        try
        {
            Objects.requireNonNull (press, "press").run ();
            submitted = true;
            return true;
        }
        finally
        {
            if (!submitted)
                this.debugInputActive = false;
        }
    }


    /** {@inheritDoc} */
    @Override
    public void endDebugInput (final Runnable release)
    {
        if (!this.debugInputActive)
            throw new IllegalStateException ("No debug input is active");
        Objects.requireNonNull (release, "release").run ();
    }


    /** {@inheritDoc} */
    @Override
    public void completeDebugInput ()
    {
        this.debugInputActive = false;
    }


    /** {@inheritDoc} */
    @Override
    public InputRouteMode debugPadRoute (final ControlId control)
    {
        final PhysicalInputAddress<ControlId> address = new PhysicalInputAddress<> (Objects.requireNonNull (control, "control"), InputKind.PAD);
        return this.registry.contains (control, InputKind.PAD) && CORE_OWNED_INPUTS.contains (address) ? this.routes.get ().modeOrNull (control, toApiKind (InputKind.PAD)) : null;
    }


    /** {@inheritDoc} */
    @Override
    public boolean debugPadMappingActive (final ControlId control)
    {
        return this.activeControllerMappings ().mappingIdOrNull (Objects.requireNonNull (control, "control")) != null;
    }


    /** {@inheritDoc} */
    @Override
    public boolean debugInputRouteIdle ()
    {
        return this.router.isIdle ();
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
     * Normalize mapping-pad edges, pressure, and sustain messages which do not have ordinary raw
     * callback dispatch.
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
        if ((command == MIDI_NOTE_ON || command == MIDI_NOTE_OFF) && this.routePhysicalPadMidi (status, data1, data2))
            return true;
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
                if (event == ButtonEvent.UP && padIndex >= 0)
                    this.mappingActivation.request (this.activeMappings.get ());
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
        for (final ButtonID button: List.of (ButtonID.PLAY, ButtonID.RECORD, ButtonID.NOTE, ButtonID.SESSION, ButtonID.LAYOUT, ButtonID.MUTE, ButtonID.SOLO))
            inputs.add (new PhysicalInputAddress<> (PushControlIds.button (button.name ()), InputKind.BUTTON));
        for (final ControlId control: CoreControls.DRUM_RATES)
        {
            inputs.add (new PhysicalInputAddress<> (control, InputKind.PAD));
            inputs.add (new PhysicalInputAddress<> (control, InputKind.POLY_PRESSURE));
        }
        for (final ControlId control: CoreControls.DRUM_CONTROL_PADS)
            inputs.add (new PhysicalInputAddress<> (control, InputKind.PAD));
        for (int index = 1; index <= 8; index++)
        {
            inputs.add (new PhysicalInputAddress<> (PushControlIds.button ("ROW1_" + index), InputKind.BUTTON));
            inputs.add (new PhysicalInputAddress<> (PushControlIds.button ("ROW2_" + index), InputKind.BUTTON));
        }
        for (int index = 1; index <= 8; index++)
            inputs.add (new PhysicalInputAddress<> (PushControlIds.continuous ("KNOB" + index), InputKind.RELATIVE));
        return Set.copyOf (inputs);
    }


    static boolean isCoreOwnedInput (final ControlId control, final InputKind kind)
    {
        return CORE_OWNED_INPUTS.contains (new PhysicalInputAddress<> (Objects.requireNonNull (control, "control"), Objects.requireNonNull (kind, "kind")));
    }


    private static boolean isMusicalGesture (final PhysicalInputAddress<ControlId> input)
    {
        return input.kind () == InputKind.PAD || input.kind () == InputKind.PEDAL && PushControlIds.SUSTAIN_PEDAL.equals (input.control ());
    }


    private boolean routePhysicalPadMidi (final int status, final int note, final int velocity)
    {
        final ControlId control = this.physicalPadControl (status, note);
        if (control == null)
            return false;

        final boolean press = (status & 0xF0) == MIDI_NOTE_ON && velocity > 0;
        this.routePhysicalPad (control, press, velocity);
        return true;
    }


    /** Inject one debugger pad edge through the same raw-pad lane arbitration as hardware MIDI. */
    void triggerDebugPad (final ControlId control, final InputPhase phase, final int velocity)
    {
        if (phase != InputPhase.BEGIN && phase != InputPhase.END)
            throw new IllegalArgumentException ("Debug pad input requires BEGIN or END");
        this.routePhysicalPad (Objects.requireNonNull (control, "control"), phase == InputPhase.BEGIN, velocity);
    }


    private void routePhysicalPad (final ControlId control, final boolean press, final int velocity)
    {
        if (press)
            this.heldPhysicalPads.add (control);
        else
            this.heldPhysicalPads.remove (control);

        final InputPhase phase = press ? InputPhase.BEGIN : InputPhase.END;
        final HardwareMappingActivationHost.RawDisposition disposition = this.mappingActivation.dispatchRaw (control, press ? ButtonEvent.DOWN : ButtonEvent.UP, normalizedVelocity (velocity));
        if (disposition == HardwareMappingActivationHost.RawDisposition.MAPPED)
        {
            this.router.route (control, InputKind.PAD, phase, velocity, () -> {
                // The permanent semantic HardwareButton matcher is the only Bitwig learned action.
            });
            if (!press)
                this.mappingActivation.request (this.activeMappings.get ());
        }
    }


    private ControlId physicalPadControl (final int status, final int note)
    {
        for (final PhysicalPadAddress pad: this.physicalPads)
            if (pad.note () == note && (pad.channel () < 0 || pad.channel () == (status & 0x0F)))
                return pad.control ();
        return null;
    }


    private static List<PhysicalPadAddress> physicalPads (final PushControlSurface surface)
    {
        final java.util.ArrayList<PhysicalPadAddress> pads = new java.util.ArrayList<> (PAD_COUNT);
        for (int index = 0; index < PAD_COUNT; index++)
        {
            final int gridNote = surface.getPadGrid ().getStartNote () + index;
            final int [] translated = surface.getPadGrid ().translateToController (gridNote);
            pads.add (new PhysicalPadAddress (PushControlIds.pad (index + 1), translated[0], translated[1]));
        }
        return List.copyOf (pads);
    }


    private void bindMappingMatcher (final IHwButton mappingButton, final ControlId physicalControl)
    {
        for (final PhysicalPadAddress pad: this.physicalPads)
        {
            if (!pad.control ().equals (physicalControl))
                continue;
            mappingButton.bind (this.surface.getMidiInput (), BindType.NOTE, pad.channel (), pad.note ());
            return;
        }
        throw new IllegalArgumentException ("Physical controller mapping input has no MIDI address");
    }


    private static double normalizedVelocity (final int velocity)
    {
        if (velocity <= 0)
            return 0;
        if (velocity >= 127)
            return 1;
        return Math.nextUp (velocity / 127.0);
    }


    private static boolean isPadGesture (final PhysicalInputAddress<ControlId> input, final ControlId control)
    {
        return input.kind () == InputKind.PAD && input.control ().equals (control);
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


    static de.mossgrabers.pull.core.api.event.InputPhase toCorePhase (final InputPhase phase)
    {
        return switch (Objects.requireNonNull (phase, "phase"))
        {
            case BEGIN -> de.mossgrabers.pull.core.api.event.InputPhase.BEGIN;
            case CHANGE -> de.mossgrabers.pull.core.api.event.InputPhase.UPDATE;
            case LONG -> de.mossgrabers.pull.core.api.event.InputPhase.LONG;
            case END -> de.mossgrabers.pull.core.api.event.InputPhase.END;
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


    private record PhysicalPadAddress (ControlId control, int channel, int note)
    {}
}
