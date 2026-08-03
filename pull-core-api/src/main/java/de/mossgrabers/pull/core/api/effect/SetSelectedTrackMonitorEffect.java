// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.TrackMonitorMode;

import java.util.Objects;

/**
 * Request an absolute monitoring mode on the selected target observed in a snapshot.
 *
 * @param targetGeneration Required selected-track generation
 * @param channelId Required stable channel identity
 * @param mode Desired monitoring mode
 */
public record SetSelectedTrackMonitorEffect (long targetGeneration, String channelId, TrackMonitorMode mode) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public SetSelectedTrackMonitorEffect
    {
        requireTarget (targetGeneration, channelId);
        mode = Objects.requireNonNull (mode, "mode");
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
