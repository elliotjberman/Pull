// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Project remote controls on the top encoder and parameter-display row.
 */
public final class ProjectMacroControlsView implements ControllerView
{
    private final ParameterTouchControls touches;

    private static final double PARAMETER_STEP_SIZE = 10.0;
    private static final Map<ControlId, ParameterSlot> PARAMETER_BINDINGS = projectParameterBindings ();
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "default",
        Set.of (
            new SurfaceClaim (SurfaceArea.ENCODER_TURNS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.ENCODER_TOUCHES, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT)),
        Set.of (ControllerViewFacet.PROJECT_MACRO_CONTROLS));


    public ProjectMacroControlsView ()
    {
        this (new ParameterTouchSession ());
    }


    public ProjectMacroControlsView (final ParameterTouchSession touchSession)
    {
        this.touches = new ParameterTouchControls (touchSession);
    }


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "project-macro-controls";
    }


    /** {@inheritDoc} */
    @Override
    public String installedModeId ()
    {
        return "WORKSPACE";
    }


    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    /** {@inheritDoc} */
    @Override
    public Map<ControlId, ParameterSlot> parameterBindings ()
    {
        return PARAMETER_BINDINGS;
    }


    @Override
    public Map<ControlId, ParameterSlot> parameterBindings (final ControllerSnapshot snapshot)
    {
        return ParameterAlignment.bindings (snapshot, PARAMETER_BINDINGS);
    }


    /** {@inheritDoc} */
    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return Set.of (BridgeSubscription.PARAMETERS, BridgeSubscription.AUTOMATION, BridgeSubscription.SELECTED_TRACK);
    }


    @Override
    public void start (final ControllerSnapshot snapshot)
    {
        // Normal core replacement waits for physical gestures; a fresh view never invents BEGIN.
        this.touches.clear ();
    }


    @Override
    public void deactivate ()
    {
        this.touches.clear ();
    }


    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        this.touches.reconcile (snapshot);
        this.touches.retainTargets (ParameterAlignment.references (snapshot));
    }


    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input))
            return List.of ();
        final ParameterSlot slot = PARAMETER_BINDINGS.get (input.controlId ());
        if (slot == null)
            return List.of ();
        final ParameterTargetSnapshot target = ParameterAlignment.target (snapshot, slot);
        if (input.kind () == InputKind.TOUCH && input.phase () == de.mossgrabers.pull.core.api.event.InputPhase.BEGIN && ParameterAlignment.contradicts (snapshot, slot))
            return List.of ();
        if (input.kind () == InputKind.RELATIVE)
            return target == null ? List.of () : List.of (new AdjustParameterValueEffect (target.target (), input.value () * PARAMETER_STEP_SIZE));
        if (input.kind () != InputKind.TOUCH)
            return List.of ();

        return this.touches.handle (input, target, snapshot);
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        return new ViewOutput (
            Map.of (),
            Map.of (),
            ProjectMacroDisplayScene.render (ParameterAlignment.targets (snapshot), snapshot.touchedControls ()),
            ControllerPadGridOverlay.inactive (),
            ControllerDisplayOverlay.inactive (),
            DesiredNotePerformance.inactive (),
            DesiredNoteRepeat.unowned (),
            DesiredControllerMappings.empty (),
            this.touches.desired ());
    }


    private static Map<ControlId, ParameterSlot> projectParameterBindings ()
    {
        final Map<ControlId, ParameterSlot> bindings = new LinkedHashMap<> ();
        for (int index = 0; index < ParameterSlot.BANK_SIZE; index++)
            bindings.put (PushControlIds.continuous ("KNOB" + (index + 1)), ParameterSlot.projectRemote (index));
        return Map.copyOf (bindings);
    }
}
