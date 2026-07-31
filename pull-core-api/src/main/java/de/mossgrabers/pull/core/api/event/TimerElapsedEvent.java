// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.event;

import de.mossgrabers.pull.core.api.TimerId;

import java.util.Objects;

/**
 * A logical timer delivered by the shell.
 *
 * @param sequence The event sequence
 * @param monotonicTimeNanos The shell monotonic event time
 * @param timerId The timer identifier
 */
public record TimerElapsedEvent (long sequence, long monotonicTimeNanos, TimerId timerId) implements CoreEvent
{
    /**
     * Validate the event.
     */
    public TimerElapsedEvent
    {
        if (sequence < 0)
            throw new IllegalArgumentException ("sequence must not be negative");
        if (monotonicTimeNanos < 0)
            throw new IllegalArgumentException ("monotonicTimeNanos must not be negative");
        timerId = Objects.requireNonNull (timerId, "timerId");
    }
}
