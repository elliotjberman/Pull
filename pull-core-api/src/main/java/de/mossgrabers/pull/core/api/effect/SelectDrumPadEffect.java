// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.DrumPadSnapshot;

import java.util.Objects;

/**
 * Select one drum pad in an observed drum window.
 *
 * @param contextGeneration Required drum-context generation
 * @param targetChannelId Required selected-track identity
 * @param padIndex Zero-based pad index in the observed window
 */
public record SelectDrumPadEffect (long contextGeneration, String targetChannelId, int padIndex) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public SelectDrumPadEffect
    {
        if (contextGeneration < 0)
            throw new IllegalArgumentException ("contextGeneration must not be negative");
        targetChannelId = Objects.requireNonNull (targetChannelId, "targetChannelId");
        if (targetChannelId.isBlank ())
            throw new IllegalArgumentException ("targetChannelId must not be blank");
        if (padIndex < 0 || padIndex >= DrumPadSnapshot.CAPACITY)
            throw new IllegalArgumentException ("padIndex must be between 0 and " + (DrumPadSnapshot.CAPACITY - 1));
    }
}
