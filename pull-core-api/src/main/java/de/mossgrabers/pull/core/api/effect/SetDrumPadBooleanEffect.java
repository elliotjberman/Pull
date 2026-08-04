// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.DrumPadSnapshot;

import java.util.Objects;

/**
 * Request an absolute boolean state on a drum pad in an observed drum window.
 *
 * @param contextGeneration Required drum-context generation
 * @param targetChannelId Required selected-track identity
 * @param padIndex Zero-based pad index in the observed window
 * @param property Property to set
 * @param enabled Desired state
 */
public record SetDrumPadBooleanEffect (long contextGeneration, String targetChannelId, int padIndex, DrumPadBoolean property, boolean enabled) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public SetDrumPadBooleanEffect
    {
        requireTarget (contextGeneration, targetChannelId, padIndex);
        property = Objects.requireNonNull (property, "property");
    }


    private static void requireTarget (final long generation, final String channelId, final int index)
    {
        if (generation < 0)
            throw new IllegalArgumentException ("contextGeneration must not be negative");
        Objects.requireNonNull (channelId, "targetChannelId");
        if (channelId.isBlank ())
            throw new IllegalArgumentException ("targetChannelId must not be blank");
        if (index < 0 || index >= DrumPadSnapshot.CAPACITY)
            throw new IllegalArgumentException ("padIndex must be between 0 and " + (DrumPadSnapshot.CAPACITY - 1));
    }
}
