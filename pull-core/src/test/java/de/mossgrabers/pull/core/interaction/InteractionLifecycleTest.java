// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.interaction;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
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
        final var lifecycle = lifecycle (4, 4);
        final var host = new ParameterHost (lifecycle);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF));
        final var cutoff = started (lifecycle, KNOB);
        final var write = host.submit (cutoff.id (), 0.75);
        assertEquals (0.0, host.observed (CUTOFF));

        lifecycle.replaceBindings (Map.of (KNOB, VOLUME));
        assertTrue (lifecycle.current (KNOB).isEmpty ());
        assertTrue (lifecycle.beginOperation (cutoff.id ()).isEmpty ());
        assertEquals (Admission.ALREADY_HELD, lifecycle.begin (KNOB).admission ());
        assertTrue (lifecycle.readyToFinish ().isEmpty (), "cleanup waits for the submitted write");
        host.advance (write);
        assertEquals (0.75, host.observed (CUTOFF));
        assertEquals (0.0, host.observed (VOLUME));
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
        final var volume = started (lifecycle, KNOB);
        host.advance (host.submit (volume.id (), 0.4));
        assertEquals (0.4, host.observed (VOLUME));
        assertEquals (0.75, host.observed (CUTOFF));
    }

    @Test
    void physicalReleaseDoesNotFinishHostWorkOrAuthorizeEarlyReuse ()
    {
        final var lifecycle = lifecycle (4, 4);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF, PAD, CUTOFF));
        final var edit = started (lifecycle, KNOB);
        final var first = lifecycle.beginOperation (edit.id ()).orElseThrow ();
        final var second = lifecycle.beginOperation (edit.id ()).orElseThrow ();
        lifecycle.release (KNOB);
        assertFalse (lifecycle.completeFinish (edit.id ()), "cannot complete cleanup before it starts");
        assertFalse (lifecycle.isIdle ());
        assertEquals (Admission.TARGET_BUSY, lifecycle.begin (PAD).admission ());

        assertTrue (lifecycle.completeOperation (second.id ()));
        assertFalse (lifecycle.completeOperation (second.id ()), "duplicate receipts cannot drain other work");
        assertTrue (lifecycle.readyToFinish ().isEmpty ());
        lifecycle.completeOperation (first.id ());
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
        final var lifecycle = lifecycle (4, 4);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF));
        final var old = started (lifecycle, KNOB);
        lifecycle.replaceBindings (Map.of (KNOB, VOLUME));
        lifecycle.release (KNOB);
        final var next = started (lifecycle, KNOB);
        final var operation = lifecycle.beginOperation (next.id ()).orElseThrow ();
        lifecycle.beginFinish (old.id ()).orElseThrow ();
        lifecycle.completeFinish (old.id ());
        assertFalse (lifecycle.completeFinish (old.id ()));
        assertEquals (next, lifecycle.current (KNOB).orElseThrow ());
        assertEquals (VOLUME, operation.target ());
        assertTrue (lifecycle.hasTargetWork (VOLUME));
    }

    @Test
    void cancellationOverridesNormalReleaseThatHasNotYetBeenDispatched ()
    {
        final var lifecycle = lifecycle (4, 4);
        lifecycle.replaceBindings (Map.of (PAD, CLIP));
        final var gesture = started (lifecycle, PAD);
        lifecycle.release (PAD);
        assertEquals (List.of (gesture.id ()), lifecycle.readyToFinish ());
        lifecycle.replaceBindings (Map.of ());
        assertEquals (EndReason.BINDING_CHANGED, lifecycle.beginFinish (gesture.id ()).orElseThrow ().reason ());
    }

    @Test
    void lostTargetNeverTurnsIntoCleanupOfTheReplacement ()
    {
        final var lifecycle = lifecycle (4, 4);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF));
        final var gesture = started (lifecycle, KNOB);
        final var operation = lifecycle.beginOperation (gesture.id ()).orElseThrow ();
        lifecycle.targetLost (CUTOFF);
        final var replacement = new Target (CUTOFF.identity (), 2);
        lifecycle.replaceBindings (Map.of (KNOB, replacement));
        assertTrue (lifecycle.current (KNOB).isEmpty ());
        assertTrue (lifecycle.readyToFinish ().isEmpty (), "loss is not a receipt for in-flight work");
        lifecycle.completeOperation (operation.id ()); // executor explicitly reports terminal target loss
        final var finish = lifecycle.beginFinish (gesture.id ()).orElseThrow ();
        assertEquals (EndReason.TARGET_LOST, finish.reason ());
        assertEquals (CUTOFF, finish.target ());
        lifecycle.completeFinish (finish.interaction ()); // executor reports abandonment; no host write
        lifecycle.release (KNOB);
        assertEquals (replacement, started (lifecycle, KNOB).target ());
    }

    @Test
    void unchangedBindingReplayPreservesOnlyTheActualHeldInteraction ()
    {
        final var lifecycle = lifecycle (4, 4);
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
        final var lifecycle = lifecycle (4, 4);
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
        final var lifecycle = lifecycle (4, 4);
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
        final var lifecycle = lifecycle (4, 4);
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
        final var lifecycle = lifecycle (1, 1);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF, PAD, CLIP));
        final var edit = started (lifecycle, KNOB);
        final var operation = lifecycle.beginOperation (edit.id ()).orElseThrow ();
        assertTrue (lifecycle.beginOperation (edit.id ()).isEmpty ());
        assertEquals (Admission.CAPACITY_EXHAUSTED, lifecycle.begin (PAD).admission ());
        lifecycle.replaceBindings (Map.of (PAD, CLIP));
        lifecycle.completeOperation (operation.id ());
        lifecycle.beginFinish (edit.id ()).orElseThrow ();
        lifecycle.completeFinish (edit.id ());
        assertEquals (Admission.ALREADY_HELD, lifecycle.begin (PAD).admission ());
        lifecycle.release (PAD);
        assertEquals (CLIP, started (lifecycle, PAD).target ());
    }

    @Test
    void operationBudgetIsSharedAcrossControlsAndRecoversOnlyOnRealCompletion ()
    {
        final var lifecycle = lifecycle (3, 1);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF, PAD, CLIP));
        final var knob = started (lifecycle, KNOB);
        final var pad = started (lifecycle, PAD);
        final var operation = lifecycle.beginOperation (knob.id ()).orElseThrow ();
        assertTrue (lifecycle.beginOperation (pad.id ()).isEmpty ());
        lifecycle.release (KNOB);
        assertTrue (lifecycle.beginOperation (pad.id ()).isEmpty ());
        lifecycle.completeOperation (operation.id ());
        assertEquals (CLIP, lifecycle.beginOperation (pad.id ()).orElseThrow ().target ());
    }

    @Test
    void stopClosesAdmissionButStillWaitsForInputAndHostCleanup ()
    {
        final var lifecycle = lifecycle (4, 4);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF, PAD, CLIP));
        final var edit = started (lifecycle, KNOB);
        final var operation = lifecycle.beginOperation (edit.id ()).orElseThrow ();
        lifecycle.stop ();
        lifecycle.stop ();
        assertTrue (lifecycle.beginOperation (edit.id ()).isEmpty ());
        assertEquals (Admission.STOPPED, lifecycle.begin (PAD).admission ());
        assertTrue (lifecycle.readyToFinish ().isEmpty ());
        lifecycle.release (KNOB);
        lifecycle.release (PAD);
        lifecycle.completeOperation (operation.id ());
        assertEquals (EndReason.STOPPED, lifecycle.beginFinish (edit.id ()).orElseThrow ().reason ());
        assertFalse (lifecycle.isIdle ());
        lifecycle.completeFinish (edit.id ());
        assertTrue (lifecycle.isIdle ());
    }

    @Test
    void staleGenerationCannotSubmitOrCompleteWorkInTheReplacement ()
    {
        final var old = lifecycle (4, 4);
        final var replacement = new InteractionLifecycle<String, Target> (2, Set.of (KNOB), 4, 4);
        old.replaceBindings (Map.of (KNOB, CUTOFF));
        replacement.replaceBindings (Map.of (KNOB, CUTOFF));
        final var before = started (old, KNOB);
        final var stale = old.beginOperation (before.id ()).orElseThrow ();
        final var after = started (replacement, KNOB);
        final var current = replacement.beginOperation (after.id ()).orElseThrow ();
        assertTrue (replacement.beginOperation (before.id ()).isEmpty ());
        assertFalse (replacement.completeOperation (stale.id ()));
        assertFalse (replacement.completeFinish (before.id ()));
        replacement.release (KNOB);
        assertTrue (replacement.readyToFinish ().isEmpty ());
        replacement.completeOperation (current.id ());
        assertEquals (List.of (after.id ()), replacement.readyToFinish ());
    }

    @Test
    void unknownControlsCannotExpandTheFootprintOrPartiallyReplaceBindings ()
    {
        final var lifecycle = lifecycle (4, 4);
        lifecycle.replaceBindings (Map.of (KNOB, CUTOFF));
        final var edit = started (lifecycle, KNOB);
        assertThrows (IllegalArgumentException.class, () -> lifecycle.begin ("unknown"));
        assertThrows (IllegalArgumentException.class, () -> lifecycle.replaceBindings (Map.of ("unknown", VOLUME)));
        assertEquals (edit, lifecycle.current (KNOB).orElseThrow ());
    }

    private static InteractionLifecycle<String, Target> lifecycle (final int interactions, final int operations)
    {
        return new InteractionLifecycle<> (1, Set.of (KNOB, PAD, STRIP), interactions, operations);
    }

    private static Interaction<String, Target> started (final InteractionLifecycle<String, Target> lifecycle, final String control)
    {
        final var result = lifecycle.begin (control);
        assertEquals (Admission.STARTED, result.admission ());
        return result.interaction ().orElseThrow ();
    }

    /** Deliberately separates submitting a write from applying it and publishing its receipt. */
    private static final class ParameterHost
    {
        private record Write (Operation<Target> operation, double value) {}
        private final InteractionLifecycle<String, Target> lifecycle;
        private final Map<OperationId, Write> submitted = new LinkedHashMap<> ();
        private final Map<Target, Double> values = new LinkedHashMap<> ();

        private ParameterHost (final InteractionLifecycle<String, Target> lifecycle) { this.lifecycle = lifecycle; }
        private double observed (final Target target) { return this.values.getOrDefault (target, 0.0); }
        private OperationId submit (final Id interaction, final double value)
        {
            final var operation = this.lifecycle.beginOperation (interaction).orElseThrow ();
            this.submitted.put (operation.id (), new Write (operation, value));
            return operation.id ();
        }
        private void advance (final OperationId id)
        {
            final var write = this.submitted.remove (id);
            this.values.put (write.operation ().target (), write.value ());
            assertTrue (this.lifecycle.completeOperation (id));
        }
    }
}
