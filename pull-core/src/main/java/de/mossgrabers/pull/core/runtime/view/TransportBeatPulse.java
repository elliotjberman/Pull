// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.output.ControllerLight;
import de.mossgrabers.pull.core.api.output.MusicalLightPulse;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Objects;


/** Shared exact musical pulse for a transport-following light. */
final class TransportBeatPulse
{
    private TransportBeatPulse ()
    {
        // Utility class.
    }


    /**
     * Derive one complete primary/alternate cycle from the requested musical duration. The core
     * supplies the current phase for hardware and a transport offset for clocked debug renderers.
     *
     * @param transport Authoritative transport snapshot
     * @param beatPosition Authoritative or boundedly reconstructed beat position
     * @param cycleBeats Length of one complete pulse cycle in quarter-note beats
     * @param resting Resting color
     * @param pulse Pulse color
     * @return Steady light for the current beat phase
     */
    static ControllerLight light (final TransportSnapshot transport, final double beatPosition, final double cycleBeats, final RgbColor resting, final RgbColor pulse)
    {
        Objects.requireNonNull (transport, "transport");
        final RgbColor restingColor = Objects.requireNonNull (resting, "resting");
        final RgbColor pulseColor = Objects.requireNonNull (pulse, "pulse");
        if (!transport.available () || !transport.playing () || !Double.isFinite (beatPosition) || beatPosition < 0 || !Double.isFinite (cycleBeats) || cycleBeats <= 0)
            return ControllerLight.steady (restingColor);

        final double alternatePhaseStart = cycleBeats / 2;
        final double phase = beatPosition % cycleBeats;
        return ControllerLight.musical (
            restingColor,
            pulseColor,
            new MusicalLightPulse (cycleBeats, alternatePhaseStart, transport.positionBeats () - beatPosition, phase >= alternatePhaseStart));
    }
}
