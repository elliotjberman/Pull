// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/**
 * Request an absolute normalized value on the selected target observed in a snapshot.
 *
 * @param targetGeneration Required selected-track generation
 * @param channelId Required stable channel identity
 * @param value Value to set
 * @param normalizedValue Desired value in {@code [0, 1]}
 */
public record SetSelectedTrackValueEffect (long targetGeneration, String channelId, SelectedTrackValue value, double normalizedValue) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public SetSelectedTrackValueEffect
    {
        requireTarget (targetGeneration, channelId);
        value = Objects.requireNonNull (value, "value");
        if (!Double.isFinite (normalizedValue) || normalizedValue < 0 || normalizedValue > 1)
            throw new IllegalArgumentException ("normalizedValue must be finite and between 0 and 1");
    }


    private static void requireTarget (final long generation, final String id)
    {
        if (generation < 0)
            throw new IllegalArgumentException ("targetGeneration must not be negative");
        Objects.requireNonNull (id, "channelId");
        if (id.isBlank ())
            throw new IllegalArgumentException ("channelId must not be blank");
    }
}
