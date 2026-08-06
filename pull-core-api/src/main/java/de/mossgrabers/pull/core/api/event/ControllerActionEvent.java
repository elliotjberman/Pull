// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.event;

import de.mossgrabers.pull.core.api.ControllerActionIntent;

import java.util.Objects;


/**
 * One stable-owned command resolved to semantic intent before its behavior executes.
 *
 * @param sequence Monotonic shell event sequence
 * @param monotonicTimeNanos Shell-monotonic event time
 * @param intent Resolved semantic action
 */
public record ControllerActionEvent (long sequence, long monotonicTimeNanos, ControllerActionIntent intent) implements CoreEvent
{
    /** Validate the semantic event. */
    public ControllerActionEvent
    {
        if (sequence < 0)
            throw new IllegalArgumentException ("sequence must not be negative");
        if (monotonicTimeNanos < 0)
            throw new IllegalArgumentException ("monotonicTimeNanos must not be negative");
        intent = Objects.requireNonNull (intent, "intent");
    }
}
