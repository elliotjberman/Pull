// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.DesiredClipScan;
import de.mossgrabers.pull.core.api.effect.ClipLaunchMode;
import de.mossgrabers.pull.core.api.effect.ClipLaunchPolicy;
import de.mossgrabers.pull.core.api.effect.ClipLaunchQuantization;
import de.mossgrabers.pull.core.api.effect.ClipReleaseTrigger;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Real pool and clip consumer, with submitted commands separated from host advancement. */
class SelectedTrackFillClipHostTest
{
    private static final ClipLaunchPolicy POLICY = new ClipLaunchPolicy (ClipLaunchQuantization.IMMEDIATE,
        ClipLaunchMode.LEGATO_FROM_CLIP_OR_PROJECT, ClipReleaseTrigger.ALTERNATE);


    @Test
    void poolAcquisitionAndClipCoherencePrecedeCatalogAndRequestsDoNotReselect ()
    {
        final Fixture f = new Fixture ();
        f.select ("a", 25);
        for (int scene = 0; scene < 25; scene += 2)
            f.put ("a", scene, "Clip " + scene);
        f.requestPage (0);
        f.refresh ();
        assertFalse (f.host.clipCatalog ().scan ().ready ());
        assertEquals (0, f.scannerReads);
        for (int index = 0; index < 10; index++)
            f.refresh ();
        assertEquals (0, f.scannerReads, "Submitted track selection does not make old slot data ready");
        f.tick ();
        assertFalse (f.host.clipCatalog ().scan ().ready ());
        f.tick ();
        assertTrue (f.host.clipCatalog ().scan ().ready ());
        assertEquals (4, f.host.clipCatalog ().clips ().size ());
        final int assignments = f.assignments.size ();
        for (final int page: List.of (8, 16, 24))
        {
            f.requestPage (page);
            f.until (() -> f.host.clipCatalog ().scan ().ready () && f.host.clipCatalog ().scan ().sceneStart () == page);
        }
        assertEquals (13, f.host.clipCatalog ().clips ().size ());
        assertEquals (assignments, f.assignments.size (), "Paging an acquired scanner must not reselect its track");
        final ClipTargetId first = f.host.clipCatalog ().clips ().getFirst ().targetId ();
        f.put ("a", 19, "New fill");
        f.put ("a", 18, "Renamed fill");
        f.clips.get ("a").remove (20);
        f.requestPage (16);
        f.until (() -> f.host.clipCatalog ().clips ().stream ().anyMatch (clip -> "New fill".equals (clip.name ())));
        assertTrue (f.host.clipCatalog ().clips ().stream ().anyMatch (clip -> "Renamed fill".equals (clip.name ())));
        assertFalse (f.host.clipCatalog ().clips ().stream ().anyMatch (clip -> "Clip 20".equals (clip.name ())));
        assertEquals (first, f.host.clipCatalog ().clips ().getFirst ().targetId ());
    }


    @Test
    void armingWaitsForSceneReadbackAndLaunchPlaybackDoesNotAcknowledgeItself ()
    {
        final Fixture f = readyFill ();
        final ClipTargetId target = f.firstTarget ();
        f.bind (target);
        f.refresh ();
        assertTrue (f.host.armedClipTargets ().isEmpty ());
        f.tick (); // pool track assignment acknowledged; slot scroll submitted
        assertTrue (f.host.armedClipTargets ().isEmpty ());
        f.refresh ();
        assertTrue (f.host.armedClipTargets ().isEmpty ());
        f.tick (); // scene readback, first coherent clip sample
        assertTrue (f.host.armedClipTargets ().isEmpty ());
        f.tick ();
        assertEquals (Map.of (CoreControls.DRUM_FILL_1, target), f.host.armedClipTargets ());
        final DrumFillClipHost.LaunchTarget held = f.prepare (target);
        held.press (POLICY);
        assertEquals (List.of ("a:3"), f.presses);
        assertFalse (held.playbackState ().playing (), "Launch submission is not playback acknowledgement");
        f.tick ();
        assertTrue (held.playbackState ().playing ());
        held.release ();
        held.release ();
        assertEquals (List.of ("a:3"), f.releases);
        assertTrue (held.playbackState ().playing (), "Release submission is not a stopped acknowledgement");
        f.tick ();
        assertFalse (held.playbackState ().playing ());
        held.retire ();
        held.retire ();
        assertThrows (IllegalStateException.class, held::playbackState);
    }


