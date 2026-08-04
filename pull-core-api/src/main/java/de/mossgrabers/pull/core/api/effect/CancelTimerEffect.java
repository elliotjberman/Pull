// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.TimerId;

import java.util.Objects;

/**
 * Cancel a logical timer.
 *
 * @param timerId The timer identifier
 */
public record CancelTimerEffect (TimerId timerId) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public CancelTimerEffect
    {
        timerId = Objects.requireNonNull (timerId, "timerId");
    }
}
