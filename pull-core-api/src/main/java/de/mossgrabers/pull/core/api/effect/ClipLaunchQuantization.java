// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

/**
 * Host-independent clip-launch quantization choices.
 */
public enum ClipLaunchQuantization
{
    /** Use the host/session default launch quantization. */
    DEFAULT,

    /** Launch immediately. */
    IMMEDIATE,

    /** Quantize to eight bars. */
    EIGHT_BARS,

    /** Quantize to four bars. */
    FOUR_BARS,

    /** Quantize to two bars. */
    TWO_BARS,

    /** Quantize to one bar. */
    ONE_BAR,

    /** Quantize to a half note. */
    HALF_NOTE,

    /** Quantize to a quarter note. */
    QUARTER_NOTE,

    /** Quantize to an eighth note. */
    EIGHTH_NOTE,

    /** Quantize to a sixteenth note. */
    SIXTEENTH_NOTE
}
