// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Objects;

/**
 * One pad in the stable 64-pad selected-drum window.
 *
 * @param padIndex Zero-based index in the current drum window
 * @param midiNote Absolute MIDI note represented by the pad
 * @param channelId Opaque stable pad-channel identity, or empty while unavailable
 * @param exists True when the pad exists
 * @param name Current pad name
 * @param color Pad color
 * @param activated Pad-chain activation state
 * @param hasDevices True when the pad contains a device chain
 * @param selected Selection state
 * @param muted Mute state
 * @param soloed Solo state
 * @param volume Normalized pad volume in {@code [0, 1]}
 * @param pan Normalized pad pan in {@code [0, 1]}
 * @param playingVelocity Authoritative playing velocity, or zero while idle
 */
public record DrumPadSnapshot (int padIndex, int midiNote, String channelId, boolean exists, String name, RgbColor color, boolean activated, boolean hasDevices, boolean selected, boolean muted, boolean soloed, double volume, double pan, int playingVelocity)
{
    /** Maximum number of pads in the stable drum window. */
    public static final int CAPACITY = 64;


    /**
     * Validate drum-pad state.
     */
    public DrumPadSnapshot
    {
        if (padIndex < 0 || padIndex >= CAPACITY)
            throw new IllegalArgumentException ("padIndex must be between 0 and " + (CAPACITY - 1));
        requireMidi7Bit (midiNote, "midiNote");
        channelId = Objects.requireNonNull (channelId, "channelId");
        name = Objects.requireNonNull (name, "name");
        color = Objects.requireNonNull (color, "color");
        requireNormalized (volume, "volume");
        requireNormalized (pan, "pan");
        requireMidi7Bit (playingVelocity, "playingVelocity");
    }


    /**
     * Create an unavailable pad at a known window position.
     *
     * @param padIndex Window index
     * @param midiNote MIDI note
     * @return Empty pad state
     */
    public static DrumPadSnapshot empty (final int padIndex, final int midiNote)
    {
        return new DrumPadSnapshot (padIndex, midiNote, "", false, "", new RgbColor (0, 0, 0), false, false, false, false, false, 0, 0, 0);
    }


    private static void requireMidi7Bit (final int value, final String name)
    {
        if (value < 0 || value > 127)
            throw new IllegalArgumentException (name + " must be between 0 and 127");
    }


    private static void requireNormalized (final double value, final String name)
    {
        if (!Double.isFinite (value) || value < 0 || value > 1)
            throw new IllegalArgumentException (name + " must be finite and between 0 and 1");
    }
}
