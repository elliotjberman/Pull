// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Bounded selected-track drum context.
 *
 * @param generation Monotonic drum-window generation
 * @param targetGeneration Selected-track generation captured with this window
 * @param targetChannelId Stable selected-track identity, or empty while unavailable
 * @param deviceId Opaque stable drum-device identity, or empty while unavailable
 * @param available True when a compatible drum target exists
 * @param modelAligned True when the rendering model and private selected target have the same ID
 * @param baseMidiNote First MIDI note in the 64-pad window
 * @param pads Pads in deterministic window order, up to 64
 */
public record DrumContextSnapshot (long generation, long targetGeneration, String targetChannelId, String deviceId, boolean available, boolean modelAligned, int baseMidiNote, List<DrumPadSnapshot> pads)
{
    private static final DrumContextSnapshot EMPTY = new DrumContextSnapshot (0, 0, "", "", false, false, 0, List.of ());


    /**
     * Validate and copy drum-context state.
     */
    public DrumContextSnapshot
    {
        if (generation < 0)
            throw new IllegalArgumentException ("generation must not be negative");
        if (targetGeneration < 0)
            throw new IllegalArgumentException ("targetGeneration must not be negative");
        targetChannelId = Objects.requireNonNull (targetChannelId, "targetChannelId");
        deviceId = Objects.requireNonNull (deviceId, "deviceId");
        if (baseMidiNote < 0 || baseMidiNote > 127)
            throw new IllegalArgumentException ("baseMidiNote must be between 0 and 127");
        pads = List.copyOf (Objects.requireNonNull (pads, "pads"));
        if (pads.size () > DrumPadSnapshot.CAPACITY)
            throw new IllegalArgumentException ("drum context cannot exceed " + DrumPadSnapshot.CAPACITY + " pads");
        if (available && targetChannelId.isBlank ())
            throw new IllegalArgumentException ("available drum context must have a target channel ID");
        if (available && deviceId.isBlank ())
            throw new IllegalArgumentException ("available drum context must have a device ID");

        final Set<Integer> indices = new HashSet<> ();
        final Set<Integer> notes = new HashSet<> ();
        for (final DrumPadSnapshot pad: pads)
        {
            if (!indices.add (Integer.valueOf (pad.padIndex ())))
                throw new IllegalArgumentException ("drum pads must have unique indices");
            if (!notes.add (Integer.valueOf (pad.midiNote ())))
                throw new IllegalArgumentException ("drum pads must have unique MIDI notes");
        }
    }


    /**
     * Get unavailable drum state.
     *
     * @return Empty drum state
     */
    public static DrumContextSnapshot empty ()
    {
        return EMPTY;
    }
}
