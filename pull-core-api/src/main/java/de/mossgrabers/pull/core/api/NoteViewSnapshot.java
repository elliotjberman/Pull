// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/**
 * Per-track note-view preference read through the private selection-following target.
 *
 * @param targetGeneration Selected-target generation that fences the preference
 * @param targetChannelId Stable selected-track identity
 * @param trackPosition Selected track's absolute position
 * @param preferredView Explicit stored preference, or {@link ControllerNoteView#NONE}
 * @param drumControllerApplicable Whether the bounded selected/model drum targets are aligned
 * @param drumSearchLimited Whether aligned drum discovery exhausted a structural or machine window
 * @param resolvedTarget Observed musical child, or empty for ordinary selected-track operation
 * @param groupScan Selected group child window and shared scanner read-back
 */
public record NoteViewSnapshot (long targetGeneration, String targetChannelId, int trackPosition, ControllerNoteView preferredView, boolean drumControllerApplicable, boolean drumSearchLimited, SelectedTrackSnapshot resolvedTarget, GroupDrumScanSnapshot groupScan)
{
    public NoteViewSnapshot (long targetGeneration, String targetChannelId, int trackPosition, ControllerNoteView preferredView, boolean drumControllerApplicable, boolean drumSearchLimited)
    {
        this (targetGeneration, targetChannelId, trackPosition, preferredView, drumControllerApplicable, drumSearchLimited, SelectedTrackSnapshot.empty (), GroupDrumScanSnapshot.empty ());
    }

    public NoteViewSnapshot (final long targetGeneration, final String targetChannelId, final int trackPosition, final ControllerNoteView preferredView, final boolean drumControllerApplicable)
    {
        this (targetGeneration, targetChannelId, trackPosition, preferredView, drumControllerApplicable, false);
    }

    private static final NoteViewSnapshot EMPTY = new NoteViewSnapshot (0, "", -1, ControllerNoteView.NONE, false);


    /** Validate the target-fenced preference. */
    public NoteViewSnapshot
    {
        if (targetGeneration < 0)
            throw new IllegalArgumentException ("targetGeneration must not be negative");
        targetChannelId = Objects.requireNonNull (targetChannelId, "targetChannelId");
        if (trackPosition < -1)
            throw new IllegalArgumentException ("trackPosition must be -1 or greater");
        resolvedTarget = Objects.requireNonNull (resolvedTarget);
        groupScan = Objects.requireNonNull (groupScan);
        preferredView = Objects.requireNonNull (preferredView, "preferredView");
    }


    /** Get unavailable preference state. */
    public static NoteViewSnapshot empty ()
    {
        return EMPTY;
    }
}
