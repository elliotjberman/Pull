// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/**
 * Request an absolute boolean state on the selected target observed in a snapshot.
 *
 * @param targetGeneration Required selected-track generation
 * @param channelId Required stable channel identity
 * @param property Property to set
 * @param enabled Desired state
 */
public record SetSelectedTrackBooleanEffect (long targetGeneration, String channelId, SelectedTrackBoolean property, boolean enabled) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public SetSelectedTrackBooleanEffect
    {
        requireTarget (targetGeneration, channelId);
        property = Objects.requireNonNull (property, "property");
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
