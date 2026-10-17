// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.interaction;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static de.mossgrabers.pull.core.interaction.InteractionLifecycle.*;
import static org.junit.jupiter.api.Assertions.*;

class InteractionLifecycleTest
{
    private static final String KNOB = "knob-1";
    private static final String PAD = "pad-1";
    private static final String STRIP = "strip";
    private static final Target CUTOFF = new Target ("synth/cutoff", 1);
    private static final Target VOLUME = new Target ("track/volume", 1);
    private static final Target CLIP = new Target ("track/scene-1", 1);
    private record Target (String identity, long generation) {}

    @Test
    void cancellationRunsOnceAndSwallowsMotionUntilAFreshTouch ()
    {
        final var lifecycle = lifecycle (4);
        final var cutoff = started (lifecycle, KNOB, CUTOFF);

        lifecycle.cancel (KNOB);
        assertTrue (lifecycle.current (KNOB).isEmpty ());
        assertEquals (Admission.ALREADY_HELD, lifecycle.begin (KNOB, CUTOFF).admission ());
        final var finish = lifecycle.beginFinish (cutoff.id ()).orElseThrow ();
        assertEquals (new Finish<> (cutoff.id (), CUTOFF, EndReason.BINDING_CHANGED), finish);
        assertTrue (lifecycle.beginFinish (cutoff.id ()).isEmpty (), "no duplicate cleanup");
        assertTrue (lifecycle.hasTargetWork (CUTOFF), "submission is not cleanup completion");

        lifecycle.completeFinish (finish.interaction ());
        assertFalse (lifecycle.hasTargetWork (CUTOFF));
        assertFalse (lifecycle.isIdle (), "the held tail still belongs to the old gesture");
        lifecycle.release (KNOB);
        assertTrue (lifecycle.isIdle ());
        assertEquals (VOLUME, started (lifecycle, KNOB, VOLUME).target ());
    }

    @Test
    void physicalReleaseDoesNotCompleteCleanupOrAuthorizeEarlyReuse ()
    {
        final var lifecycle = lifecycle (4);
        final var edit = started (lifecycle, KNOB, CUTOFF);
        lifecycle.release (KNOB);
        assertFalse (lifecycle.completeFinish (edit.id ()), "cannot complete cleanup before it starts");
        assertFalse (lifecycle.isIdle ());
        assertEquals (Admission.TARGET_BUSY, lifecycle.begin (PAD, CUTOFF).admission ());

        assertEquals (EndReason.RELEASED, lifecycle.beginFinish (edit.id ()).orElseThrow ().reason ());
        assertEquals (Admission.TARGET_BUSY, lifecycle.begin (KNOB, CUTOFF).admission ());
        lifecycle.completeFinish (edit.id ());
        assertEquals (Admission.ALREADY_HELD, lifecycle.begin (PAD, CUTOFF).admission (), "a rejected press cannot retry itself");
        lifecycle.release (PAD);
        lifecycle.release (KNOB);
        assertEquals (CUTOFF, started (lifecycle, PAD, CUTOFF).target ());
    }

    @Test
    void lateOldCleanupCannotEndANewInteractionOnAnotherTarget ()
    {
        final var lifecycle = lifecycle (4);
        final var old = started (lifecycle, KNOB, CUTOFF);
        lifecycle.cancel (KNOB);
        lifecycle.release (KNOB);
        final var next = started (lifecycle, KNOB, VOLUME);
        lifecycle.beginFinish (old.id ()).orElseThrow ();
        lifecycle.completeFinish (old.id ());
        assertFalse (lifecycle.completeFinish (old.id ()));
        assertEquals (next, lifecycle.current (KNOB).orElseThrow ());
        assertTrue (lifecycle.hasTargetWork (VOLUME));
    }

    @Test
    void cancellingOneControlDoesNotCancelOtherControls ()
    {
        final var lifecycle = lifecycle (4);
        final var knob = started (lifecycle, KNOB, CUTOFF);
        final var pad = started (lifecycle, PAD, CLIP);
        final var strip = started (lifecycle, STRIP, VOLUME);
        lifecycle.cancel (KNOB);
        assertTrue (lifecycle.current (KNOB).isEmpty ());
        assertEquals (pad, lifecycle.current (PAD).orElseThrow ());
        assertEquals (strip, lifecycle.current (STRIP).orElseThrow ());
        assertEquals (List.of (knob.id ()), lifecycle.readyToFinish ());
    }

    @Test
    void unboundPressStaysSuppressedEvenWhenATargetAppears ()
    {
        final var lifecycle = lifecycle (4);
        assertEquals (Admission.UNBOUND, lifecycle.begin (KNOB, null).admission ());
        assertEquals (Admission.ALREADY_HELD, lifecycle.begin (KNOB, CUTOFF).admission ());
        assertTrue (lifecycle.current (KNOB).isEmpty ());
        lifecycle.release (KNOB);
        lifecycle.release (KNOB); // duplicate UP never ends a different interaction
        assertEquals (CUTOFF, started (lifecycle, KNOB, CUTOFF).target ());
    }

    @Test
    void boundedCapacityRejectsNewWorkWithoutLosingCleanup ()
    {
        final var lifecycle = lifecycle (1);
        final var edit = started (lifecycle, KNOB, CUTOFF);
        assertEquals (Admission.CAPACITY_EXHAUSTED, lifecycle.begin (PAD, CLIP).admission ());
        lifecycle.cancel (KNOB);
        lifecycle.beginFinish (edit.id ()).orElseThrow ();
        lifecycle.completeFinish (edit.id ());
        assertEquals (Admission.ALREADY_HELD, lifecycle.begin (PAD, CUTOFF).admission ());
        lifecycle.release (PAD);
        assertEquals (CLIP, started (lifecycle, PAD, CLIP).target ());
    }

    @Test
    void staleGenerationCannotBeginOrCompleteCleanupInTheReplacement ()
    {
        final var old = lifecycle (4);
        final var replacement = new InteractionLifecycle<String, Target> (2, Set.of (KNOB), 4);
        final var before = started (old, KNOB, CUTOFF);
        final var after = started (replacement, KNOB, CUTOFF);
        assertTrue (replacement.beginFinish (before.id ()).isEmpty ());
        assertFalse (replacement.completeFinish (before.id ()));
        replacement.release (KNOB);
        assertEquals (List.of (after.id ()), replacement.readyToFinish ());
        replacement.beginFinish (after.id ()).orElseThrow ();
        assertFalse (replacement.completeFinish (before.id ()));
        assertTrue (replacement.hasTargetWork (CUTOFF));
        assertTrue (replacement.completeFinish (after.id ()));
    }

    @Test
    void unknownControlsCannotExpandTheFootprint ()
    {
        final var lifecycle = lifecycle (4);
        final var edit = started (lifecycle, KNOB, CUTOFF);
        assertThrows (IllegalArgumentException.class, () -> lifecycle.begin ("unknown", VOLUME));
        assertEquals (edit, lifecycle.current (KNOB).orElseThrow ());
    }

    private static InteractionLifecycle<String, Target> lifecycle (final int interactions)
    {
        return new InteractionLifecycle<> (1, Set.of (KNOB, PAD, STRIP), interactions);
    }

    private static Interaction<String, Target> started (final InteractionLifecycle<String, Target> lifecycle, final String control, final Target target)
    {
        final var result = lifecycle.begin (control, target);
        assertEquals (Admission.STARTED, result.admission ());
        return result.interaction ().orElseThrow ();
    }
}
