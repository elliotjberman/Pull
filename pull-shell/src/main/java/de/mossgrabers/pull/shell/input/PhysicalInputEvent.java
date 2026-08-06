// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.input;

import de.mossgrabers.pull.core.api.ControllerActionIntent;

import java.util.Objects;
import java.util.Optional;

/**
 * A shell-owned physical input sample. The generic control key lets the eventual API adapter use
 * its own stable identifier without coupling this router to a particular core API version.
 *
 * @param sequence Monotonically increasing physical-input sequence
 * @param timeNanos Monotonic sample time in nanoseconds
 * @param ownerGeneration Reloadable-core generation which owned this sample, or zero when none
 * @param control Registered physical control key
 * @param kind Physical input kind
 * @param phase Input phase
 * @param value Raw or decoded value; coalesced relative values may exceed the range of one sample
 * @param stableAction Stable-owned semantic action resolved at begin, when present
 * @param <C> Control key type
 */
public record PhysicalInputEvent<C> (long sequence, long timeNanos, long ownerGeneration, C control, InputKind kind, InputPhase phase, long value, Optional<ControllerActionIntent> stableAction)
{
    /** Construct a physical input without a stable-owned semantic action. */
    public PhysicalInputEvent (final long sequence, final long timeNanos, final long ownerGeneration, final C control, final InputKind kind, final InputPhase phase, final long value)
    {
        this (sequence, timeNanos, ownerGeneration, control, kind, phase, value, Optional.empty ());
    }


    /**
     * Validate an input event.
     */
    public PhysicalInputEvent
    {
        if (sequence <= 0)
            throw new IllegalArgumentException ("sequence must be positive");
        if (ownerGeneration < 0)
            throw new IllegalArgumentException ("ownerGeneration must not be negative");
        Objects.requireNonNull (control, "control");
        Objects.requireNonNull (kind, "kind");
        Objects.requireNonNull (phase, "phase");
        stableAction = Objects.requireNonNull (stableAction, "stableAction");
        if (stableAction.isPresent () && (!kind.isEdge () || phase != InputPhase.BEGIN))
            throw new IllegalArgumentException ("stable semantic actions are resolved only for edge BEGIN events");
        if (kind.isEdge () == (phase == InputPhase.CHANGE))
            throw new IllegalArgumentException ("phase " + phase + " is invalid for " + kind);
    }


    PhysicalInputEvent<C> withValue (final long newValue)
    {
        return new PhysicalInputEvent<> (this.sequence, this.timeNanos, this.ownerGeneration, this.control, this.kind, this.phase, newValue, this.stableAction);
    }
}