    @Test
    void scannerPauseAndInactiveSubscriptionPreserveHeldCleanupWithoutSlotReads ()
    {
        final Fixture f = readyFill ();
        final DrumFillClipHost.LaunchTarget held = f.armAndPress ();
        final ClipCatalogSnapshot before = f.host.clipCatalog ();
        f.paused = true;
        final int reads = f.scannerReads;
        f.put ("a", 4, "New clip");
        for (int index = 0; index < 4; index++)
            f.tick ();
        assertEquals (reads, f.scannerReads);
        assertEquals (before, f.host.clipCatalog ());
        f.paused = false;
        f.until (() -> f.host.clipCatalog ().clips ().size () == 2);
        f.host.setDesiredScan (DesiredClipScan.inactive ());
        f.tick ();
        final int inactiveReads = f.scannerReads;
        for (int index = 0; index < 4; index++)
            f.tick ();
        assertEquals (inactiveReads, f.scannerReads);
        assertTrue (f.host.clipCatalog ().clips ().isEmpty ());
        assertTrue (f.host.armedClipTargets ().isEmpty ());
        held.release ();
        assertEquals (List.of ("a:3"), f.releases);
        f.tick ();
        held.retire ();
    }


    @Test
    void selectedTrackChangesCancelNewLaunchButRetainOldCleanupUntilExplicitRetirement ()
    {
        final Fixture f = readyFill ();
        final ClipTargetId target = f.firstTarget ();
        f.bind (target);
        f.until (() -> !f.host.armedClipTargets ().isEmpty ());
        final DrumFillClipHost.LaunchTarget prepared = f.prepare (target);
        final DrumFillClipHost.LaunchTarget held = f.prepare (target);
        held.press (POLICY);
        f.tick ();
        final RetainedCursorPool.Handle handle = f.lastPressed;
        f.select ("b", 8);
        f.put ("b", 6, "Fill B");
        assertThrows (IllegalStateException.class, () -> prepared.press (POLICY));
        f.refresh ();
        assertTrue (f.host.clipCatalog ().clips ().isEmpty ());
        f.requestPage (0);
        f.until (() -> f.host.clipCatalog ().clips ().size () == 1);
        final ClipTargetId next = f.firstTarget ();
        f.bind (next);
        for (int index = 0; index < 4; index++)
            f.tick ();
        assertEquals ("a", f.tracks.get (handle.slot ()));
        assertEquals (3, f.windows.get (handle.slot ()).intValue ());
        held.release ();
        f.tick ();
        assertFalse (held.playbackState ().playing ());
        assertEquals ("a", f.tracks.get (handle.slot ()), "Stopped readback alone does not retire a lease");
        assertTrue (f.host.armedClipTargets ().isEmpty ());
        held.retire ();
        f.until (() -> next.equals (f.host.armedClipTargets ().get (CoreControls.DRUM_FILL_1)));
        assertFalse (f.pool.valid (handle));
        f.prepare (next).press (POLICY);
        assertEquals (List.of ("a:3", "b:6"), f.presses);
        held.release ();
        assertEquals (List.of ("a:3"), f.releases);
    }


    @Test
    void targetOrSceneRebindingRejectsDelayedPressAndCleanup ()
    {
        for (final boolean trackChanged: List.of (true, false))
        {
            final Fixture f = readyFill ();
            final DrumFillClipHost.LaunchTarget held = f.armAndPress ();
            final RetainedCursorPool.Handle handle = f.lastPressed;
            if (trackChanged)
                f.tracks.put (handle.slot (), "replacement");
            else
                f.windows.put (handle.slot (), 4);
            assertThrows (IllegalStateException.class, held::release);
            assertThrows (IllegalStateException.class, held::playbackState);
            assertTrue (f.releases.isEmpty ());
            assertDoesNotThrow (held::retire);
        }
        final Fixture f = readyFill ();
        final ClipTargetId target = f.firstTarget ();
        f.bind (target);
        f.until (() -> !f.host.armedClipTargets ().isEmpty ());
        final DrumFillClipHost.LaunchTarget prepared = f.prepare (target);
        final RetainedCursorPool.Handle handle = f.pool.lookup ("fill-clips", "actuator-0").handle ();
        f.tracks.put (handle.slot (), "replacement");
        assertThrows (IllegalStateException.class, () -> prepared.press (POLICY));
        assertTrue (f.presses.isEmpty ());
    }


