// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/** Move one exact observed device's pad window, without changing the controller's musical layout. */
public record SetDrumWindowPositionEffect (long contextGeneration, String targetChannelId, String deviceId, int baseMidiNote) implements CoreEffect
{
    public SetDrumWindowPositionEffect
    {
        Objects.requireNonNull (targetChannelId, "targetChannelId");
        Objects.requireNonNull (deviceId, "deviceId");
        if (contextGeneration <= 0 || targetChannelId.isBlank () || deviceId.isBlank () || baseMidiNote < 0 || baseMidiNote > 127)
            throw new IllegalArgumentException ("Drum window requires a current exact target and MIDI-range position");
    }
}
