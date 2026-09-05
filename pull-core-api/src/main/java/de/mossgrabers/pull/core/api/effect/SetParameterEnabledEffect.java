// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ParameterTargetRef;
import java.util.Objects;

/** Submit absolute enablement to an exact parameter which exposes that capability. */
public record SetParameterEnabledEffect (ParameterTargetRef target, boolean enabled) implements CoreEffect
{
    public SetParameterEnabledEffect
    {
        target = Objects.requireNonNull (target, "target");
    }
}
