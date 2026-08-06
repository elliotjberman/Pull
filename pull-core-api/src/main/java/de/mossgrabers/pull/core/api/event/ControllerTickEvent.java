// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.event;


/**
 * Bounded controller-cycle observation requested while retained state needs host reconciliation.
 *
 * @param sequence Monotonic shell event sequence
 * @param monotonicTimeNanos Shell-monotonic event time
 */
public record ControllerTickEvent (long sequence, long monotonicTimeNanos) implements CoreEvent
{
    /** Validate the event. */
    public ControllerTickEvent
    {
        if (sequence < 0)
            throw new IllegalArgumentException ("sequence must not be negative");
        if (monotonicTimeNanos < 0)
            throw new IllegalArgumentException ("monotonicTimeNanos must not be negative");
    }
}
