// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.interaction;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
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
    void cutoffToVolumeCancelsOnceAndSwallowsMotionUntilAFreshTouch ()
    {
        final var lifecycle = lifecycle (4);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF));
        final var cutoff = started (lifecycle, KNOB);

        lifecycle.replaceBindings (Map.of (KNOB, VOLUME));
        assertTrue (lifecycle.current (KNOB).isEmpty ());
        assertEquals (Admission.ALREADY_HELD, lifecycle.begin (KNOB).admission ());
        final var finish = lifecycle.beginFinish (cutoff.id ()).orElseThrow ();
        assertEquals (new Finish<> (cutoff.id (), CUTOFF, EndReason.BINDING_CHANGED), finish);
        assertTrue (lifecycle.beginFinish (cutoff.id ()).isEmpty (), "no duplicate cleanup");
        assertTrue (lifecycle.hasTargetWork (CUTOFF), "submission is not cleanup completion");

        lifecycle.completeFinish (finish.interaction ());
        assertFalse (lifecycle.hasTargetWork (CUTOFF));
        assertFalse (lifecycle.isIdle (), "the held tail still belongs to the old gesture");
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF));
        assertTrue (lifecycle.current (KNOB).isEmpty (), "returning to the page cannot revive a touch");
        lifecycle.replaceBindings (Map.of (KNOB, VOLUME));
        lifecycle.release (KNOB);
        assertTrue (lifecycle.isIdle ());
        assertEquals (VOLUME, started (lifecycle, KNOB).target ());
    }

    @Test
    void physicalReleaseDoesNotCompleteCleanupOrAuthorizeEarlyReuse ()
    {
        final var lifecycle = lifecycle (4);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF, PAD, CUTOFF));
        final var edit = started (lifecycle, KNOB);
        lifecycle.release (KNOB);
        assertFalse (lifecycle.completeFinish (edit.id ()), "cannot complete cleanup before it starts");
        assertFalse (lifecycle.isIdle ());
        assertEquals (Admission.TARGET_BUSY, lifecycle.begin (PAD).admission ());

        assertEquals (EndReason.RELEASED, lifecycle.beginFinish (edit.id ()).orElseThrow ().reason ());
        assertEquals (Admission.TARGET_BUSY, lifecycle.begin (KNOB).admission ());
        lifecycle.completeFinish (edit.id ());
        assertEquals (Admission.ALREADY_HELD, lifecycle.begin (PAD).admission (), "a rejected press cannot retry itself");
        lifecycle.release (PAD);
        lifecycle.release (KNOB);
        assertEquals (CUTOFF, started (lifecycle, PAD).target ());
    }

    @Test
    void lateOldCleanupCannotEndANewInteractionOnAnotherTarget ()
    {
        final var lifecycle = lifecycle (4);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF));
        final var old = started (lifecycle, KNOB);
        lifecycle.replaceBindings (Map.of (KNOB, VOLUME));
        lifecycle.release (KNOB);
        final var next = started (lifecycle, KNOB);
        lifecycle.beginFinish (old.id ()).orElseThrow ();
        lifecycle.completeFinish (old.id ());
        assertFalse (lifecycle.completeFinish (old.id ()));
        assertEquals (next, lifecycle.current (KNOB).orElseThrow ());
        assertTrue (lifecycle.hasTargetWork (VOLUME));
    }

    @Test
    void cancellationOverridesNormalReleaseThatHasNotYetBeenDispatched ()
    {
        final var lifecycle = lifecycle (4);
        lifecycle.replaceBindings (Map.of (PAD, CLIP));
        final var gesture = started (lifecycle, PAD);
        lifecycle.release (PAD);
        assertEquals (List.of (gesture.id ()), lifecycle.readyToFinish ());
        lifecycle.replaceBindings (Map.of ());
        assertEquals (EndReason.BINDING_CHANGED, lifecycle.beginFinish (gesture.id ()).orElseThrow ().reason ());
    }

    @Test
    void unchangedBindingReplayPreservesOnlyTheActualHeldInteraction ()
    {
        final var lifecycle = lifecycle (4);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF));
        final var edit = started (lifecycle, KNOB);
        lifecycle.replaceBindings (Map.of (KNOB, new Target (CUTOFF.identity (), 1)));
        assertEquals (edit, lifecycle.current (KNOB).orElseThrow ());
        assertEquals (Admission.ALREADY_HELD, lifecycle.begin (KNOB).admission ());
        lifecycle.replaceBindings (Map.of (KNOB, new Target (CUTOFF.identity (), 2)));
        assertTrue (lifecycle.current (KNOB).isEmpty ());
        assertEquals (EndReason.BINDING_CHANGED, lifecycle.beginFinish (edit.id ()).orElseThrow ().reason ());
    }

    @Test
    void visibilityAtAnotherControlDoesNotTransferTheGesture ()
    {
        final var lifecycle = lifecycle (4);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF));
        final var edit = started (lifecycle, KNOB);
        lifecycle.replaceBindings (Map.of (PAD, CUTOFF));
        assertTrue (lifecycle.current (KNOB).isEmpty ());
        assertEquals (Admission.TARGET_BUSY, lifecycle.begin (PAD).admission ());
        assertEquals (EndReason.BINDING_CHANGED, lifecycle.beginFinish (edit.id ()).orElseThrow ().reason ());
    }

    @Test
    void changingOneBindingDoesNotCancelOtherControls ()
    {
        final var lifecycle = lifecycle (4);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF, PAD, CLIP, STRIP, VOLUME));
        final var knob = started (lifecycle, KNOB);
        final var pad = started (lifecycle, PAD);
        final var strip = started (lifecycle, STRIP);
        lifecycle.replaceBindings (Map.of (PAD, CLIP, STRIP, VOLUME));
        assertTrue (lifecycle.current (KNOB).isEmpty ());
        assertEquals (pad, lifecycle.current (PAD).orElseThrow ());
        assertEquals (strip, lifecycle.current (STRIP).orElseThrow ());
        assertEquals (List.of (knob.id ()), lifecycle.readyToFinish ());
    }

    @Test
    void unboundPressStaysSuppressedEvenWhenATargetAppears ()
    {
        final var lifecycle = lifecycle (4);
        assertEquals (Admission.UNBOUND, lifecycle.begin (KNOB).admission ());
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF));
        assertEquals (Admission.ALREADY_HELD, lifecycle.begin (KNOB).admission ());
        assertTrue (lifecycle.current (KNOB).isEmpty ());
        lifecycle.release (KNOB);
        lifecycle.release (KNOB); // duplicate UP never ends a different interaction
        assertEquals (CUTOFF, started (lifecycle, KNOB).target ());
    }

    @Test
    void boundedCapacityRejectsNewWorkWithoutLosingCleanup ()
    {
        final var lifecycle = lifecycle (1);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF, PAD, CLIP));
        final var edit = started (lifecycle, KNOB);
        assertEquals (Admission.CAPACITY_EXHAUSTED, lifecycle.begin (PAD).admission ());
        lifecycle.replaceBindings (Map.of (PAD, CLIP));
        lifecycle.beginFinish (edit.id ()).orElseThrow ();
        lifecycle.completeFinish (edit.id ());
        assertEquals (Admission.ALREADY_HELD, lifecycle.begin (PAD).admission ());
        lifecycle.release (PAD);
        assertEquals (CLIP, started (lifecycle, PAD).target ());
    }

    @Test
    void staleGenerationCannotBeginOrCompleteCleanupInTheReplacement ()
    {
        final var old = lifecycle (4);
        final var replacement = new InteractionLifecycle<String, Target> (2, Set.of (KNOB), 4);
        old.replaceBindings (Map.of (KNOB, CUTOFF));
        replacement.replaceBindings (Map.of (KNOB, CUTOFF));
        final var before = started (old, KNOB);
        final var after = started (replacement, KNOB);
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
    void unknownControlsCannotExpandTheFootprintOrPartiallyReplaceBindings ()
    {
        final var lifecycle = lifecycle (4);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF));
        final var edit = started (lifecycle, KNOB);
        assertThrows (IllegalArgumentException.class, () -> lifecycle.begin ("unknown"));
        assertThrows (IllegalArgumentException.class, () -> lifecycle.replaceBindings (Map.of ("unknown", VOLUME)));
        assertEquals (edit, lifecycle.current (KNOB).orElseThrow ());
    }

    private static InteractionLifecycle<String, Target> lifecycle (final int interactions)
    {
        return new InteractionLifecycle<> (1, Set.of (KNOB, PAD, STRIP), interactions);
    }

    private static Interaction<String, Target> started (final InteractionLifecycle<String, Target> lifecycle, final String control)
    {
        final var result = lifecycle.begin (control);
        assertEquals (Admission.STARTED, result.admission ());
        return result.interaction ().orElseThrow ();
    }
}
