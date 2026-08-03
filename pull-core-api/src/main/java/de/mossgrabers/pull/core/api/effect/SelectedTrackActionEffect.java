// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/**
 * Request a one-shot action on the selected target observed in a snapshot.
 *
 * @param targetGeneration Required selected-track generation
 * @param channelId Required stable channel identity
 * @param action Action to invoke
 */
public record SelectedTrackActionEffect (long targetGeneration, String channelId, SelectedTrackAction action) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public SelectedTrackActionEffect
    {
        if (targetGeneration < 0)
            throw new IllegalArgumentException ("targetGeneration must not be negative");
        channelId = Objects.requireNonNull (channelId, "channelId");
        if (channelId.isBlank ())
            throw new IllegalArgumentException ("channelId must not be blank");
        action = Objects.requireNonNull (action, "action");
    }
}
