// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Objects;


/**
 * Observed launcher slot state. Scene position identifies a location, not a permanent clip ID.
 * Playback and recording flags are host read-back, never confirmation of submitted commands.
 */
public record SessionSlotSnapshot (boolean exists, int scenePosition, String name, boolean hasContent, boolean selected, boolean muted, boolean playing, boolean recording, boolean playbackQueued, boolean recordingQueued, boolean stopQueued, RgbColor color)
{
    public SessionSlotSnapshot
    {
        name = Objects.requireNonNullElse (name, "");
        color = Objects.requireNonNull (color, "color");
        if (name.length () > 128 || scenePosition < -1 || exists && scenePosition < 0)
            throw new IllegalArgumentException ("Invalid Session slot metadata");
        if (!exists && (scenePosition != -1 || !name.isEmpty () || hasContent || selected || muted || playing || recording || playbackQueued || recordingQueued || stopQueued))
            throw new IllegalArgumentException ("An unavailable Session slot cannot contain state");
    }


    public static SessionSlotSnapshot empty ()
    {
        return new SessionSlotSnapshot (false, -1, "", false, false, false, false, false, false, false, false, new RgbColor (0, 0, 0));
    }
}
