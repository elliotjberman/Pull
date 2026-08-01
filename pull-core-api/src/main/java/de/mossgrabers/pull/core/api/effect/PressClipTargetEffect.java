// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;

import java.util.Objects;

/**
 * Acquire an owner-scoped momentary lease and press one armed clip target.
 *
 * @param owner Logical control which owns the lease
 * @param catalogGeneration Required selected-track catalog generation
 * @param target Target to press
 */
public record PressClipTargetEffect (ControlId owner, long catalogGeneration, ClipTargetId target) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public PressClipTargetEffect
    {
        owner = Objects.requireNonNull (owner, "owner");
        if (catalogGeneration < 0)
            throw new IllegalArgumentException ("catalogGeneration must not be negative");
        target = Objects.requireNonNull (target, "target");
    }
}
