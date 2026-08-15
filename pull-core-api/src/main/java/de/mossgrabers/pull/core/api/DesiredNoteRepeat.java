// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/** Complete replayable ownership and live state of the stable note-repeat engine. */
public record DesiredNoteRepeat (boolean owned, boolean active, NoteRepeatMode mode, int octaves, double period, double noteLength, boolean latchActive, boolean freeRunning, boolean usePressure, boolean shuffle)
{
    private static final DesiredNoteRepeat UNOWNED = new DesiredNoteRepeat (false, false, NoteRepeatMode.ALL, 0, 0.25, 0.5, false, false, false, false);


    /** Validate the desired engine state. */
    public DesiredNoteRepeat
    {
        mode = Objects.requireNonNull (mode, "mode");
        if (octaves < 0 || octaves > 8)
            throw new IllegalArgumentException ("octaves must be between 0 and 8");
        requirePositiveFinite (period, "period");
        requirePositiveFinite (noteLength, "noteLength");
    }


    /** Leave note repeat under its established stable configuration. */
    public static DesiredNoteRepeat unowned ()
    {
        return UNOWNED;
    }


    private static void requirePositiveFinite (final double value, final String name)
    {
        if (!Double.isFinite (value) || value <= 0)
            throw new IllegalArgumentException (name + " must be positive and finite");
    }
}
