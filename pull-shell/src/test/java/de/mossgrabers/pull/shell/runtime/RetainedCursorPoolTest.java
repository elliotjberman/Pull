// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Coverage.FULL;
import static de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Coverage.PARTIAL;
import static de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Profile.*;
import static de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Status.*;
import static org.junit.jupiter.api.Assertions.*;


/** Cursor commands, identity propagation and profile-property propagation advance separately. */
class RetainedCursorPoolTest
{
    @Test
    void assignmentSubmissionAndFreshUuidDoNotPublishOldParameterValues ()
    {
        final Rig rig = new Rig (List.of (MIX), "a");
        rig.request ("knob", "a", MIX);
        final RetainedCursorPool.Handle handle = rig.lookup ("knob").handle ();
        assertTrue (rig.pool.assigned (handle));
        assertFalse (rig.pool.valid (handle));
        for (int i = 0; i < 5; i++) rig.pool.refresh ();
        assertEquals (PENDING, rig.lookup ("knob").status ());

        rig.host.advanceIdentities ();
        rig.pool.refresh ();
        assertEquals (PENDING, rig.lookup ("knob").status ());
        assertFalse (rig.pool.valid (handle));
        rig.host.advanceProperties ();
        rig.pool.refresh ();
        assertEquals (READY, rig.lookup ("knob").status ());
    }


    @Test
    void evenAlignedPropertiesNeedAnObservationLaterThanSelectionSubmission ()
    {
        final Rig rig = new Rig (List.of (MIX), "a");
        rig.request ("knob", "a", MIX);
        final RetainedCursorPool.Handle handle = rig.lookup ("knob").handle ();
        rig.host.observations.put (handle.slot (), new RetainedCursorPool.Observation (0, true, "a", true, handle.assignmentGeneration ()));
        rig.pool.refresh ();
        assertEquals (PENDING, rig.lookup ("knob").status ());
        rig.host.advanceProperties ();
        rig.pool.refresh ();
        assertEquals (READY, rig.lookup ("knob").status ());
    }


    @Test
    void retainedHandleAndUnchangedReplaySurviveCatalogReorder ()
    {
        final Rig rig = new Rig (List.of (MIX), "a", "b");
        final RetainedCursorPool.Handle handle = rig.acquire ("knob", "a", MIX);
        rig.host.catalog = new RetainedCursorPool.Catalog (1, FULL, 2, 0, 64, List.of ("b", "a"));
        for (int i = 0; i < 5; i++)
        {
            rig.request ("knob", "a", MIX);
            rig.pool.refresh ();
        }
        assertEquals (1, rig.host.selections.size (), "Unchanged replay must not reselect or unpin");
        assertTrue (rig.pool.writable (handle));
        assertEquals (handle, rig.lookup ("knob").handle ());
    }


    @Test
    void changingOwnerKeepsExactCleanupUntilConsumerExplicitlyRetires ()
    {
        final Rig rig = new Rig (List.of (CLIP_ACTUATOR), "a", "b");
        final RetainedCursorPool.Handle old = rig.acquire ("fill", "a", CLIP_ACTUATOR);
        assertTrue (rig.pool.retain (old));
        rig.request ("fill", "b", CLIP_ACTUATOR);
        assertEquals (UNAVAILABLE, rig.lookup ("fill").status ());
        assertTrue (rig.pool.valid (old));
        assertFalse (rig.pool.writable (old));
        rig.pool.refresh ();
        assertEquals (UNAVAILABLE, rig.lookup ("fill").status ());
        assertTrue (rig.host.retired.isEmpty ());

        rig.pool.release (old);
        rig.pool.refresh ();
        assertEquals (PENDING, rig.lookup ("fill").status ());
        rig.advance ();
        final RetainedCursorPool.Handle replacement = rig.lookup ("fill").handle ();
        assertEquals (READY, rig.lookup ("fill").status ());
        assertTrue (replacement.assignmentGeneration () > old.assignmentGeneration ());
        assertFalse (rig.pool.assigned (old));
        assertFalse (rig.pool.valid (old));
    }


    @Test
    void lastIndependentHoldControlsRetirementAndCapacity ()
    {
        final Rig rig = new Rig (List.of (MIX), "a", "b");
        final RetainedCursorPool.Handle old = rig.acquire ("knob", "a", MIX);
        assertTrue (rig.pool.retain (old));
        assertTrue (rig.pool.retain (old));
        rig.request ("knob", "b", MIX);
        rig.pool.release (old);
        rig.pool.refresh ();
        assertTrue (rig.pool.valid (old));
        assertEquals (UNAVAILABLE, rig.lookup ("knob").status ());
        rig.pool.release (old);
        rig.pool.refresh ();
        assertEquals (PENDING, rig.lookup ("knob").status ());
    }


