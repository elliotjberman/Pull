// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.input;

import java.util.Objects;

/**
 * A shell-owned physical input sample. The generic control key lets the eventual API adapter use
 * its own stable identifier without coupling this router to a particular core API version.
 *
 * @param sequence Monotonically increasing physical-input sequence
 * @param timeNanos Monotonic sample time in nanoseconds
 * @param control Registered physical control key
 * @param kind Physical input kind
 * @param phase Input phase
 * @param value Raw or decoded value; coalesced relative values may exceed the range of one sample
 * @param <C> Control key type
 */
public record PhysicalInputEvent<C> (long sequence, long timeNanos, C control, InputKind kind, InputPhase phase, long value)
{
    /**
     * Validate an input event.
     */
    public PhysicalInputEvent
    {
        if (sequence <= 0)
            throw new IllegalArgumentException ("sequence must be positive");
        Objects.requireNonNull (control, "control");
        Objects.requireNonNull (kind, "kind");
        Objects.requireNonNull (phase, "phase");
        if (kind.isEdge () == (phase == InputPhase.CHANGE))
            throw new IllegalArgumentException ("phase " + phase + " is invalid for " + kind);
    }


    PhysicalInputEvent<C> withValue (final long newValue)
    {
        return new PhysicalInputEvent<> (this.sequence, this.timeNanos, this.control, this.kind, this.phase, newValue);
    }
}
