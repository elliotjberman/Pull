// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.daw.midi;

import java.util.Objects;


/**
 * Bounded authoritative state of the private selection-following note target.
 *
 * @param generation Identity generation of the selected target
 * @param trackID Stable Bitwig channel ID, or an empty string when unresolved
 * @param exists True if the target exists
 * @param name Track name
 * @param colorRed Normalized red component
 * @param colorGreen Normalized green component
 * @param colorBlue Normalized blue component
 * @param trackType Bitwig track type identifier
 * @param position Absolute track position
 * @param canHoldNotes True if the track can hold note data
 * @param canHoldAudio True if the track can hold audio data
 * @param group True if the target is a group track
 * @param groupExpanded True if the group is expanded
 * @param activated True if the track is activated
 * @param armed True if the track is armed
 * @param monitorMode Track monitor mode
 * @param muted True if the track is muted
 * @param soloed True if the track is soloed
 * @param mutedBySolo True if another solo is muting the track
 * @param clipPlaying True if a launcher clip is playing
 * @param stopped True if launcher playback is stopped
 * @param volume Normalized track volume
 * @param pan Normalized track pan, with 0.5 at center
 */
public record SelectedTrackNoteTargetSnapshot (long generation, String trackID, boolean exists, String name, double colorRed, double colorGreen, double colorBlue, String trackType, int position, boolean canHoldNotes, boolean canHoldAudio, boolean group, boolean groupExpanded, boolean activated, boolean armed, SelectedTrackMonitorMode monitorMode, boolean muted, boolean soloed, boolean mutedBySolo, boolean clipPlaying, boolean stopped, double volume, double pan)
{
    /**
     * Validate snapshot values.
     */
    public SelectedTrackNoteTargetSnapshot
    {
        if (generation < 0)
            throw new IllegalArgumentException ("generation must not be negative");

        trackID = Objects.requireNonNull (trackID, "trackID");
        name = Objects.requireNonNull (name, "name");
        trackType = Objects.requireNonNull (trackType, "trackType");
        monitorMode = Objects.requireNonNull (monitorMode, "monitorMode");
        requireNormalized (colorRed, "colorRed");
        requireNormalized (colorGreen, "colorGreen");
        requireNormalized (colorBlue, "colorBlue");
        requireNormalized (volume, "volume");
        requireNormalized (pan, "pan");
        if (clipPlaying == stopped)
            throw new IllegalArgumentException ("clipPlaying and stopped must be complementary");
    }


    private static void requireNormalized (final double value, final String name)
    {
        if (!Double.isFinite (value) || value < 0.0 || value > 1.0)
            throw new IllegalArgumentException (name + " must be finite and normalized");
    }
}
