// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.ui.page.DevicePageRenderer;
import de.mossgrabers.pull.core.view.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Named channel controls for selected layers, layer/drum-pad rows and track crossfade assignments. */
public final class ChannelParameterControlsView implements ControllerView
{
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId SELECT = PushControlIds.button ("SELECT");
    private static final ViewProfile PROFILE = ViewProfile.fixed ("retained-channel-parameters", Set.of (
        new SurfaceClaim (SurfaceArea.ENCODER_TURNS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.ENCODER_TOUCHES, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.OUTPUT)), Set.of ());
    private final String mode;
    private final ParameterBankId bank;
    private final Map<ControlId, ParameterSlot> bindings;
    private final List<AuthoritativeBooleanToggle<ParameterTargetRef>> enabled = java.util.stream.IntStream.range (0, 8).mapToObj (ignored -> new AuthoritativeBooleanToggle<ParameterTargetRef> ()).toList ();

    public ChannelParameterControlsView (final String mode, final ParameterBankId bank)
    {
        this.mode = mode;
        this.bank = bank;
        final Map<ControlId, ParameterSlot> bindings = new LinkedHashMap<> ();
        for (int index = 0; index < 8; index++)
        {
            if (bank == ParameterBankId.SELECTED_LAYER && (index == 2 || index == 3)) continue;
            bindings.put (knob (index), bank == ParameterBankId.SELECTED_LAYER && index >= 4 ?
                new ParameterSlot (ParameterBankId.SELECTED_LAYER_SENDS, index - 4) : new ParameterSlot (bank, index));
        }
        this.bindings = Map.copyOf (bindings);
    }

