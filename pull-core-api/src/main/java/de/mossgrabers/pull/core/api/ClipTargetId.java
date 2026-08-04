// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

/**
 * Shell-issued identity for a clip target.
 *
 * @param value The non-negative identity
 */
public record ClipTargetId (long value)
{
    /**
     * Validate the identity.
     */
    public ClipTargetId
    {
        if (value < 0)
            throw new IllegalArgumentException ("value must not be negative");
    }
}
