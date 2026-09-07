// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.daw.midi;

/**
 * MIDI constants.
 *
 * @author Jürgen Moßgraber
 */
public class MidiConstants
{
    /** Note off. */
    public static final int        CMD_NOTE_OFF           = 0x80;
    /** Note on. */
    public static final int        CMD_NOTE_ON            = 0x90;
    /** Polyphonic Aftertouch. */
    public static final int        CMD_POLY_AFTERTOUCH    = 0xA0;
    /** Continuous Control. */
    public static final int        CMD_CC                 = 0xB0;
    /** Program Change. */
    public static final int        CMD_PROGRAM_CHANGE     = 0xC0;
    /** Channel Aftertouch. */
    public static final int        CMD_CHANNEL_AFTERTOUCH = 0xD0;
    /** Pitchbend. */
    public static final int        CMD_PITCHBEND          = 0xE0;
    /** System. */
    public static final int        CMD_SYSTEM             = 0xF0;

    /**
     * Due to constant class.
     */
    private MidiConstants ()
    {
        // Intentionally empty
    }
}