    @Override public String id () { return this.mode + "-parameters"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.PARAMETERS, BridgeSubscription.CONTROLLER_PAGE_DISPLAY, BridgeSubscription.AUTOMATION, BridgeSubscription.ENCODER_CONFIGURATION); }
    @Override public Set<ParameterBankId> parameterBanks () { return this.bank == ParameterBankId.SELECTED_LAYER ? Set.of (this.bank, ParameterBankId.SELECTED_LAYER_SENDS) : Set.of (this.bank); }
    @Override public Map<ControlId, ParameterSlot> parameterBindings () { return this.bindings; }
    @Override public Map<ControlId, ParameterSlot> parameterBindings (final ControllerSnapshot snapshot) { return state (snapshot) == null ? Map.of () : ParameterAlignment.bindings (snapshot, this.slots (snapshot)); }
    @Override public Set<ControlId> parameterTouchControls (final ControllerSnapshot snapshot) { return SurfaceArea.ENCODER_TOUCHES.controls (); }
    @Override public boolean consumesRelativeSamples () { return this.bank == ParameterBankId.TRACK_CROSSFADE; }
    @Override public boolean touchAfterReset () { return this.bank != ParameterBankId.SELECTED_LAYER; }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (this.enabled.stream ().anyMatch (AuthoritativeBooleanToggle::pending)); }
    @Override public void deactivate () { this.enabled.forEach (AuthoritativeBooleanToggle::clear); }

    @Override
    public InputTarget inputTarget (final ControlId control, final InputKind kind, final ControllerSnapshot snapshot)
    {
        final var state = this.state (snapshot);
        if (state == null || !state.selection ().bankAligned () || this.bank.isLayer () && state.parameterOwnerId ().isBlank ()) return null;
        final ParameterSlot slot = this.slots (snapshot).get (control);
        final var target = ParameterAlignment.target (snapshot, slot);
        if (target != null) return new InputTarget.Parameter (target.target ());
        // Selected-channel spacer touches still consume Delete and stop automation on release.
        return this.bank == ParameterBankId.SELECTED_LAYER && state.selectedChannel ().exists () && slot == null ?
            new InputTarget.Context (control, "layer-spacer", state.selectedChannel ().id (), 0) : null;
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        final List<CoreEffect> effects = new ArrayList<> ();
        final var slots = this.slots (snapshot);
        for (int index = 0; index < 8; index++)
        {
            final var target = ParameterAlignment.target (snapshot, slots.get (knob (index)));
            if (target == null || target.enabled ().isEmpty ()) this.enabled.get (index).clear ();
            else effects.addAll (this.enabled.get (index).update (target.target (), target.enabled ().orElseThrow (), snapshot.monotonicTimeNanos (), false, SetParameterEnabledEffect::new));
        }
        if (!(event instanceof final ControllerInputEvent input)) return List.copyOf (effects);
        final ParameterSlot slot = this.slots (snapshot).get (input.controlId ());
        final var target = this.state (snapshot) == null ? null : ParameterAlignment.target (snapshot, slot);
        if (target == null) return List.copyOf (effects);
        if (input.kind () == InputKind.RELATIVE)
        {
            if (this.bank == ParameterBankId.TRACK_CROSSFADE)
            {
                if (!snapshot.bridge ().encoderConfiguration ().available ()) return List.copyOf (effects);
                final double range = snapshot.bridge ().encoderConfiguration ().valueUpperBound () - 1.0;
                int position = (int) Math.round (2 * target.value () / range);
                for (final long sample: input.relativeSamples ()) position = Math.max (0, Math.min (2, position + Long.signum (sample)));
                effects.add (new SetParameterNormalizedValueEffect (target.target (), position / 2.0));
                return List.copyOf (effects);
            }
            final var role = slot.bank () == ParameterBankId.LAYER_VOLUME || slot.bank () == ParameterBankId.SELECTED_LAYER && slot.index () == 0 ? TrackEncoderResponse.Role.VOLUME :
                slot.bank () == ParameterBankId.LAYER_PAN || slot.bank () == ParameterBankId.SELECTED_LAYER && slot.index () == 1 ? TrackEncoderResponse.Role.PAN : TrackEncoderResponse.Role.RANGED;
            effects.addAll (TrackEncoderResponse.adjust (role, target, input.value (), snapshot.pressedControls ().contains (SHIFT), snapshot.bridge ().encoderConfiguration ()));
        }
        else if (input.kind () == InputKind.TOUCH && input.phase () == InputPhase.BEGIN && target.enabled ().isPresent () && snapshot.pressedControls ().containsAll (Set.of (SHIFT, SELECT)) &&
            !(this.bank == ParameterBankId.SELECTED_LAYER && snapshot.pressedControls ().contains (PushControlIds.button ("DELETE"))))
        {
            effects.add (new AcquireParameterTouchEffect (input.controlId (), target.target ()));
            effects.add (new ConsumeControllerButtonEffect (SELECT));
            final int index = java.util.stream.IntStream.range (0, 8).filter (candidate -> knob (candidate).equals (input.controlId ())).findFirst ().orElseThrow ();
            effects.addAll (this.enabled.get (index).update (target.target (), target.enabled ().orElseThrow (), snapshot.monotonicTimeNanos (), true, SetParameterEnabledEffect::new));
        }
        return List.copyOf (effects);
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final var state = this.state (snapshot);
        if (state == null) return new ViewOutput (Map.of (), Map.of (), DevicePageRenderer.render (DevicePageState.empty ()));
        final List<DevicePageState.Parameter> parameters = new ArrayList<> (8);
        final double range = snapshot.bridge ().encoderConfiguration ().valueUpperBound () - 1.0;
        final var slots = this.slots (snapshot);
        for (int index = 0; index < 8; index++)
        {
            final var target = ParameterAlignment.target (snapshot, slots.get (knob (index)));
            parameters.add (target == null || range <= 0 ? DevicePageState.Parameter.empty () : new DevicePageState.Parameter (true, target.name (), normalize (target.value (), range),
                target.modulatedValue () < 0 ? -1 : normalize (target.modulatedValue (), range), target.displayedValue (), target.enabled ().orElse (true), snapshot.bridge ().parameters ().touchLeases ().contains (target.target ())));
        }
        return new ViewOutput (Map.of (), Map.of (), DevicePageRenderer.render (new DevicePageState (state.kind (), state.device (), state.channels (), state.selectedChannel (), parameters, state.sends (), selection (state.selection (), snapshot.touchedControls ().contains (knob (7))), state.parameterOwnerId ())));
    }

    private Map<ControlId, ParameterSlot> slots (final ControllerSnapshot snapshot)
    {
        final var state = this.state (snapshot);
        if (state == null) return Map.of ();
        if (!this.bank.isLayer () || this.bank == ParameterBankId.SELECTED_LAYER || state.selection ().bankOffset () == 0) return this.bindings;
        if (state.selection ().bankOffset () != 8) return Map.of ();
        final Map<ControlId, ParameterSlot> slots = new LinkedHashMap<> ();
        this.bindings.forEach ((control, slot) -> slots.put (control, new ParameterSlot (slot.bank (), slot.index () + 8)));
        return Map.copyOf (slots);
    }

    private static DevicePageState.Selection selection (final DevicePageState.Selection observed, final boolean touched)
    {
        return new DevicePageState.Selection (observed.showDevices (), observed.drumPadBank (), observed.bankHasItems (), observed.bankAligned (), observed.bankOffset (), observed.sendIndex (), observed.mixSendOffset (), observed.shift (), touched, observed.trackPinned (), observed.midiEditChannel (), observed.actionTargetId ());
    }

    private DevicePageState state (final ControllerSnapshot snapshot)
    {
        final var page = snapshot.bridge ().pageDisplay ();
        return this.mode.equals (page.modeId ()) && page.state () instanceof final DevicePageState state ? state : null;
    }
    private static double normalize (final double value, final double range) { return Math.max (0, Math.min (1, value / range)); }
    private static ControlId knob (final int index) { return PushControlIds.continuous ("KNOB" + (index + 1)); }
}
