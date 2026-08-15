// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ClipTimelineTarget;

import java.util.Objects;


/**
 * Request one selected launcher clip's loop and playback range.
 *
 * @param target Exact target observed when the gesture began
 * @param start Range start in quarter-note beats
 * @param length Positive range length in quarter-note beats
 */
public record SetClipTimelineRangeEffect (ClipTimelineTarget target, double start, double length) implements CoreEffect
{
    /** Validate the requested range. */
    public SetClipTimelineRangeEffect
    {
        target = Objects.requireNonNull (target, "target");
        if (!Double.isFinite (start) || start < 0)
            throw new IllegalArgumentException ("start must be finite and not negative");
        if (!Double.isFinite (length) || length <= 0)
            throw new IllegalArgumentException ("length must be finite and positive");
    }
}
