// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/**
 * Host-independent policy for one momentary clip launch.
 *
 * @param quantization When the target starts
 * @param mode Where playback starts within the target
 * @param releaseTrigger Which session-configured release action is invoked
 */
public record ClipLaunchPolicy (ClipLaunchQuantization quantization, ClipLaunchMode mode, ClipReleaseTrigger releaseTrigger)
{
    /**
     * Validate the policy.
     */
    public ClipLaunchPolicy
    {
        quantization = Objects.requireNonNull (quantization, "quantization");
        mode = Objects.requireNonNull (mode, "mode");
        releaseTrigger = Objects.requireNonNull (releaseTrigger, "releaseTrigger");
    }
}
