// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Objects;
import java.util.Optional;


/**
 * Authoritative selected launcher-clip range and playback state.
 *
 * @param target Exact target while the cursor clip and private selection agree
 * @param loopStart Loop start in quarter-note beats
 * @param loopLength Loop length in quarter-note beats
 * @param selectableEnd Exclusive end of the observed selectable clip extent in quarter-note beats
 * @param playing True while the represented launcher clip is authoritatively playing
 * @param color Bitwig clip color
 */
public record ClipTimelineSnapshot (Optional<ClipTimelineTarget> target, double loopStart, double loopLength, double selectableEnd, boolean playing, RgbColor color)
{
    private static final ClipTimelineSnapshot EMPTY = new ClipTimelineSnapshot (Optional.empty (), 0, 0, 0, false, new RgbColor (0, 0, 0));


    /** Validate immutable clip state. */
    public ClipTimelineSnapshot
    {
        target = Objects.requireNonNull (target, "target");
        if (!Double.isFinite (loopStart))
            throw new IllegalArgumentException ("loopStart must be finite");
        requireFiniteNonNegative (loopLength, "loopLength");
        requireFiniteNonNegative (selectableEnd, "selectableEnd");
        color = Objects.requireNonNull (color, "color");
        if (target.isPresent ())
        {
            if (loopLength <= 0 || selectableEnd <= 0)
                throw new IllegalArgumentException ("available clip timeline must have positive lengths");
            final double loopEnd = loopStart + loopLength;
            if (!Double.isFinite (loopEnd) || selectableEnd < loopEnd)
                throw new IllegalArgumentException ("selectableEnd must contain the loop range");
        }
        else if (playing)
            throw new IllegalArgumentException ("unavailable clip timeline cannot be playing");
    }


    /** Get unavailable clip-timeline state. */
    public static ClipTimelineSnapshot empty ()
    {
        return EMPTY;
    }


    /** Test whether an exact selected clip is available. */
    public boolean available ()
    {
        return this.target.isPresent ();
    }


    private static void requireFiniteNonNegative (final double value, final String name)
    {
        if (!Double.isFinite (value) || value < 0)
            throw new IllegalArgumentException (name + " must be finite and not negative");
    }
}
