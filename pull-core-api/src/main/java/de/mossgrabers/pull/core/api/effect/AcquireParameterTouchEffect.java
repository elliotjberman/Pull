// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import java.util.Objects;

/** Acquire an admitted desired touch at this exact point in an ordered effect sequence. */
public record AcquireParameterTouchEffect (ControlId owner, ParameterTargetRef target) implements CoreEffect
{
    public AcquireParameterTouchEffect
    {
        owner = Objects.requireNonNull (owner, "owner");
        target = Objects.requireNonNull (target, "target");
    }
}