    @Test
    void applyRechecksLiveIdentityAfterPreparationAndUndoNeverRevivesOldHandle ()
    {
        final Rig rig = new Rig (List.of (MIX, MIX), "a", "b");
        final RetainedCursorPool.Handle prepared = rig.acquire ("knob", "a", MIX);
        assertTrue (rig.pool.retain (prepared));
        rig.host.retarget (prepared, "b", true);
        assertFalse (rig.pool.valid (prepared));
        assertNotEquals (READY, rig.lookup ("knob").status ());
        rig.host.retarget (prepared, "a", true);
        assertFalse (rig.pool.valid (prepared), "Returning UUID cannot revive a revoked gesture");
        rig.pool.refresh ();
        rig.advance ();
        final RetainedCursorPool.Handle newHandle = rig.lookup ("knob").handle ();
        assertNotEquals (prepared, newHandle);
        assertEquals (READY, rig.lookup ("knob").status ());
        assertFalse (rig.pool.valid (prepared));
        rig.pool.release (prepared);
        assertTrue (rig.pool.valid (newHandle));
    }


    @Test
    void deletedRetainedTargetIsUnavailableForCleanupAndItsUndoNeedsFreshAcquisition ()
    {
        final Rig rig = new Rig (List.of (MIX), "a");
        final RetainedCursorPool.Handle old = rig.acquire ("knob", "a", MIX);
        assertTrue (rig.pool.retain (old));
        rig.host.catalog = new RetainedCursorPool.Catalog (1, FULL, 0, 0, 64, List.of ());
        rig.host.retarget (old, "", false);
        rig.pool.refresh ();
        assertEquals (MISSING, rig.lookup ("knob").status ());
        assertFalse (rig.pool.valid (old));
        rig.host.catalog = new RetainedCursorPool.Catalog (1, FULL, 1, 0, 64, List.of ("a"));
        rig.pool.refresh ();
        assertEquals (UNAVAILABLE, rig.lookup ("knob").status ());
        rig.pool.release (old); // Explicit abandonment; there is no exact deleted restore target.
        rig.pool.refresh ();
        rig.advance ();
        assertEquals (READY, rig.lookup ("knob").status ());
        assertFalse (rig.pool.valid (old));
    }


    @Test
    void projectSwitchRejectsOldHandlesBeforeRefreshAndRequiresExplicitReplay ()
    {
        final Rig rig = new Rig (List.of (MIX), "a");
        final RetainedCursorPool.Handle old = rig.acquire ("knob", "a", MIX);
        assertTrue (rig.pool.retain (old));
        rig.host.catalog = new RetainedCursorPool.Catalog (2, FULL, 1, 0, 64, List.of ("a"));
        assertFalse (rig.pool.valid (old));
        assertFalse (rig.pool.assigned (old));
        rig.pool.refresh ();
        assertEquals (UNAVAILABLE, rig.lookup ("knob").status ());
        rig.request ("knob", "a", MIX);
        rig.advance ();
        final RetainedCursorPool.Handle current = rig.lookup ("knob").handle ();
        rig.pool.release (old);
        assertTrue (rig.pool.valid (current));
        assertNotEquals (old.projectGeneration (), current.projectGeneration ());
    }


    @Test
    void pendingDiscoveryRetriesOnlyDuringReconcileOrRefresh ()
    {
        final Rig rig = new Rig (List.of (MIX), "a");
        rig.host.catalog = new RetainedCursorPool.Catalog (1, RetainedCursorPool.Coverage.PENDING, -1, 0, 64, List.of ());
        rig.request ("knob", "a", MIX);
        assertEquals (PENDING, rig.lookup ("knob").status ());
        rig.host.catalog = new RetainedCursorPool.Catalog (1, FULL, 1, 0, 64, List.of ("a"));
        rig.lookup ("knob");
        assertTrue (rig.host.selections.isEmpty ());
        rig.pool.refresh ();
        assertEquals (1, rig.host.selections.size ());
        rig.advance ();
        assertEquals (READY, rig.lookup ("knob").status ());
    }


