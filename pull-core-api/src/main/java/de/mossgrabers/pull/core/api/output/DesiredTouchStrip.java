// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;

import java.util.Objects;


/**
 * Complete replayable output for one installed touch strip. This is hardware output only: it does
 * not route musical MIDI or prove that a host parameter received a requested value.
 *
 * @param owned Whether core owns the strip instead of its frozen stable output
 * @param mode Hardware display mode
 * @param value Complete 14-bit hardware position, from 0 through 16383
 */
public record DesiredTouchStrip (boolean owned, TouchStripMode mode, int value)
{
    private static final DesiredTouchStrip UNOWNED = new DesiredTouchStrip (false, TouchStripMode.OFF, 0);
    private static final DesiredTouchStrip OFF = new DesiredTouchStrip (true, TouchStripMode.OFF, 0);


    /** Validate the bounded physical output. */
    public DesiredTouchStrip
    {
        mode = Objects.requireNonNull (mode, "mode");
        if (value < 0 || value > 16383)
            throw new IllegalArgumentException ("touch-strip value must be between 0 and 16383");
        if (!owned && (mode != TouchStripMode.OFF || value != 0))
            throw new IllegalArgumentException ("unowned touch-strip output must be empty");
        if (mode == TouchStripMode.OFF && value != 0)
            throw new IllegalArgumentException ("off touch-strip output must have zero position");
    }


    /** Leave the frozen stable output in charge. */
    public static DesiredTouchStrip unowned ()
    {
        return UNOWNED;
    }


    /** Retain ownership with the strip dark; this does not enable stable fallback. */
    public static DesiredTouchStrip off ()
    {
        return OFF;
    }


    /** Display a complete 14-bit position in pitch-bend mode. */
    public static DesiredTouchStrip pitchBend (final int value)
    {
        return new DesiredTouchStrip (true, TouchStripMode.PITCH_BEND, value);
    }
}
