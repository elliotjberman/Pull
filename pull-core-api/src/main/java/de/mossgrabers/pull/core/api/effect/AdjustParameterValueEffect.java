// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ParameterTargetRef;

import java.util.Objects;


/** Request a relative change through one currently fenced parameter actuator. */
public record AdjustParameterValueEffect (ParameterTargetRef target, double delta) implements CoreEffect
{
    /** Validate the exact target and finite nonzero delta. */
    public AdjustParameterValueEffect
    {
        target = Objects.requireNonNull (target, "target");
        if (!Double.isFinite (delta) || delta == 0)
            throw new IllegalArgumentException ("parameter delta must be finite and nonzero");
    }
}
