// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.event;

/**
 * Notification that authoritative shell state changed without a physical input event.
 *
 * @param sequence The event sequence
 * @param monotonicTimeNanos The shell monotonic event time
 */
public record SnapshotChangedEvent (long sequence, long monotonicTimeNanos) implements CoreEvent
{
    /**
     * Validate the event.
     */
    public SnapshotChangedEvent
    {
        if (sequence < 0)
            throw new IllegalArgumentException ("sequence must not be negative");
        if (monotonicTimeNanos < 0)
            throw new IllegalArgumentException ("monotonicTimeNanos must not be negative");
    }
}
