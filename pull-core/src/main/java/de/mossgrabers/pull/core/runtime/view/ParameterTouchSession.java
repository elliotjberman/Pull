// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.AutomationSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.DesiredParameterTouches;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetAutomationWriteEffect;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/** Core-owned release policy retained across page changes for explicitly admitted touch gestures. */
public final class ParameterTouchSession
{
    private final Map<ControlId, String> projects = new LinkedHashMap<> ();


    boolean contains (final ControlId control)
    {
        return this.projects.containsKey (control);
    }


    boolean begin (final ControlId control, final AutomationSnapshot automation)
    {
        if (this.projects.containsKey (control))
            return false;
        if (this.projects.size () >= DesiredParameterTouches.CAPACITY)
            throw new IllegalStateException ("Parameter touch session exceeds its bounded capacity");
        this.projects.put (control, automation.projectIdentity ());
        return true;
    }


    List<CoreEffect> end (final ControlId control, final AutomationSnapshot automation)
    {
        final String project = this.projects.remove (control);
        if (automation.available () && automation.stopOnTouchRelease () && automation.writingEnabled () && automation.projectIdentity ().equals (project))
            return List.of (new SetAutomationWriteEffect (project, false));
        return List.of ();
    }
}
