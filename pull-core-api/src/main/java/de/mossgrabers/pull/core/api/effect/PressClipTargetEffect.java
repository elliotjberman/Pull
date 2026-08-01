// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;

import java.util.Objects;

/**
 * Request one armed target as the sole active member of the shell-managed momentary clip session.
 * If another target is active, the shell keeps this as a value-only pending intent and launches it
 * only after the active target has returned to the opaque base.
 *
 * @param owner Logical control requesting active session ownership
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
