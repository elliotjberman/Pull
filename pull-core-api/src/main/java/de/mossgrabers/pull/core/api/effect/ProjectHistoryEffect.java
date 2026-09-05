// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/** Submit one native history command to the currently observed project. */
public record ProjectHistoryEffect (String projectIdentity, ProjectHistoryAction action) implements CoreEffect
{
    public ProjectHistoryEffect
    {
        projectIdentity = Objects.requireNonNull (projectIdentity, "projectIdentity");
        action = Objects.requireNonNull (action, "action");
        if (projectIdentity.isBlank ())
            throw new IllegalArgumentException ("projectIdentity must not be blank");
    }
}
