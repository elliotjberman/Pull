// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;


/** Request the current project's unified Automation Write state with an exact project fence. */
public record SetAutomationWriteEffect (String projectIdentity, boolean enabled) implements CoreEffect
{
    public SetAutomationWriteEffect
    {
        projectIdentity = Objects.requireNonNull (projectIdentity, "projectIdentity");
        if (projectIdentity.isBlank ())
            throw new IllegalArgumentException ("automation effects require a project identity");
    }
}
