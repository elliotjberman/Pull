// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.event;

import de.mossgrabers.pull.core.api.ControlId;
import java.util.Objects;
import java.util.List;

/**
 * One normalized event from the permanent controller-input canopy.
 *
 * <p>Buttons, pads, touches, and pedals use begin/long/end phases with a 7-bit velocity or
 * pressure value. Relative inputs use {@link InputPhase#UPDATE} with an exact signed, potentially
 * coalesced delta. Absolute inputs use {@code [0, 16383]}, and pressure uses
 * {@code [0, 127]}.</p>
 *
 * @param sequence Monotonic shell event sequence
 * @param monotonicTimeNanos Shell-monotonic event time
 * @param controlId Stable physical control identity
 * @param kind Input kind
 * @param phase Gesture phase
 * @param value Kind-specific normalized value
 * @param relativeSamples Ordered callbacks, for discrete controls whose clamping depends on direction changes
 */
public record ControllerInputEvent (long sequence, long monotonicTimeNanos, ControlId controlId, InputKind kind, InputPhase phase, long value, List<Long> relativeSamples) implements CoreEvent
{
    /** Maximum callbacks in one relative-input batch. */
    public static final int RELATIVE_SAMPLE_CAPACITY = 64;

    public ControllerInputEvent (final long sequence, final long monotonicTimeNanos, final ControlId controlId, final InputKind kind, final InputPhase phase, final long value)
    {
        this (sequence, monotonicTimeNanos, controlId, kind, phase, value, kind == InputKind.RELATIVE ? List.of (value) : List.of ());
    }

    /**
     * Validate the normalized input.
     */
    public ControllerInputEvent
    {
        if (sequence < 0)
            throw new IllegalArgumentException ("sequence must not be negative");
        if (monotonicTimeNanos < 0)
            throw new IllegalArgumentException ("monotonicTimeNanos must not be negative");

        controlId = Objects.requireNonNull (controlId, "controlId");
        kind = Objects.requireNonNull (kind, "kind");
        phase = Objects.requireNonNull (phase, "phase");
        relativeSamples = List.copyOf (relativeSamples);
        if (kind == InputKind.RELATIVE)
        {
            if (relativeSamples.isEmpty () || relativeSamples.size () > RELATIVE_SAMPLE_CAPACITY || relativeSamples.contains (0L) ||
                relativeSamples.stream ().reduce (0L, Math::addExact) != value)
                throw new IllegalArgumentException ("relative samples must be a bounded nonzero sequence summing to value");
        }
        else if (!relativeSamples.isEmpty ())
            throw new IllegalArgumentException ("only relative input carries samples");
        switch (kind)
        {
            case BUTTON, PAD, TOUCH, PEDAL -> validateGestureValue (phase, value);
            case RELATIVE ->
            {
                requirePhase (phase, InputPhase.UPDATE, kind);
            }
            case ABSOLUTE ->
            {
                requirePhase (phase, InputPhase.UPDATE, kind);
                requireRange (value, 0, 16383, "absolute value");
            }
            case POLY_PRESSURE, CHANNEL_PRESSURE ->
            {
                requirePhase (phase, InputPhase.UPDATE, kind);
                requireRange (value, 0, 127, "pressure value");
            }
        }
    }


    private static void validateGestureValue (final InputPhase phase, final long value)
    {
        if (phase == InputPhase.UPDATE)
            throw new IllegalArgumentException ("discrete and touch inputs do not use UPDATE");
        if (phase == InputPhase.END)
        {
            if (value != 0)
                throw new IllegalArgumentException ("ended input value must be zero");
            return;
        }
        requireRange (value, 0, 127, "active input value");
    }


    private static void requirePhase (final InputPhase actual, final InputPhase expected, final InputKind kind)
    {
        if (actual != expected)
            throw new IllegalArgumentException (kind + " input requires phase " + expected);
    }


    private static void requireRange (final long value, final long minimum, final long maximum, final String name)
    {
        if (value < minimum || value > maximum)
            throw new IllegalArgumentException (name + " must be between " + minimum + " and " + maximum);
    }
}
