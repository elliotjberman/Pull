// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
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
    private static final Map<ControlId, ParameterSlot> PARAMETER_BINDINGS = projectParameterBindings ();
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "default",
        Set.of (
            new SurfaceClaim (SurfaceArea.ENCODER_TURNS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.ENCODER_TOUCHES, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
            new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT)),
        Set.of (ControllerViewFacet.PROJECT_MACRO_CONTROLS));


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "project-macro-controls";
    }


    /** {@inheritDoc} */
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


    /** {@inheritDoc} */
    @Override
    public Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return Set.of (BridgeSubscription.PARAMETERS);
    }


    /** {@inheritDoc} */
    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ControllerInputEvent input) || input.kind () != InputKind.RELATIVE)
            return List.of ();
        final ParameterSlot slot = PARAMETER_BINDINGS.get (input.controlId ());
        if (slot == null)
            return List.of ();
        final ParameterTargetSnapshot target = snapshot.bridge ().parameters ().slots ().get (slot);
        return target == null ? List.of () : List.of (new AdjustParameterValueEffect (target.target (), input.value ()));
    }


    private static Map<ControlId, ParameterSlot> projectParameterBindings ()
    {
        final Map<ControlId, ParameterSlot> bindings = new LinkedHashMap<> ();
        for (int index = 0; index < ParameterSlot.BANK_SIZE; index++)
            bindings.put (PushControlIds.continuous ("KNOB" + (index + 1)), ParameterSlot.projectRemote (index));
        return Map.copyOf (bindings);
    }
}
