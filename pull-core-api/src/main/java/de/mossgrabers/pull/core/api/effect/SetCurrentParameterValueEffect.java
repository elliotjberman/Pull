// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ParameterTargetRef;
import java.util.Objects;

/** Set one currently applicable exact target in the same units as its observed value. */
public record SetCurrentParameterValueEffect (ParameterTargetRef target, double value) implements CoreEffect
{
    public SetCurrentParameterValueEffect
    {
        Objects.requireNonNull (target, "target");
        if (!Double.isFinite (value)) throw new IllegalArgumentException ("parameter value must be finite");
    }
}
