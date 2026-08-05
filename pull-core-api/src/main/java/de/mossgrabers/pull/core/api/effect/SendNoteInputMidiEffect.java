// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

/**
 * Send one stateful raw MIDI channel message through the permanent controller note input.
 *
 * <p>Bitwig's ordinary input, monitoring, and record-arm routing decides which tracks receive the
 * message. This effect does not imply a selected-track destination.</p>
 *
 * @param status MIDI poly-pressure, CC, channel-pressure, or pitch-bend status byte
 * @param data1 First 7-bit MIDI data byte
 * @param data2 Second 7-bit MIDI data byte
 */
public record SendNoteInputMidiEffect (int status, int data1, int data2) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public SendNoteInputMidiEffect
    {
        final int command = status & 0xF0;
        if (status < 0x80 || status > 0xEF || command != 0xA0 && command != 0xB0 && command != 0xD0 && command != 0xE0)
            throw new IllegalArgumentException ("status must be MIDI poly pressure, CC, channel pressure, or pitch bend");
        requireMidi7Bit (data1, "data1");
        requireMidi7Bit (data2, "data2");
    }


    private static void requireMidi7Bit (final int value, final String name)
    {
        if (value < 0 || value > 127)
            throw new IllegalArgumentException (name + " must be between 0 and 127");
    }
}
