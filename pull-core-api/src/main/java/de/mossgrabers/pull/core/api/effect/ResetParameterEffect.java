// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ParameterTargetRef;

import java.util.Objects;


/** Request the host-defined default value for one currently fenced parameter actuator. */
public record ResetParameterEffect (ParameterTargetRef target) implements CoreEffect
{
    /** Validate the exact target. */
    public ResetParameterEffect
    {
        target = Objects.requireNonNull (target, "target");
    }
}
