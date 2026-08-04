// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/**
 * Request an absolute numeric transport value.
 *
 * @param value Value to set
 * @param amount Absolute value in the unit documented by {@code value}
 */
public record SetTransportValueEffect (TransportValue value, double amount) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public SetTransportValueEffect
    {
        value = Objects.requireNonNull (value, "value");
        if (!Double.isFinite (amount))
            throw new IllegalArgumentException ("amount must be finite");
        if (value == TransportValue.TEMPO && amount <= 0)
            throw new IllegalArgumentException ("tempo must be positive");
        if (value == TransportValue.POSITION_BEATS && amount < 0)
            throw new IllegalArgumentException ("position must not be negative");
    }
}
