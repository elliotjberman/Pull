// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;


/** Request absolute transport state through the exact currently visible project proxy. */
public record SetProjectTransportStateEffect (String expectedProjectIdentity, TransportState state, boolean enabled) implements CoreEffect
{
    public SetProjectTransportStateEffect
    {
        expectedProjectIdentity = Objects.requireNonNull (expectedProjectIdentity, "expectedProjectIdentity");
        if (expectedProjectIdentity.isBlank ())
            throw new IllegalArgumentException ("expectedProjectIdentity must not be blank");
        state = Objects.requireNonNull (state, "state");
    }
}