    @Test
    void projectChangeWithTheSameTrackIdCannotAdoptOldLaunchCleanup ()
    {
        final Fixture f = readyFill ();
        final DrumFillClipHost.LaunchTarget held = f.armAndPress ();
        f.projectGeneration++;
        assertThrows (IllegalStateException.class, held::release);
        assertThrows (IllegalStateException.class, held::playbackState);
        assertTrue (f.releases.isEmpty ());
        f.tick ();
        assertDoesNotThrow (held::retire);
    }


    @Test
    void sameTrackSceneReplacementCannotReusePreparedTarget ()
    {
        final Fixture f = readyFill ();
        f.put ("a", 5, "Fill B");
        f.until (() -> f.host.clipCatalog ().clips ().size () == 2);
        final ClipTargetId original = f.firstTarget ();
        f.bind (original);
        f.until (() -> !f.host.armedClipTargets ().isEmpty ());
        final DrumFillClipHost.LaunchTarget prepared = f.prepare (original);
        final ClipTargetId replacement = f.host.clipCatalog ().clips ().get (1).targetId ();
        f.bind (replacement);
        f.until (() -> replacement.equals (f.host.armedClipTargets ().get (CoreControls.DRUM_FILL_1)));
        assertThrows (IllegalStateException.class, () -> prepared.press (POLICY));
        assertTrue (f.presses.isEmpty ());
        f.prepare (replacement).press (POLICY);
        assertEquals (List.of ("a:5"), f.presses);
    }


    @Test
    void failedReleaseCanRetryAndRetirementDoesNotSubmitNewHostCommands ()
    {
        final Fixture f = readyFill ();
        final DrumFillClipHost.LaunchTarget held = f.armAndPress ();
        f.releaseFailures = 1;
        assertThrows (IllegalStateException.class, held::release);
        assertTrue (f.releases.isEmpty ());
        held.release ();
        held.release ();
        assertEquals (List.of ("a:3"), f.releases);
        f.put ("a", 5, "Fill B");
        f.until (() -> f.host.clipCatalog ().clips ().size () == 2);
        f.bind (f.host.clipCatalog ().clips ().get (1).targetId ());
        final int assignments = f.assignments.size ();
        final int moves = f.moves;
        assertDoesNotThrow (held::retire);
        assertEquals (assignments, f.assignments.size ());
        assertEquals (moves, f.moves);
        f.until (() -> !f.host.armedClipTargets ().isEmpty ());
    }


    @Test
    void sceneCountChangesInvalidateCatalogAndIdleBindings ()
    {
        final Fixture f = readyFill ();
        final ClipTargetId oldTarget = f.firstTarget ();
        f.bind (oldTarget);
        f.until (() -> !f.host.armedClipTargets ().isEmpty ());
        final long oldGeneration = f.host.clipCatalog ().generation ();
        f.sceneCounts.put ("a", 9);
        f.tick ();
        assertEquals (oldGeneration + 1, f.host.clipCatalog ().generation ());
        assertTrue (f.host.armedClipTargets ().isEmpty ());
        assertTrue (f.host.clipCatalog ().clips ().isEmpty ());
        assertThrows (IllegalArgumentException.class, () -> f.host.prepare (CoreControls.DRUM_FILL_1, oldGeneration, oldTarget));
        f.until (() -> f.host.clipCatalog ().clips ().size () == 1);
        assertFalse (oldTarget.equals (f.firstTarget ()));
    }


    @Test
    void eightProvisionedActuatorsHoldIndependentExactSlots ()
    {
        final Fixture f = new Fixture ();
        f.select ("a", 8);
        for (int scene = 0; scene < 8; scene++)
            f.put ("a", scene, "Fill " + scene);
        f.requestPage (0);
        f.until (() -> f.host.clipCatalog ().clips ().size () == 8);
        final Map<ControlId, ClipTargetId> bindings = new LinkedHashMap<> ();
        for (int index = 0; index < 8; index++)
            bindings.put (CoreControls.drumFills ().get (index), f.host.clipCatalog ().clips ().get (index).targetId ());
        f.host.setDesiredBindings (f.host.clipCatalog ().generation (), bindings);
        f.until (() -> f.host.armedClipTargets ().size () == 8);
        final List<DrumFillClipHost.LaunchTarget> held = new ArrayList<> ();
        for (final Map.Entry<ControlId, ClipTargetId> binding: bindings.entrySet ())
        {
            final DrumFillClipHost.LaunchTarget target = f.host.prepare (binding.getKey (), f.host.clipCatalog ().generation (), binding.getValue ());
            target.press (POLICY);
            held.add (target);
        }
        f.tick ();
        for (int index = 7; index >= 0; index--)
            held.get (index).release ();
        f.tick ();
        held.forEach (DrumFillClipHost.LaunchTarget::retire);
        assertEquals (List.of ("a:0", "a:1", "a:2", "a:3", "a:4", "a:5", "a:6", "a:7"), f.presses);
        assertEquals (List.of ("a:7", "a:6", "a:5", "a:4", "a:3", "a:2", "a:1", "a:0"), f.releases);
    }


