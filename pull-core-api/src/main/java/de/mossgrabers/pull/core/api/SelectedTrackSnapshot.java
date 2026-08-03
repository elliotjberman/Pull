// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Objects;

/**
 * Authoritative state for the private selection-following track target.
 *
 * @param generation Monotonic identity generation
 * @param channelId Stable host channel identity, or empty while unavailable
 * @param name Current track name
 * @param position Absolute project position, or {@code -1} while unavailable
 * @param trackType Portable host track-type identifier, or empty while unknown
 * @param exists True when a selected target exists
 * @param group True when the target is a group track
 * @param groupExpanded True when the group target is expanded
 * @param canHoldNotes True when the target can contain notes
 * @param canHoldAudio True when the target can contain audio
 * @param activated Track activation state
 * @param recordArmed Record-arm state
 * @param monitorMode Monitoring mode
 * @param muted Mute state
 * @param soloed Solo state
 * @param mutedBySolo True when another track's solo state mutes this target
 * @param clipPlaying True when a launcher clip is playing on the target
 * @param volume Normalized volume in {@code [0, 1]}
 * @param pan Normalized pan in {@code [0, 1]}
 * @param color Track color
 */
public record SelectedTrackSnapshot (long generation, String channelId, String name, int position, String trackType, boolean exists, boolean group, boolean groupExpanded, boolean canHoldNotes, boolean canHoldAudio, boolean activated, boolean recordArmed, TrackMonitorMode monitorMode, boolean muted, boolean soloed, boolean mutedBySolo, boolean clipPlaying, double volume, double pan, RgbColor color)
{
    private static final SelectedTrackSnapshot EMPTY = new SelectedTrackSnapshot (0, "", "", -1, "", false, false, false, false, false, false, false, TrackMonitorMode.OFF, false, false, false, false, 0, 0, new RgbColor (0, 0, 0));


    /**
     * Validate selected-track state.
     */
    public SelectedTrackSnapshot
    {
        if (generation < 0)
            throw new IllegalArgumentException ("generation must not be negative");
        channelId = Objects.requireNonNull (channelId, "channelId");
        name = Objects.requireNonNull (name, "name");
        if (position < -1)
            throw new IllegalArgumentException ("position must be -1 or greater");
        trackType = Objects.requireNonNull (trackType, "trackType");
        monitorMode = Objects.requireNonNull (monitorMode, "monitorMode");
        color = Objects.requireNonNull (color, "color");
        requireNormalized (volume, "volume");
        requireNormalized (pan, "pan");
        if (exists && channelId.isBlank ())
            throw new IllegalArgumentException ("existing selected track must have a channel ID");
    }


    /**
     * Get unavailable selected-track state.
     *
     * @return Empty selected-track state
     */
    public static SelectedTrackSnapshot empty ()
    {
        return EMPTY;
    }


    private static void requireNormalized (final double value, final String name)
    {
        if (!Double.isFinite (value) || value < 0 || value > 1)
            throw new IllegalArgumentException (name + " must be finite and between 0 and 1");
    }
}
