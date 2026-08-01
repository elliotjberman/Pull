// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.event;

import de.mossgrabers.pull.core.api.ControlId;

import java.util.Objects;

/**
 * Normalized absolute hardware input.
 *
 * @param sequence Monotonic event sequence
 * @param monotonicTimeNanos Shell monotonic event time
 * @param controlId Logical hardware control
 * @param normalizedValue Absolute value in the range {@code [0, 1]}
 */
public record AbsoluteInputEvent (long sequence, long monotonicTimeNanos, ControlId controlId, double normalizedValue) implements CoreEvent
{
    /** Validate values. */
    public AbsoluteInputEvent
    {
        if (sequence < 0)
            throw new IllegalArgumentException ("sequence must not be negative");
        if (monotonicTimeNanos < 0)
            throw new IllegalArgumentException ("monotonicTimeNanos must not be negative");
        controlId = Objects.requireNonNull (controlId, "controlId");
        if (!Double.isFinite (normalizedValue) || normalizedValue < 0 || normalizedValue > 1)
            throw new IllegalArgumentException ("normalizedValue must be finite and in [0, 1]");
    }
}
