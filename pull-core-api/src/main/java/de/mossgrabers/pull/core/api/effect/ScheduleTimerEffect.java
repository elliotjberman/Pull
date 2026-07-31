// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.TimerId;

import java.util.Objects;

/**
 * Schedule or replace a logical timer at an absolute shell-monotonic deadline.
 *
 * @param timerId The timer identifier
 * @param deadlineNanos The absolute deadline
 */
public record ScheduleTimerEffect (TimerId timerId, long deadlineNanos) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public ScheduleTimerEffect
    {
        timerId = Objects.requireNonNull (timerId, "timerId");
        if (deadlineNanos < 0)
            throw new IllegalArgumentException ("deadlineNanos must not be negative");
    }
}
