// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;


/**
 * Request absolute transport state for an exact project while preserving the visible project.
 *
 * <p>The stable shell applies local requests directly. For a different target it owns the complete
 * bounded visit, authoritative acknowledgement, and exact return transaction.</p>
 */
public record SetProjectTransportStateEffect (String originProjectIdentity, String targetProjectIdentity, TransportState state, boolean enabled) implements CoreEffect
{
    public SetProjectTransportStateEffect
    {
        originProjectIdentity = Objects.requireNonNull (originProjectIdentity, "originProjectIdentity");
        targetProjectIdentity = Objects.requireNonNull (targetProjectIdentity, "targetProjectIdentity");
        if (originProjectIdentity.isBlank ())
            throw new IllegalArgumentException ("originProjectIdentity must not be blank");
        if (targetProjectIdentity.isBlank ())
            throw new IllegalArgumentException ("targetProjectIdentity must not be blank");
        state = Objects.requireNonNull (state, "state");
    }
}
