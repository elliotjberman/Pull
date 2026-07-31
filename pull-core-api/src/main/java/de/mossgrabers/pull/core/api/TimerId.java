// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/**
 * Core-owned logical timer identifier.
 *
 * @param value The identifier
 */
public record TimerId (String value)
{
    /**
     * Validate the identifier.
     */
    public TimerId
    {
        Objects.requireNonNull (value, "value");
        if (value.isBlank ())
            throw new IllegalArgumentException ("value must not be blank");
    }
}
