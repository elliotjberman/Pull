// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** One distinct device and its observed MIDI-addressed window; no merged parameter values. */
public record DrumMachineSnapshot (String deviceId, int baseMidiNote, List<DrumPadSnapshot> pads)
{
    public DrumMachineSnapshot
    {
        deviceId = Objects.requireNonNull (deviceId, "deviceId");
        pads = List.copyOf (pads);
        if (deviceId.isBlank () || baseMidiNote < 0 || baseMidiNote > 127 || pads.size () > DrumPadSnapshot.CAPACITY)
            throw new IllegalArgumentException ("Drum machine requires an identity and a bounded MIDI window");
        final var indices = new HashSet<Integer> ();
        for (final DrumPadSnapshot pad : pads)
            if (pad.midiNote () != baseMidiNote + pad.padIndex () || !indices.add (pad.padIndex ()))
                throw new IllegalArgumentException ("Drum pads must have distinct, aligned MIDI addresses");
    }
}
