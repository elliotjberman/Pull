// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;


/** Absolute position request for the exact observed drum device's bounded pad window. */
public record SetDrumBankPositionEffect (long contextGeneration, String targetChannelId, int baseMidiNote, boolean adjustPage) implements CoreEffect
{
    public SetDrumBankPositionEffect
    {
        targetChannelId = Objects.requireNonNull (targetChannelId, "targetChannelId");
        if (contextGeneration <= 0 || targetChannelId.isBlank () || baseMidiNote < 0 || baseMidiNote > 127)
            throw new IllegalArgumentException ("Drum bank position requires a current target and MIDI-range position");
    }
}
