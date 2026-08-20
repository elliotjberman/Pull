// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.SessionBankShape;

import java.util.Objects;


/** Stop one exact track in the active bounded Session bank. */
public record StopSessionTrackEffect (long targetGeneration, SessionBankShape shape, int trackIndex, String channelId, boolean alternative) implements CoreEffect
{
    /** Validate the requested bank and track identity. */
    public StopSessionTrackEffect
    {
        if (targetGeneration < 0)
            throw new IllegalArgumentException ("targetGeneration must not be negative");
        shape = Objects.requireNonNull (shape, "shape");
        if (!shape.isPresent ())
            throw new IllegalArgumentException ("Session track stop requires a visible bank");
        if (trackIndex < 0 || trackIndex >= shape.tracks ())
            throw new IllegalArgumentException ("trackIndex must address the requested Session bank");
        channelId = Objects.requireNonNull (channelId, "channelId");
        if (channelId.isBlank ())
            throw new IllegalArgumentException ("Session track stop requires a channel ID");
    }
}
