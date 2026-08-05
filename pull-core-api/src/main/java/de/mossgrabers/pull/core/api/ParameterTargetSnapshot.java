// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/**
 * Authoritative value of one currently addressable parameter target.
 *
 * @param target Opaque target and actuator generation
 * @param value Authoritative host value
 * @param tolerance Read-back tolerance for equality
 */
public record ParameterTargetSnapshot (ParameterTargetRef target, double value, double tolerance)
{
    /**
     * Validate the snapshot.
     */
    public ParameterTargetSnapshot
    {
        target = Objects.requireNonNull (target, "target");
        if (!Double.isFinite (value))
            throw new IllegalArgumentException ("parameter value must be finite");
        if (!Double.isFinite (tolerance) || tolerance < 0)
            throw new IllegalArgumentException ("parameter tolerance must be finite and non-negative");
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