    @Test
    void discoveryRaceRejectsWithoutFaultOrLeakingCapacityAndRetriesOnLaterRefresh ()
    {
        final Rig rig = new Rig (List.of (MIX), "a");
        rig.host.rejectAssignment = true;
        assertDoesNotThrow (() -> rig.request ("knob", "a", MIX));
        assertEquals (PENDING, rig.lookup ("knob").status ());
        assertEquals ("assignment-rejected", rig.lookup ("knob").reason ());
        assertNull (rig.lookup ("knob").handle ());
        rig.host.rejectAssignment = false;
        assertEquals (PENDING, rig.lookup ("knob").status ());
        assertTrue (rig.host.selections.isEmpty ());
        rig.pool.refresh ();
        assertNotNull (rig.lookup ("knob").handle ());
        rig.advance ();
        assertEquals (READY, rig.lookup ("knob").status ());
    }


    @Test
    void deletingPendingTargetReportsMissingAndUndoRequiresAnotherAssignment ()
    {
        final Rig rig = new Rig (List.of (MIX), "a");
        rig.request ("knob", "a", MIX);
        final RetainedCursorPool.Handle pending = rig.lookup ("knob").handle ();
        rig.host.catalog = new RetainedCursorPool.Catalog (1, FULL, 0, 0, 64, List.of ());
        rig.pool.refresh ();
        assertEquals (MISSING, rig.lookup ("knob").status ());
        assertFalse (rig.pool.assigned (pending));
        rig.host.catalog = new RetainedCursorPool.Catalog (1, FULL, 1, 0, 64, List.of ("a"));
        rig.pool.refresh ();
        assertNotEquals (pending, rig.lookup ("knob").handle ());
        rig.advance ();
        assertEquals (READY, rig.lookup ("knob").status ());
        assertFalse (rig.pool.valid (pending));
    }


    @Test
    void sixtyFourSixtyFiveBoundaryDistinguishesCapacityAndDiscoveryCoverage ()
    {
        final List<String> tracks = IntStream.range (0, 64).mapToObj (i -> "track-" + i).toList ();
        final Rig rig = new Rig (Collections.nCopies (64, MIX), tracks.toArray (String[]::new));
        final List<RetainedCursorPool.Request> requests = tracks.stream ().map (id -> new RetainedCursorPool.Request (id, id, MIX)).toList ();
        rig.pool.reconcile ("test", requests);
        rig.advance ();
        assertTrue (tracks.stream ().allMatch (id -> rig.lookup (id).status () == READY));
        rig.pool.reconcile ("extra", List.of (new RetainedCursorPool.Request ("missing", "track-64", MIX)));
        assertEquals (MISSING, rig.pool.lookup ("extra", "missing").status ());
        rig.host.catalog = new RetainedCursorPool.Catalog (1, PARTIAL, 65, 0, 64, tracks);
        rig.pool.refresh ();
        assertEquals ("outside-discovery-coverage", rig.pool.lookup ("extra", "missing").reason ());
        rig.pool.reconcile ("extra", List.of (new RetainedCursorPool.Request ("duplicate-owner", "track-0", MIX)));
        assertEquals ("capacity-exhausted", rig.pool.lookup ("extra", "duplicate-owner").reason ());
        assertTrue (tracks.stream ().allMatch (id -> rig.lookup (id).status () == READY));
    }


    @Test
    void profileResourcesAndNamespacesRemainIndependentEvenForTheSameTrack ()
    {
        final Rig rig = new Rig (List.of (MIX, CLIP_SCAN, CLIP_ACTUATOR, CLIP_ACTUATOR), "a");
        rig.pool.reconcile ("mix", List.of (new RetainedCursorPool.Request ("a", "a", MIX)));
        rig.pool.reconcile ("clips", List.of (new RetainedCursorPool.Request ("scanner", "a", CLIP_SCAN),
            new RetainedCursorPool.Request ("fill-1", "a", CLIP_ACTUATOR), new RetainedCursorPool.Request ("fill-2", "a", CLIP_ACTUATOR)));
        rig.advance ();
        final RetainedCursorPool.Handle one = rig.pool.lookup ("clips", "fill-1").handle ();
        final RetainedCursorPool.Handle two = rig.pool.lookup ("clips", "fill-2").handle ();
        assertNotEquals (one.slot (), two.slot ());
        assertNotEquals (one.assignmentGeneration (), two.assignmentGeneration ());
        rig.pool.reconcile ("mix", List.of ());
        assertTrue (rig.pool.valid (one));
        assertTrue (rig.pool.valid (two));
        rig.pool.reconcile ("device", List.of (new RetainedCursorPool.Request ("page", "a", DEVICE_PAGE)));
        assertEquals ("profile-unavailable", rig.pool.lookup ("device", "page").reason ());
    }


