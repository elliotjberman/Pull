// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;

import java.util.Objects;

/**
 * Press one newly armed target as the active member of the shell-managed momentary clip session.
 * Previously active targets are retained as native Return ancestry until the session is released;
 * use {@link ReactivateClipTargetEffect} to reveal one of those frozen frames.
 *
 * @param owner Logical control which becomes the active session owner
 * @param catalogGeneration Required selected-track catalog generation
 * @param target Target to press
 * @param launchPolicy Launch and release policy frozen into the lease
 */
public record PressClipTargetEffect (ControlId owner, long catalogGeneration, ClipTargetId target, ClipLaunchPolicy launchPolicy) implements CoreEffect
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
        launchPolicy = Objects.requireNonNull (launchPolicy, "launchPolicy");
    }
}
