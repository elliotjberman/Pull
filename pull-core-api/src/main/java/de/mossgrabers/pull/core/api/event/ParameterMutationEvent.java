// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.event;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;

import java.util.Objects;


/**
 * First controller mutation observed for a target before established stable dispatch.
 *
 * @param sequence Monotonic shell event sequence
 * @param monotonicTimeNanos Shell-monotonic event time
 * @param controlId Physical control selected by the active view
 * @param slot View-independent parameter slot
 * @param target Authoritative target state before mutation
 */
public record ParameterMutationEvent (long sequence, long monotonicTimeNanos, ControlId controlId, ParameterSlot slot, ParameterTargetSnapshot target) implements CoreEvent
{
    /**
     * Validate the event.
     */
    public ParameterMutationEvent
    {
        if (sequence < 0)
            throw new IllegalArgumentException ("sequence must not be negative");
        if (monotonicTimeNanos < 0)
            throw new IllegalArgumentException ("monotonicTimeNanos must not be negative");
        controlId = Objects.requireNonNull (controlId, "controlId");
        slot = Objects.requireNonNull (slot, "slot");
        target = Objects.requireNonNull (target, "target");
    }
}
