// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;


/**
 * Exact tempo-linked light phase realized from controller snapshots rather than a fixed firmware
 * blink rate.
 *
 * @param cycleBeats Length of one complete primary/alternate cycle in quarter-note beats
 * @param alternatePhaseStartBeats Beat within the cycle where the alternate color begins
 * @param transportOffsetBeats Transport position minus the semantic beat position that owns the
 *            pulse; this lets debug renderers reconstruct the same phase from the transport clock
 * @param alternatePhase Whether hardware should currently show the alternate color
 */
public record MusicalLightPulse (double cycleBeats, double alternatePhaseStartBeats, double transportOffsetBeats, boolean alternatePhase)
{
    /** Validate one complete musical pulse. */
    public MusicalLightPulse
    {
        if (!Double.isFinite (cycleBeats) || cycleBeats <= 0)
            throw new IllegalArgumentException ("Musical light cycle must be finite and positive");
        if (!Double.isFinite (alternatePhaseStartBeats) || alternatePhaseStartBeats <= 0 || alternatePhaseStartBeats >= cycleBeats)
            throw new IllegalArgumentException ("Musical light alternate phase must begin within the cycle");
        if (!Double.isFinite (transportOffsetBeats))
            throw new IllegalArgumentException ("Musical light transport offset must be finite");
    }
}
