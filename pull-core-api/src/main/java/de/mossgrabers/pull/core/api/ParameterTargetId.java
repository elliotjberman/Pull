// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

/**
 * Opaque identity of a shell-owned parameter actuator.
 *
 * @param value Stable non-negative identity within one parameter-catalog generation
 */
public record ParameterTargetId (long value)
{
    /** Validate the opaque value. */
    public ParameterTargetId
    {
        if (value < 0)
            throw new IllegalArgumentException ("value must not be negative");
    }
}
