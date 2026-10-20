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
 * @param baseMidiNote First MIDI note in the primary window
 * @param pads Primary pads in deterministic window order, up to 64
 * @param machines Distinct observed machine windows in tree order, up to eight
 * @param searchLimited Whether a machine or structural search window overflowed
 */
public record DrumContextSnapshot (long generation, long targetGeneration, String targetChannelId, String deviceId, boolean available, boolean modelAligned, int baseMidiNote, List<DrumPadSnapshot> pads, List<DrumMachineSnapshot> machines, boolean searchLimited)
{
    /** Maximum distinct machines in the selected track's composite kit. */
    public static final int MAX_MACHINES = 8;

    /** A single-device context, also used by consumers that do not need tree composition. */
    public DrumContextSnapshot (final long generation, final long targetGeneration, final String targetChannelId, final String deviceId, final boolean available, final boolean modelAligned, final int baseMidiNote, final List<DrumPadSnapshot> pads)
    {
        this (generation, targetGeneration, targetChannelId, deviceId, available, modelAligned, baseMidiNote, pads,
            available ? List.of (new DrumMachineSnapshot (deviceId, baseMidiNote, pads)) : List.of (), false);
    }

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

        machines = List.copyOf (machines);
        if (machines.size () > MAX_MACHINES || machines.stream ().map (DrumMachineSnapshot::deviceId).distinct ().count () != machines.size ())
            throw new IllegalArgumentException ("Drum context requires at most eight distinct machines");
        if (available && (machines.isEmpty () || !machines.get (0).equals (new DrumMachineSnapshot (deviceId, baseMidiNote, pads))))
            throw new IllegalArgumentException ("Primary drum window must match the first machine");

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
