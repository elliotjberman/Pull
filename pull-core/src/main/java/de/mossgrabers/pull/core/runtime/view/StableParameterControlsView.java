// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;


/**
 * Explicit compatibility view for the stable controller mode's active eight-parameter window.
 */
public final class StableParameterControlsView implements ControllerView
{
    private static final ViewProfile PROFILE = ViewProfile.fixed ("stable-active-parameters", Set.of (), Set.of ());
    private static final Map<ControlId, ParameterSlot> PARAMETER_BINDINGS = bindings ();


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "stable-parameter-controls";
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


    private static Map<ControlId, ParameterSlot> bindings ()
    {
        final Map<ControlId, ParameterSlot> bindings = new LinkedHashMap<> ();
        for (int index = 0; index < ParameterSlot.BANK_SIZE; index++)
            bindings.put (PushControlIds.continuous ("KNOB" + (index + 1)), ParameterSlot.active (index));
        return Map.copyOf (bindings);
    }
}
