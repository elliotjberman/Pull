// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Objects;


/** Authoritative state for one track in the active bounded Session bank. */
public record SessionTrackSnapshot (String channelId, int position, String name, boolean exists, boolean selected, boolean activated, boolean recordArmed, boolean muted, boolean soloed, boolean clipPlaying, SessionTrackType type, RgbColor color)
{
    /** Validate and normalize one visible track. */
    public SessionTrackSnapshot
    {
        channelId = Objects.requireNonNull (channelId, "channelId");
        name = Objects.requireNonNullElse (name, "");
        if (name.length () > 128)
            throw new IllegalArgumentException ("Session track name must not exceed 128 UTF-16 code units");
        type = Objects.requireNonNull (type, "type");
        color = Objects.requireNonNull (color, "color");
        if (position < -1)
            throw new IllegalArgumentException ("position must be -1 or greater");
        if (exists && channelId.isBlank ())
            throw new IllegalArgumentException ("an existing Session track must have a channel ID");
        if (exists && position < 0)
            throw new IllegalArgumentException ("an existing Session track must have a position");
        if (!exists && (!channelId.isEmpty () || position != -1 || !name.isEmpty () || selected || activated || recordArmed || muted || soloed || clipPlaying || type != SessionTrackType.UNKNOWN))
            throw new IllegalArgumentException ("an unavailable Session track cannot contain state");
    }


    /** Compatibility constructor for snapshots without a semantic track type. */
    public SessionTrackSnapshot (final String channelId, final int position, final String name, final boolean exists, final boolean selected, final boolean activated, final boolean recordArmed, final boolean muted, final boolean soloed, final boolean clipPlaying, final RgbColor color)
    {
        this (channelId, position, name, exists, selected, activated, recordArmed, muted, soloed, clipPlaying, SessionTrackType.UNKNOWN, color);
    }


    /** Compatibility constructor for snapshots without a track name. */
    public SessionTrackSnapshot (final String channelId, final int position, final boolean exists, final boolean selected, final boolean activated, final boolean recordArmed, final boolean muted, final boolean soloed, final boolean clipPlaying, final RgbColor color)
    {
        this (channelId, position, "", exists, selected, activated, recordArmed, muted, soloed, clipPlaying, SessionTrackType.UNKNOWN, color);
    }


    /** Create one unavailable bank slot. */
    public static SessionTrackSnapshot empty ()
    {
        return new SessionTrackSnapshot ("", -1, "", false, false, false, false, false, false, false, SessionTrackType.UNKNOWN, new RgbColor (0, 0, 0));
    }
}
