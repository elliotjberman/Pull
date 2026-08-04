// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/**
 * Stable shell identifier for a physical or logical controller input/output.
 *
 * @param value The identifier
 */
public record ControlId (String value)
{
    /**
     * Validate the identifier.
     */
    public ControlId
    {
        Objects.requireNonNull (value, "value");
        if (value.isBlank ())
            throw new IllegalArgumentException ("value must not be blank");
    }
}
