// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/**
 * Complete replayable ownership of the permanent controller note input.
 *
 * @param active True when the note input must target the authoritative selected track
 * @param targetGeneration Selected-target generation that authorized the route
 * @param targetChannelId Stable selected-target channel identity that authorized the route
 */
public record DesiredNoteInputRoute (boolean active, long targetGeneration, String targetChannelId)
{
    private static final DesiredNoteInputRoute DISABLED = new DesiredNoteInputRoute (false, 0, "");


    /** Validate route ownership and target identity. */
    public DesiredNoteInputRoute
    {
        if (targetGeneration < 0)
            throw new IllegalArgumentException ("targetGeneration must not be negative");
        targetChannelId = Objects.requireNonNull (targetChannelId, "targetChannelId");
        if (active && (targetGeneration == 0 || targetChannelId.isBlank ()))
            throw new IllegalArgumentException ("An active note-input route requires a target identity");
        if (!active && (targetGeneration != 0 || !targetChannelId.isEmpty ()))
            throw new IllegalArgumentException ("A disabled note-input route must not retain a target identity");
    }


    /**
     * Target the permanent note input at one authoritative selected-track observation.
     *
     * @param targetGeneration Selected-target generation
     * @param targetChannelId Stable selected-target channel identity
     * @return Active selected-track routing
     */
    public static DesiredNoteInputRoute selectedTrack (final long targetGeneration, final String targetChannelId)
    {
        return new DesiredNoteInputRoute (true, targetGeneration, targetChannelId);
    }


    /**
     * Do not route the permanent note input through the controller-owned selected-track path.
     *
     * @return Disabled route
     */
    public static DesiredNoteInputRoute disabled ()
    {
        return DISABLED;
    }
}
