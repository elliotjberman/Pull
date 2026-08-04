// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.event;

import de.mossgrabers.pull.core.api.ControlId;

import java.util.Objects;

/**
 * A normalized press or release.
 *
 * @param sequence The event sequence
 * @param monotonicTimeNanos The shell monotonic event time
 * @param controlId The control
 * @param pressed True for press, false for release
 */
public record ButtonInputEvent (long sequence, long monotonicTimeNanos, ControlId controlId, boolean pressed) implements CoreEvent
{
    /**
     * Validate the event.
     */
    public ButtonInputEvent
    {
        if (sequence < 0)
            throw new IllegalArgumentException ("sequence must not be negative");
        if (monotonicTimeNanos < 0)
            throw new IllegalArgumentException ("monotonicTimeNanos must not be negative");
        controlId = Objects.requireNonNull (controlId, "controlId");
    }
}
