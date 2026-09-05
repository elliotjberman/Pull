// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;


/** Request one native transport tap against the currently observed project. */
public record TapTempoEffect (String projectIdentity) implements CoreEffect
{
    public TapTempoEffect
    {
        projectIdentity = Objects.requireNonNull (projectIdentity, "projectIdentity");
        if (projectIdentity.isBlank ())
            throw new IllegalArgumentException ("projectIdentity must not be blank");
    }
}
