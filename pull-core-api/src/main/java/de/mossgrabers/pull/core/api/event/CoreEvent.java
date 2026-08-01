// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.event;

/**
 * A normalized input delivered by the stable shell.
 */
public sealed interface CoreEvent permits AbsoluteInputEvent, ButtonInputEvent, SnapshotChangedEvent, TimerElapsedEvent, TouchInputEvent
{
    /**
     * Get the monotonic event sequence.
     *
     * @return The sequence
     */
    long sequence ();


    /**
     * Get the shell monotonic time at which the event occurred.
     *
     * @return The monotonic time in nanoseconds
     */
    long monotonicTimeNanos ();
}
