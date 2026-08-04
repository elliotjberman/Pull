// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/**
 * Request an absolute boolean transport state.
 *
 * @param state State to set
 * @param enabled Desired state
 */
public record SetTransportStateEffect (TransportState state, boolean enabled) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public SetTransportStateEffect
    {
        state = Objects.requireNonNull (state, "state");
    }
}
