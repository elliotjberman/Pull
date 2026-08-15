// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/** Authoritative live note-repeat read-back plus the drum-roll user setting. */
public record NoteRepeatSnapshot (boolean available, boolean drumRollEnabled, boolean active, NoteRepeatMode mode, int octaves, double period, double noteLength, boolean latchActive, boolean freeRunning, boolean usePressure, boolean shuffle)
{
    private static final NoteRepeatSnapshot EMPTY = new NoteRepeatSnapshot (false, false, false, NoteRepeatMode.ALL, 0, 0.25, 0.5, false, false, false, false);


    /** Validate the read-back. */
    public NoteRepeatSnapshot
    {
        mode = Objects.requireNonNull (mode, "mode");
        if (octaves < 0 || octaves > 8)
            throw new IllegalArgumentException ("octaves must be between 0 and 8");
        if (!Double.isFinite (period) || period <= 0)
            throw new IllegalArgumentException ("period must be positive and finite");
        if (!Double.isFinite (noteLength) || noteLength <= 0)
            throw new IllegalArgumentException ("noteLength must be positive and finite");
    }


    /** Get unavailable read-back. */
    public static NoteRepeatSnapshot empty ()
    {
        return EMPTY;
    }


    /** Test whether live read-back acknowledges a desired owned state. */
    public boolean matches (final DesiredNoteRepeat desired)
    {
        final DesiredNoteRepeat checked = Objects.requireNonNull (desired, "desired");
        return checked.owned () && this.available && this.active == checked.active () && this.mode == checked.mode () && this.octaves == checked.octaves () && close (this.period, checked.period ()) && close (this.noteLength, checked.noteLength ()) && this.latchActive == checked.latchActive () && this.freeRunning == checked.freeRunning () && this.usePressure == checked.usePressure () && this.shuffle == checked.shuffle ();
    }


    private static boolean close (final double left, final double right)
    {
        return Math.abs (left - right) < 0.000001;
    }
}
