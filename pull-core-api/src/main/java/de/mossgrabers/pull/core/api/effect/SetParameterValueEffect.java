// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ParameterTargetId;

import java.util.Objects;

/**
 * Set one exact shell-owned parameter actuator immediately.
 *
 * @param catalogGeneration Required parameter-catalog generation
 * @param target Opaque target identity from that catalog
 * @param normalizedValue Absolute value in the range {@code [0, 1]}
 */
public record SetParameterValueEffect (long catalogGeneration, ParameterTargetId target, double normalizedValue) implements CoreEffect
{
    /** Validate values. */
    public SetParameterValueEffect
    {
        if (catalogGeneration < 0)
            throw new IllegalArgumentException ("catalogGeneration must not be negative");
        target = Objects.requireNonNull (target, "target");
        if (!Double.isFinite (normalizedValue) || normalizedValue < 0 || normalizedValue > 1)
            throw new IllegalArgumentException ("normalizedValue must be finite and in [0, 1]");
    }
}
