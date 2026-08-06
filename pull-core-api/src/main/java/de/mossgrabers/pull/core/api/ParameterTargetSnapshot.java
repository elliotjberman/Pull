// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/**
 * Authoritative value of one currently addressable parameter target.
 *
 * @param target Opaque target and actuator generation
 * @param name Authoritative parameter name
 * @param value Authoritative host value in the shell's controller range
 * @param modulatedValue Authoritative modulated value in the same range
 * @param displayedValue Authoritative host-formatted value
 * @param numberOfSteps Discrete step count, or {@code -1} for a continuous parameter
 * @param tolerance Read-back tolerance for equality
 */
public record ParameterTargetSnapshot (ParameterTargetRef target, String name, double value, double modulatedValue, String displayedValue, int numberOfSteps, double tolerance)
{
    /**
     * Validate the snapshot.
     */
    public ParameterTargetSnapshot
    {
        target = Objects.requireNonNull (target, "target");
        name = Objects.requireNonNullElse (name, "");
        if (!Double.isFinite (value))
            throw new IllegalArgumentException ("parameter value must be finite");
        if (!Double.isFinite (modulatedValue))
            throw new IllegalArgumentException ("parameter modulated value must be finite");
        displayedValue = Objects.requireNonNullElse (displayedValue, "");
        if (numberOfSteps < -1)
            throw new IllegalArgumentException ("parameter step count must be -1 or non-negative");
        if (!Double.isFinite (tolerance) || tolerance < 0)
            throw new IllegalArgumentException ("parameter tolerance must be finite and non-negative");
    }


    /** Convenience constructor for value-only callers and deterministic tests. */
    public ParameterTargetSnapshot (final ParameterTargetRef target, final double value, final double tolerance)
    {
        this (target, "", value, value, "", -1, tolerance);
    }


    /**
     * Test an expected value against authoritative read-back.
     *
     * @param expected Expected value
     * @return True when within this target's tolerance
     */
    public boolean isAt (final double expected)
    {
        return Double.isFinite (expected) && Math.abs (this.value - expected) <= this.tolerance;
    }
}
