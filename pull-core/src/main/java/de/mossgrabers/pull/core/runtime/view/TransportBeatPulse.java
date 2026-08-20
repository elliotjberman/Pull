// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.output.ControllerLight;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Objects;


/** Shared Session-style playback pulse for a transport-following light. */
final class TransportBeatPulse
{
    private TransportBeatPulse ()
    {
        // Utility class.
    }


    /**
     * Use the same slow tempo-clocked alternate-color output as a playing Session clip. Keeping
     * the blink semantic in the light output lets the hardware and debugger expose and animate the
     * pulse instead of receiving an opaque succession of steady colors.
     *
     * @param transport Authoritative transport snapshot
     * @param beatPosition Authoritative or boundedly reconstructed beat position
     * @param resting Resting color
     * @param pulse Pulse color
     * @return Steady light for the current beat phase
     */
    static ControllerLight light (final TransportSnapshot transport, final double beatPosition, final RgbColor resting, final RgbColor pulse)
    {
        Objects.requireNonNull (transport, "transport");
        final RgbColor restingColor = Objects.requireNonNull (resting, "resting");
        final RgbColor pulseColor = Objects.requireNonNull (pulse, "pulse");
        if (!transport.available () || !transport.playing () || !Double.isFinite (beatPosition) || beatPosition < 0)
            return ControllerLight.steady (restingColor);

        return ControllerLight.playing (restingColor, pulseColor);
    }
}
