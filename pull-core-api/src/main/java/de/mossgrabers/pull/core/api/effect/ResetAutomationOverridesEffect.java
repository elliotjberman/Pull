// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/** Submit native reset of automation overrides in the exact current project. */
public record ResetAutomationOverridesEffect (String projectIdentity) implements CoreEffect
{
    public ResetAutomationOverridesEffect
    {
        projectIdentity = Objects.requireNonNull (projectIdentity, "projectIdentity");
        if (projectIdentity.isBlank ()) throw new IllegalArgumentException ("projectIdentity must not be blank");
    }
}