    private static Fixture readyFill ()
    {
        final Fixture f = new Fixture ();
        f.select ("a", 8);
        f.put ("a", 3, "Fill A");
        f.requestPage (0);
        f.until (() -> f.host.clipCatalog ().clips ().size () == 1);
        return f;
    }


    private static final class Fixture implements RetainedCursorPool.Host, SelectedTrackFillClipHost.Adapter
    {
        private final Map<String, Map<Integer, String>> clips = new LinkedHashMap<> ();
        private final Map<String, Integer> sceneCounts = new HashMap<> ();
        private final Map<Integer, String> tracks = new HashMap<> ();
        private final Map<Integer, Long> propertyGenerations = new HashMap<> ();
        private final Map<Integer, Integer> windows = new HashMap<> ();
        private final Map<Integer, Boolean> playing = new HashMap<> ();
        private final List<RetainedCursorPool.Handle> assignments = new ArrayList<> ();
        private final List<Runnable> pending = new ArrayList<> ();
        private final List<String> presses = new ArrayList<> ();
        private final List<String> releases = new ArrayList<> ();
        private final RetainedCursorPool pool;
        private final SelectedTrackFillClipHost host;
        private SelectedTrackFillClipHost.SelectedTrackSample selected = new SelectedTrackFillClipHost.SelectedTrackSample ("", false, 0);
        private RetainedCursorPool.Handle lastPressed;
        private long sequence;
        private long projectGeneration = 1;
        private int scannerReads;
        private int moves;
        private int releaseFailures;
        private boolean paused;


        private Fixture ()
        {
            final List<RetainedCursorPool.Profile> profiles = new ArrayList<> ();
            profiles.add (RetainedCursorPool.Profile.CLIP_SCAN);
            for (int index = 0; index < 8; index++)
                profiles.add (RetainedCursorPool.Profile.CLIP_ACTUATOR);
            this.pool = new RetainedCursorPool (profiles, this);
            this.host = new SelectedTrackFillClipHost (this.pool, this, () -> this.paused);
        }


        private void select (final String track, final int sceneCount)
        {
            this.clips.computeIfAbsent (track, ignored -> new HashMap<> ());
            this.sceneCounts.put (track, sceneCount);
            this.selected = new SelectedTrackFillClipHost.SelectedTrackSample (track, true, this.selected.generation () + 1);
        }


        private void put (final String track, final int scene, final String name)
        {
            this.clips.get (track).put (scene, name);
        }


        private void requestPage (final int page)
        {
            this.host.setDesiredScan (new DesiredClipScan (this.selected.generation (), this.selected.trackId (), page));
        }


        private void refresh ()
        {
            this.pool.refresh ();
            this.host.refresh ();
        }


        private void tick ()
        {
            final List<Runnable> submitted = List.copyOf (this.pending);
            this.pending.clear ();
            submitted.forEach (Runnable::run);
            this.sequence++;
            this.refresh ();
        }


        private void until (final BooleanSupplier condition)
        {
            for (int index = 0; index < 100 && !condition.getAsBoolean (); index++)
                this.tick ();
            assertTrue (condition.getAsBoolean (), "Host did not settle within 100 observations");
        }


        private ClipTargetId firstTarget ()
        {
            return this.host.clipCatalog ().clips ().getFirst ().targetId ();
        }


        private void bind (final ClipTargetId target)
        {
            this.host.setDesiredBindings (this.host.clipCatalog ().generation (), Map.of (CoreControls.DRUM_FILL_1, target));
        }


