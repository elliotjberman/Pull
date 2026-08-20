// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.output.ControllerLight;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Objects;


/** Shared beat-phase pulse for lights whose physical owner can move during playback. */
final class TransportBeatPulse
{
    private static final double HALF_BEAT = 0.5;


    private TransportBeatPulse ()
    {
        // Utility class.
    }


    /**
     * Select a steady light for the current half-beat. Deriving the phase from one continuous beat
     * position keeps the pulse continuous when ownership moves to a different pad; assigning a
     * fresh firmware blink to each pad would restart its visible animation.
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

        final double beatPhase = beatPosition - Math.floor (beatPosition);
        return ControllerLight.steady (beatPhase < HALF_BEAT ? pulseColor : restingColor);
    }
}
