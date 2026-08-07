// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;


/** Request an absolute audio-engine state for one exact observed project tab. */
public record SetProjectEngineEffect (String expectedProjectIdentity, boolean active) implements CoreEffect
{
    public SetProjectEngineEffect
    {
        expectedProjectIdentity = Objects.requireNonNull (expectedProjectIdentity, "expectedProjectIdentity");
        if (expectedProjectIdentity.isBlank ())
            throw new IllegalArgumentException ("expectedProjectIdentity must not be blank");
    }
}
