// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell;

import com.bitwig.extension.controller.api.NoteStep;

/** Raw host note observations in the existing opt-in, bounded selection trace. */
public final class NoteStepDebug
{
    private NoteStepDebug ()
    {
        // Utility class.
    }

    /** Record note starts only; initial empty-grid callbacks must not overwhelm the trace. */
    public static void recordObserved (final String role, final String trackId, final int sceneIndex, final NoteStep step)
    {
        recordObserved (role, trackId, sceneIndex, step, false);
    }

    /** Explicit probes may include an empty/sustaining destination to establish later host state. */
    public static void recordObserved (final String role, final String trackId, final int sceneIndex, final NoteStep step, final boolean includeNonStarts)
    {
        if (!SelectionDebug.recording () || step == null || !includeNonStarts && step.state () != NoteStep.State.NoteOn)
            return;
        // Read on the controller thread now: NoteStep itself is a mutable host proxy.
        SelectionDebug.record ("NOTE_OBSERVED", "role=" + PushDebugging.sanitize (role) + " track=" + PushDebugging.sanitize (trackId) + " scene=" + sceneIndex
            + " channel=" + step.channel () + " x=" + step.x () + " y=" + step.y () + " state=" + step.state ()
            + " selected=" + step.isIsSelected () + " muted=" + step.isMuted ()
            + " duration=" + step.duration () + " velocity=" + step.velocity () + " velocitySpread=" + step.velocitySpread ()
            + " releaseVelocity=" + step.releaseVelocity () + " pressure=" + step.pressure () + " timbre=" + step.timbre ()
            + " pan=" + step.pan () + " transpose=" + step.transpose () + " gain=" + step.gain ()
            + " chanceEnabled=" + step.isChanceEnabled () + " chance=" + step.chance ()
            + " occurrenceEnabled=" + step.isOccurrenceEnabled () + " occurrence=" + step.occurrence ()
            + " recurrenceEnabled=" + step.isRecurrenceEnabled () + " recurrenceLength=" + step.recurrenceLength () + " recurrenceMask=" + step.recurrenceMask ()
            + " repeatEnabled=" + step.isRepeatEnabled () + " repeatCount=" + step.repeatCount () + " repeatCurve=" + step.repeatCurve ()
            + " repeatVelocityCurve=" + step.repeatVelocityCurve () + " repeatVelocityEnd=" + step.repeatVelocityEnd ());
    }
}
