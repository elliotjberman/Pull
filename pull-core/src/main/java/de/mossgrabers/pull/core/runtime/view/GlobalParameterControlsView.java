// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.Map;
import java.util.Set;


/**
 * Fixed global tempo and master-volume parameter controls.
 */
public final class GlobalParameterControlsView implements ControllerView
{
    private static final ViewProfile PROFILE = ViewProfile.fixed ("global-parameters", Set.of (), Set.of ());
    private static final Map<ControlId, ParameterSlot> PARAMETER_BINDINGS = Map.of (
        PushControlIds.continuous ("TEMPO"), ParameterSlot.TEMPO,
        PushControlIds.continuous ("MASTER_KNOB"), ParameterSlot.MASTER_VOLUME);


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "global-parameter-controls";
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
}
