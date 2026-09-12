// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.DevicePageState;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.ui.page.DevicePageRenderer;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.InputTarget;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Eight retained Device remotes; the distinct legacy menu/navigation controls stay frozen. */
public final class DeviceRemoteControlsView implements ControllerView
{
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final Map<ControlId, ParameterSlot> BINDINGS = bindings ();
    private static final ViewProfile PROFILE = ViewProfile.fixed ("retained-device-remotes", Set.of (
        new SurfaceClaim (SurfaceArea.ENCODER_TURNS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.ENCODER_TOUCHES, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.OUTPUT)), Set.of ());
    private static final ControllerDisplayScene BLANK = new ControllerDisplayScene (960, 160,
        List.of (new DisplayCommand.Rectangle (0, 0, 960, 160, new RgbColor (0, 0, 0))));

    @Override public String id () { return "device-remote-controls"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Map<ControlId, ParameterSlot> parameterBindings () { return BINDINGS; }
    @Override public Map<ControlId, ParameterSlot> parameterBindings (final ControllerSnapshot snapshot) { return ParameterAlignment.bindings (snapshot, BINDINGS); }
    @Override public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return Set.of (BridgeSubscription.PARAMETERS, BridgeSubscription.CONTROLLER_PAGE_DISPLAY, BridgeSubscription.AUTOMATION, BridgeSubscription.ENCODER_CONFIGURATION);
    }
    @Override public Set<ControlId> parameterTouchControls (final ControllerSnapshot snapshot) { return SurfaceArea.ENCODER_TOUCHES.controls (); }

    @Override
    public InputTarget inputTarget (final ControlId control, final InputKind kind, final ControllerSnapshot snapshot)
    {
        if (ParameterAlignment.target (snapshot, BINDINGS.get (control)) != null)
            return ControllerView.super.inputTarget (control, kind, snapshot);
        // An empty/pending slot still owns Delete consumption and automation release. It never
        // acquires a parameter, and a later ready target cancels this physical tail.
        return kind == InputKind.TOUCH && observedPage (snapshot) != null ?
            new InputTarget.Context (control, "device-parameter-touch", snapshot.bridge ().automation ().projectIdentity (), 0) : null;
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input) || input.kind () != InputKind.RELATIVE)
            return List.of ();
        final ParameterTargetSnapshot target = ParameterAlignment.target (snapshot, BINDINGS.get (input.controlId ()));
        return target == null ? List.of () : TrackEncoderResponse.adjust (TrackEncoderResponse.Role.RANGED, target, input.value (),
            snapshot.pressedControls ().contains (SHIFT), snapshot.bridge ().encoderConfiguration ());
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final DevicePageState state = observedPage (snapshot);
        if (state == null)
            return new ViewOutput (Map.of (), Map.of (), BLANK);
        final List<DevicePageState.Parameter> parameters = new ArrayList<> (ParameterSlot.BANK_SIZE);
        final double range = snapshot.bridge ().encoderConfiguration ().available () ? snapshot.bridge ().encoderConfiguration ().valueUpperBound () - 1.0 : 0;
        for (int index = 0; index < ParameterSlot.BANK_SIZE; index++)
        {
            final ParameterTargetSnapshot target = ParameterAlignment.target (snapshot, ParameterSlot.selectedDeviceRemote (index));
            parameters.add (target == null || range <= 0 ? DevicePageState.Parameter.empty () : new DevicePageState.Parameter (true, target.name (),
                normalized (target.value (), range), target.modulatedValue () < 0 ? -1 : normalized (target.modulatedValue (), range),
                target.displayedValue (), target.enabled ().orElse (Boolean.TRUE).booleanValue (), snapshot.bridge ().parameters ().touchLeases ().contains (target.target ())));
        }
        // Raw state still describes the frozen menu rows. Parameter values and touch feedback
        // come exclusively from the same retained targets admitted for edits and cleanup.
        final DevicePageState projected = new DevicePageState (state.kind (), state.device (), state.channels (), state.selectedChannel (),
            parameters, state.sends (), state.selection (), state.parameterOwnerId ());
        return new ViewOutput (Map.of (), Map.of (), DevicePageRenderer.render (projected));
    }

    static DevicePageState observedPage (final ControllerSnapshot snapshot)
    {
        final var display = snapshot.bridge ().pageDisplay ();
        return "DEVICE_PARAMS".equals (display.modeId ()) && display.state () instanceof final DevicePageState state && state.kind () == DevicePageState.Kind.PARAMETERS ? state : null;
    }

    private static double normalized (final double value, final double range) { return Math.max (0, Math.min (1, value / range)); }

    private static Map<ControlId, ParameterSlot> bindings ()
    {
        final Map<ControlId, ParameterSlot> result = new LinkedHashMap<> ();
        for (int index = 0; index < ParameterSlot.BANK_SIZE; index++)
            result.put (PushControlIds.continuous ("KNOB" + (index + 1)), ParameterSlot.selectedDeviceRemote (index));
        return Map.copyOf (result);
    }
}