        private DrumFillClipHost.LaunchTarget prepare (final ClipTargetId target)
        {
            return this.host.prepare (CoreControls.DRUM_FILL_1, this.host.clipCatalog ().generation (), target);
        }


        private DrumFillClipHost.LaunchTarget armAndPress ()
        {
            final ClipTargetId target = this.firstTarget ();
            this.bind (target);
            this.until (() -> !this.host.armedClipTargets ().isEmpty ());
            final DrumFillClipHost.LaunchTarget held = this.prepare (target);
            held.press (POLICY);
            return held;
        }


        @Override
        public RetainedCursorPool.Catalog catalog ()
        {
            return new RetainedCursorPool.Catalog (this.projectGeneration, RetainedCursorPool.Coverage.FULL, this.clips.size (), 0, 64, List.copyOf (this.clips.keySet ()));
        }


        @Override
        public boolean assign (final RetainedCursorPool.Handle handle)
        {
            this.assignments.add (handle);
            this.pending.add (() -> {
                this.tracks.put (handle.slot (), handle.trackId ());
                this.propertyGenerations.put (handle.slot (), handle.assignmentGeneration ());
            });
            return true;
        }


        @Override
        public RetainedCursorPool.Observation observe (final RetainedCursorPool.Handle handle)
        {
            final String track = this.tracks.getOrDefault (handle.slot (), "");
            return new RetainedCursorPool.Observation (this.sequence, !track.isEmpty (), track, true, this.propertyGenerations.getOrDefault (handle.slot (), 0L));
        }


        @Override
        public void release (final RetainedCursorPool.Handle handle)
        {
            // Resource retirement does not submit playback or magically advance host state.
        }


        @Override
        public SelectedTrackFillClipHost.SelectedTrackSample selectedTrack ()
        {
            return this.selected;
        }


        @Override
        public void moveScanner (final RetainedCursorPool.Handle handle, final int sceneStart)
        {
            this.moveActuator (handle, sceneStart);
        }


        @Override
        public SelectedTrackFillClipHost.ScannerSample scannerSample (final RetainedCursorPool.Handle handle)
        {
            this.scannerReads++;
            final String track = this.tracks.get (handle.slot ());
            final int start = this.windows.getOrDefault (handle.slot (), 0);
            final int count = this.sceneCounts.get (track);
            final List<SelectedTrackFillClipHost.SlotSample> slots = new ArrayList<> ();
            for (int index = 0; index < 8; index++)
            {
                final int scene = start + index;
                final String name = this.clips.get (track).get (scene);
                slots.add (new SelectedTrackFillClipHost.SlotSample (scene, name, scene < count, name != null));
            }
            return new SelectedTrackFillClipHost.ScannerSample (count, start, slots);
        }


        @Override
        public void moveActuator (final RetainedCursorPool.Handle handle, final int scene)
        {
            this.moves++;
            this.pending.add (() -> this.windows.put (handle.slot (), scene));
        }


        @Override
        public SelectedTrackFillClipHost.ActuatorSample actuatorSample (final RetainedCursorPool.Handle handle)
        {
            final String track = this.tracks.get (handle.slot ());
            final int scene = this.windows.getOrDefault (handle.slot (), 0);
            final String name = this.clips.getOrDefault (track, Map.of ()).get (scene);
            return new SelectedTrackFillClipHost.ActuatorSample (scene, name, scene < this.sceneCounts.getOrDefault (track, 0), name != null,
                this.playing.getOrDefault (handle.slot (), false), false, false);
        }


        @Override
        public void pressActuator (final RetainedCursorPool.Handle handle, final ClipLaunchPolicy policy)
        {
            this.lastPressed = handle;
            this.presses.add (this.coordinate (handle));
            this.pending.add (() -> this.playing.put (handle.slot (), true));
        }


        @Override
        public void releaseActuator (final RetainedCursorPool.Handle handle, final ClipReleaseTrigger trigger)
        {
            assertEquals (ClipReleaseTrigger.ALTERNATE, trigger);
            if (this.releaseFailures > 0)
            {
                this.releaseFailures--;
                throw new IllegalStateException ("Host rejected release");
            }
            this.releases.add (this.coordinate (handle));
            this.pending.add (() -> this.playing.put (handle.slot (), false));
        }


        private String coordinate (final RetainedCursorPool.Handle handle)
        {
            return this.tracks.get (handle.slot ()) + ":" + this.windows.getOrDefault (handle.slot (), 0);
        }
    }
}
