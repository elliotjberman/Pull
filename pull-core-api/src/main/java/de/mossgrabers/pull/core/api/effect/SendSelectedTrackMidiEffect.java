// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/**
 * Send one raw MIDI channel message through the permanent private selected-track note route.
 *
 * @param targetGeneration Required selected-track generation
 * @param channelId Required stable channel identity
 * @param status MIDI CC, channel-pressure, or pitch-bend status byte
 * @param data1 First 7-bit MIDI data byte
 * @param data2 Second 7-bit MIDI data byte
 */
public record SendSelectedTrackMidiEffect (long targetGeneration, String channelId, int status, int data1, int data2) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public SendSelectedTrackMidiEffect
    {
        if (targetGeneration < 0)
            throw new IllegalArgumentException ("targetGeneration must not be negative");
        channelId = Objects.requireNonNull (channelId, "channelId");
        if (channelId.isBlank ())
            throw new IllegalArgumentException ("channelId must not be blank");
        final int command = status & 0xF0;
        if (status < 0x80 || status > 0xEF || command != 0xB0 && command != 0xD0 && command != 0xE0)
            throw new IllegalArgumentException ("status must be MIDI CC, channel pressure, or pitch bend");
        requireMidi7Bit (data1, "data1");
        requireMidi7Bit (data2, "data2");
    }


    private static void requireMidi7Bit (final int value, final String name)
    {
        if (value < 0 || value > 127)
            throw new IllegalArgumentException (name + " must be between 0 and 127");
    }
}
