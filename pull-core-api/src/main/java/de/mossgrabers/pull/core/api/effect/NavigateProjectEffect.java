// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;


/** Request navigation from one exact observed project tab. */
public record NavigateProjectEffect (String expectedProjectIdentity, ProjectNavigationDirection direction) implements CoreEffect
{
    public NavigateProjectEffect
    {
        expectedProjectIdentity = Objects.requireNonNull (expectedProjectIdentity, "expectedProjectIdentity");
        if (expectedProjectIdentity.isBlank ())
            throw new IllegalArgumentException ("expectedProjectIdentity must not be blank");
        direction = Objects.requireNonNull (direction, "direction");
    }
}
