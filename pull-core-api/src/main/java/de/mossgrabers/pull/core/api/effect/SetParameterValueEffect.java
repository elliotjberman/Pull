// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ParameterTargetRef;

import java.util.Objects;


/**
 * Request an absolute value through one exact retained parameter actuator.
 *
 * @param target Opaque target and actuator generation
 * @param value Absolute value in the target's native controller range
 */
public record SetParameterValueEffect (ParameterTargetRef target, double value) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public SetParameterValueEffect
    {
        target = Objects.requireNonNull (target, "target");
        if (!Double.isFinite (value))
            throw new IllegalArgumentException ("parameter value must be finite");
    }
}
