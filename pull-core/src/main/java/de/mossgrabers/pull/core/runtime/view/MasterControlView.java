// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.ui.page.MasterPageRenderer;
import de.mossgrabers.pull.core.ui.page.PageVisuals;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerActionBinding;
import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerStateScope;
import de.mossgrabers.pull.core.api.MasterSnapshot;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.NavigateProjectEffect;
import de.mossgrabers.pull.core.api.effect.ProjectFileAction;
import de.mossgrabers.pull.core.api.effect.ProjectFileActionEffect;
import de.mossgrabers.pull.core.api.effect.ProjectNavigationDirection;
import de.mossgrabers.pull.core.api.effect.SetProjectEngineEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;


/** Core-owned policy, input, lights, and display for the Master page. */
public final class MasterControlView implements ControllerView
{

    private static final double PARAMETER_STEP_SIZE = 10.0;
    private static final ControlId ENGINE = row (2, 5);
    private static final ControlId PREVIOUS = row (2, 7);
    private static final ControlId NEXT = row (2, 8);
    private static final ControlId OPEN = row (1, 7);
    private static final ControlId SAVE = row (1, 8);
    private static final Map<ControlId, ParameterSlot> PARAMETER_BINDINGS = Map.of (
        PushControlIds.continuous ("KNOB1"), ParameterSlot.MASTER_MIX_VOLUME,
        PushControlIds.continuous ("KNOB2"), ParameterSlot.MASTER_MIX_PAN,
        PushControlIds.continuous ("KNOB3"), ParameterSlot.CUE_VOLUME,
        PushControlIds.continuous ("KNOB4"), ParameterSlot.CUE_MIX);
    private static final Set<BridgeSubscription> SUBSCRIPTIONS = Set.of (BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.MASTER, BridgeSubscription.PARAMETERS, BridgeSubscription.AUTOMATION, BridgeSubscription.SELECTED_TRACK);
    private static final Set<ControllerActionBinding> ACTION_BINDINGS = Set.of (
        new ControllerActionBinding (ENGINE, InputKind.BUTTON, ControllerActionId.SET_PROJECT_ENGINE, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)),
        new ControllerActionBinding (PREVIOUS, InputKind.BUTTON, ControllerActionId.NAVIGATE_PROJECT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)),
        new ControllerActionBinding (NEXT, InputKind.BUTTON, ControllerActionId.NAVIGATE_PROJECT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)),
        new ControllerActionBinding (OPEN, InputKind.BUTTON, ControllerActionId.PROJECT_FILE_ACTION, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)),
        new ControllerActionBinding (SAVE, InputKind.BUTTON, ControllerActionId.PROJECT_FILE_ACTION, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)));
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "master",
        Set.of (
            new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT),
            new SurfaceClaim (SurfaceArea.ENCODER_TURNS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.ENCODER_TOUCHES, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.OUTPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.OUTPUT),
            new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT),
            new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.OUTPUT)),
        Set.of ());


    @Override
    public String id ()
    {
        return "master-controls";
    }


    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return SUBSCRIPTIONS;
    }


    @Override
    public Set<ControllerActionBinding> actionBindings ()
    {
        return ACTION_BINDINGS;
    }


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


    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input) || input.kind () != InputKind.RELATIVE)
            return List.of ();
        final ParameterTargetSnapshot target = ParameterAlignment.target (snapshot, PARAMETER_BINDINGS.get (input.controlId ()));
        return target == null ? List.of () : List.of (new AdjustParameterValueEffect (target.target (), input.value () * PARAMETER_STEP_SIZE));
    }


    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        final MasterSnapshot master = snapshot.bridge ().master ();
        if (!master.available ())
            return ResolvedControllerAction.of (binding.intent (), List::of);

        if (ENGINE.equals (input.controlId ()))
            return effect (binding, new SetProjectEngineEffect (master.projectIdentity (), !master.engineActive ()));
        if (PREVIOUS.equals (input.controlId ()) && master.canPrevious ())
            return effect (binding, new NavigateProjectEffect (master.projectIdentity (), ProjectNavigationDirection.PREVIOUS));
        if (NEXT.equals (input.controlId ()) && master.canNext ())
            return effect (binding, new NavigateProjectEffect (master.projectIdentity (), ProjectNavigationDirection.NEXT));
        if (OPEN.equals (input.controlId ()))
            return effect (binding, new ProjectFileActionEffect (master.projectIdentity (), ProjectFileAction.OPEN));
        if (SAVE.equals (input.controlId ()))
            return effect (binding, new ProjectFileActionEffect (master.projectIdentity (), ProjectFileAction.SAVE));
        return ResolvedControllerAction.of (binding.intent (), List::of);
    }


    @Override
    public de.mossgrabers.pull.core.view.InputTarget inputTarget (final ControlId control, final InputKind kind, final ControllerSnapshot snapshot)
    {
        if (kind == InputKind.TOUCH)
        {
            final ParameterSlot slot = PARAMETER_BINDINGS.get (control);
            if (!ParameterAlignment.masterContextAligned (snapshot) || ParameterAlignment.contradicts (snapshot, slot)) return null;
        }
        if (kind == InputKind.BUTTON && ACTION_BINDINGS.stream ().anyMatch (action -> action.controlId ().equals (control)))
        {
            final MasterSnapshot master = snapshot.bridge ().master ();
            return ParameterAlignment.masterContextAligned (snapshot)
                ? new de.mossgrabers.pull.core.view.InputTarget.Context (control, "project", master.projectIdentity (), 0) : null;
        }
        return ControllerView.super.inputTarget (control, kind, snapshot);
    }


    @Override
    public Set<ControlId> parameterTouchControls (final ControllerSnapshot snapshot)
    {
        return SurfaceArea.ENCODER_TOUCHES.controls ();
    }


    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final MasterSnapshot master = snapshot.bridge ().master ();
        if (!master.available ())
            return ViewOutput.empty ();

        final PageVisuals visuals = MasterPageRenderer.render (MixerPageProjections.master (snapshot));
        return new ViewOutput (visuals.lights (), Map.of (), visuals.display (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), DesiredNotePerformance.inactive (), DesiredNoteRepeat.unowned (), DesiredControllerMappings.empty ());
    }


    private static ResolvedControllerAction effect (final ControllerActionBinding binding, final CoreEffect effect)
    {
        return ResolvedControllerAction.of (binding.intent (), () -> List.of (effect));
    }


    private static ControlId row (final int row, final int column)
    {
        return PushControlIds.button ("ROW" + row + "_" + column);
    }
}
