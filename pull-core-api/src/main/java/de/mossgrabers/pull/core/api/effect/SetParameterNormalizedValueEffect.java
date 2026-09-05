// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ParameterTargetRef;
import java.util.Objects;

/** Set one exact currently applicable parameter, in its normalized host range. */
public record SetParameterNormalizedValueEffect (ParameterTargetRef target, double value) implements CoreEffect
{
    public SetParameterNormalizedValueEffect
    {
        target = Objects.requireNonNull (target, "target");
        if (!Double.isFinite (value) || value < 0 || value > 1)
            throw new IllegalArgumentException ("Normalized parameter value must be in [0, 1]");
    }
}
