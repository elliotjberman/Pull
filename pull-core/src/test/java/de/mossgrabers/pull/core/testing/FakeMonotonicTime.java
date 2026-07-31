// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import java.time.Duration;
import java.util.Objects;

/**
 * Manually advanced monotonic time for deterministic core tests.
 */
final class FakeMonotonicTime
{
    private long nowNanos;


    /**
     * Get the current fake monotonic time.
     *
     * @return Nanoseconds
     */
    long nowNanos ()
    {
        return this.nowNanos;
    }


    /**
     * Advance time without sleeping.
     *
     * @param duration The non-negative duration
     */
    void advance (final Duration duration)
    {
        Objects.requireNonNull (duration, "duration");
        if (duration.isNegative ())
            throw new IllegalArgumentException ("duration must not be negative");
        this.nowNanos = Math.addExact (this.nowNanos, duration.toNanos ());
    }
}
