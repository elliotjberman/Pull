// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.ui.page.EditingPageRenderer;
import de.mossgrabers.pull.core.view.*;
import java.util.*;

/** Project-scoped Groove parameters; its separate row-button navigation remains frozen. */
public final class GrooveParameterControlsView implements ControllerView
{
    private static final int[] COLUMNS = {2, 3, 5, 6, 7};
    private static final Map<ControlId, ParameterSlot> BINDINGS = bindings ();
    private static final ViewProfile PROFILE = ViewProfile.fixed ("groove-parameters", Set.of (
        new SurfaceClaim (SurfaceArea.ENCODER_TURNS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.ENCODER_TOUCHES, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.OUTPUT)), Set.of ());

    @Override public String id () { return "groove-parameters"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Map<ControlId, ParameterSlot> parameterBindings () { return BINDINGS; }
    @Override public Map<ControlId, ParameterSlot> parameterBindings (final ControllerSnapshot snapshot) { return observed (snapshot) == null ? Map.of () : ParameterAlignment.bindings (snapshot, BINDINGS); }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.PARAMETERS, BridgeSubscription.AUTOMATION, BridgeSubscription.CONTROLLER_PAGE_DISPLAY, BridgeSubscription.ENCODER_CONFIGURATION); }
    @Override public Set<ControlId> parameterTouchControls (final ControllerSnapshot snapshot) { return SurfaceArea.ENCODER_TOUCHES.controls (); }
    @Override public boolean stopAutomationOnTouchRelease () { return false; }

    @Override
    public InputTarget inputTarget (final ControlId control, final InputKind kind, final ControllerSnapshot snapshot)
    {
        if (observed (snapshot) == null) return null;
        final var slot = BINDINGS.get (control);
        if (slot == null) return new InputTarget.Local (control);
        final var target = ParameterAlignment.target (snapshot, slot);
        return target == null ? null : new InputTarget.Parameter (target.target ());
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (observed (snapshot) == null || !(event instanceof ControllerInputEvent input) || input.kind () != InputKind.RELATIVE) return List.of ();
        final var target = ParameterAlignment.target (snapshot, BINDINGS.get (input.controlId ()));
        return target == null ? List.of () : TrackEncoderResponse.adjust (TrackEncoderResponse.Role.RANGED, target, input.value (), snapshot.pressedControls ().contains (PushControlIds.button ("SHIFT")), snapshot.bridge ().encoderConfiguration ());
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final var state = observed (snapshot);
        final List<EditingPageState.Parameter> parameters = new ArrayList<> ();
        final double range = snapshot.bridge ().encoderConfiguration ().valueUpperBound () - 1.0;
        for (int index = 0; index < COLUMNS.length; index++)
        {
            final var target = state == null ? null : ParameterAlignment.target (snapshot, new ParameterSlot (ParameterBankId.GROOVE, index));
            parameters.add (target == null || range <= 0 ? new EditingPageState.Parameter (false, "", 0, "", false) :
                new EditingPageState.Parameter (true, target.name (), Math.max (0, Math.min (1, target.value () / range)), target.displayedValue (), snapshot.bridge ().parameters ().touchLeases ().contains (target.target ())));
        }
        return new ViewOutput (Map.of (), Map.of (), EditingPageRenderer.render (state == null ? EditingPageState.empty () : new EditingPageState.Groove (state.enabled (), parameters)));
    }

    private static EditingPageState.Groove observed (final ControllerSnapshot snapshot)
    {
        final var page = snapshot.bridge ().pageDisplay ();
        return "GROOVE".equals (page.modeId ()) && page.state () instanceof EditingPageState.Groove state ? state : null;
    }

    private static Map<ControlId, ParameterSlot> bindings ()
    {
        final Map<ControlId, ParameterSlot> bindings = new LinkedHashMap<> ();
        for (int index = 0; index < COLUMNS.length; index++) bindings.put (PushControlIds.continuous ("KNOB" + (COLUMNS[index] + 1)), new ParameterSlot (ParameterBankId.GROOVE, index));
        return Map.copyOf (bindings);
    }
}
