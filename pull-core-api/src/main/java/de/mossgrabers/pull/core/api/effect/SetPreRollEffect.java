// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.PreRoll;
import java.util.Objects;

/** Submit a pre-roll setting to the exact current project. */
public record SetPreRollEffect (String projectIdentity, PreRoll preRoll) implements CoreEffect
{
    public SetPreRollEffect
    {
        projectIdentity = Objects.requireNonNull (projectIdentity, "projectIdentity");
        preRoll = Objects.requireNonNull (preRoll, "preRoll");
        if (projectIdentity.isBlank ()) throw new IllegalArgumentException ("projectIdentity must not be blank");
    }
}