    @Test
    void retainedTrackRemainsAddressableWhenItsDiscoveryWindowBecomesPartial ()
    {
        final Rig rig = new Rig (List.of (MIX), "a", "b");
        final RetainedCursorPool.Handle handle = rig.acquire ("knob", "a", MIX);
        rig.host.catalog = new RetainedCursorPool.Catalog (1, PARTIAL, 65, 0, 64, List.of ("b"));
        rig.pool.refresh ();
        assertEquals (READY, rig.lookup ("knob").status ());
        assertTrue (rig.pool.valid (handle));
    }


    private static final class Rig
    {
        final FakeHost host;
        final RetainedCursorPool pool;

        Rig (final List<RetainedCursorPool.Profile> profiles, final String... tracks)
        {
            this.host = new FakeHost (List.of (tracks));
            this.pool = new RetainedCursorPool (profiles, this.host);
        }

        void request (final String owner, final String track, final RetainedCursorPool.Profile profile)
        {
            this.pool.reconcile ("test", List.of (new RetainedCursorPool.Request (owner, track, profile)));
        }

        RetainedCursorPool.Result lookup (final String owner)
        {
            return this.pool.lookup ("test", owner);
        }

        RetainedCursorPool.Handle acquire (final String owner, final String track, final RetainedCursorPool.Profile profile)
        {
            this.request (owner, track, profile);
            this.advance ();
            assertEquals (READY, this.lookup (owner).status ());
            return this.lookup (owner).handle ();
        }

        void advance ()
        {
            this.host.advanceIdentities ();
            this.pool.refresh ();
            this.host.advanceProperties ();
            this.pool.refresh ();
        }
    }


    private static final class FakeHost implements RetainedCursorPool.Host
    {
        private RetainedCursorPool.Catalog catalog;
        private long sequence;
        private boolean rejectAssignment;
        private final List<RetainedCursorPool.Handle> selections = new ArrayList<> ();
        private final List<RetainedCursorPool.Handle> retired = new ArrayList<> ();
        private final Map<Integer, RetainedCursorPool.Handle> submitted = new HashMap<> ();
        private final Map<Integer, RetainedCursorPool.Observation> observations = new HashMap<> ();

        FakeHost (final List<String> tracks)
        {
            this.catalog = new RetainedCursorPool.Catalog (1, FULL, tracks.size (), 0, 64, tracks);
        }

        @Override
        public RetainedCursorPool.Catalog catalog ()
        {
            return this.catalog;
        }

        @Override
        public boolean assign (final RetainedCursorPool.Handle handle)
        {
            if (this.rejectAssignment)
                return false;
            this.selections.add (handle);
            this.submitted.put (handle.slot (), handle);
            return true;
        }

        @Override
        public RetainedCursorPool.Observation observe (final RetainedCursorPool.Handle handle)
        {
            return this.observations.getOrDefault (handle.slot (), new RetainedCursorPool.Observation (this.sequence, false, "", false, 0));
        }

        @Override
        public void release (final RetainedCursorPool.Handle handle)
        {
            this.retired.add (handle);
            this.submitted.remove (handle.slot ());
        }

        void advanceIdentities ()
        {
            this.sequence++;
            this.submitted.forEach ((slot, handle) -> {
                if (this.observe (handle).propertiesAssignmentGeneration () != handle.assignmentGeneration ())
                    this.observations.put (slot, new RetainedCursorPool.Observation (this.sequence, true, handle.trackId (), true, 0));
            });
        }

        void advanceProperties ()
        {
            this.sequence++;
            this.submitted.forEach ((slot, handle) -> {
                final RetainedCursorPool.Observation prior = this.observe (handle);
                this.observations.put (slot, new RetainedCursorPool.Observation (this.sequence, prior.exists (), prior.trackId (), prior.pinned (), handle.assignmentGeneration ()));
            });
        }

        void retarget (final RetainedCursorPool.Handle handle, final String track, final boolean exists)
        {
            this.sequence++;
            this.observations.put (handle.slot (), new RetainedCursorPool.Observation (this.sequence, exists, track, true, handle.assignmentGeneration ()));
        }

    }
}
