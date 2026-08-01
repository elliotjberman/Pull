// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Deterministic selected-track scanner and pinned-actuator state-machine tests.
 */
class SelectedTrackFillClipHostTest
{
    private static final ClipLaunchPolicy LAUNCH_POLICY = new ClipLaunchPolicy (
        ClipLaunchQuantization.IMMEDIATE,
        ClipLaunchMode.LEGATO_FROM_CLIP_OR_PROJECT,
        ClipReleaseTrigger.ALTERNATE);
    private static final ClipLaunchPolicy DIFFERENT_LAUNCH_POLICY = new ClipLaunchPolicy (
        ClipLaunchQuantization.DEFAULT,
        ClipLaunchMode.DEFAULT,
        ClipReleaseTrigger.MAIN);

    @Test
    void publishesOnlyCompleteAllSceneSweepsInAbsoluteOrder ()
    {
        final FakeAdapter adapter = new FakeAdapter ();
        adapter.selectTrack ("track-a", 25);
        for (int scene = 0; scene < 13; scene++)
            adapter.putClip (scene * 2, "Clip " + scene);

        final SelectedTrackFillClipHost host = new SelectedTrackFillClipHost (adapter);
        assertTrue (host.refresh ());
        assertEquals (1, host.clipCatalog ().generation ());
        assertTrue (host.clipCatalog ().clips ().isEmpty ());

        // The first page may be accepted, but no partial catalog is ever exposed.
        assertFalse (host.refresh ());
        assertTrue (host.clipCatalog ().clips ().isEmpty ());

        refreshUntil (host, () -> host.clipCatalog ().clips ().size () == 13);
        assertEquals (
            List.of ("Clip 0", "Clip 1", "Clip 2", "Clip 3", "Clip 4", "Clip 5", "Clip 6", "Clip 7", "Clip 8", "Clip 9", "Clip 10", "Clip 11", "Clip 12"),
            host.clipCatalog ().clips ().stream ().map (clip -> clip.name ()).toList ());
        assertTrue (adapter.scannerMoves.containsAll (List.of (Integer.valueOf (0), Integer.valueOf (8), Integer.valueOf (16), Integer.valueOf (24))));

        final List<ClipTargetId> originalIds = host.clipCatalog ().clips ().stream ().map (clip -> clip.targetId ()).toList ();
        adapter.putClip (19, "Off-window edit");
        refreshUntil (host, () -> host.clipCatalog ().clips ().stream ().anyMatch (clip -> "Off-window edit".equals (clip.name ())));

        // A metadata-only rescan keeps the track generation and existing absolute-scene IDs.
        assertEquals (1, host.clipCatalog ().generation ());
        assertEquals (originalIds.subList (0, 10), host.clipCatalog ().clips ().stream ().map (clip -> clip.targetId ()).limit (10).toList ());
    }


    @Test
    void armsOnlyAfterTwoValidatedSamplesAndLaunchesTheParkedSlot ()
    {
        final FakeAdapter adapter = new FakeAdapter ();
        adapter.selectTrack ("track-a", 8);
        adapter.putClip (3, "Fill A");
        final SelectedTrackFillClipHost host = new SelectedTrackFillClipHost (adapter);
        refreshUntil (host, () -> host.clipCatalog ().clips ().size () == 1);

        final ClipCatalogSnapshot catalog = host.clipCatalog ();
        final ClipTargetId targetId = catalog.clips ().get (0).targetId ();
        final ControlId control = CoreControls.DRUM_FILL_1;
        adapter.automaticallyParkActuators = false;
        host.setDesiredBindings (catalog.generation (), Map.of (control, targetId));
        assertTrue (host.armedClipTargets ().isEmpty ());
        assertThrows (IllegalArgumentException.class, () -> host.prepare (control, catalog.generation (), targetId));

        adapter.setActuatorSample (0, new SelectedTrackFillClipHost.ActuatorSample ("track-a", true, true, 2, "Wrong scene", true, true));
        host.refresh ();
        assertTrue (host.armedClipTargets ().isEmpty ());

        adapter.setActuatorSample (0, new SelectedTrackFillClipHost.ActuatorSample ("track-a", true, false, 3, "Fill A", true, true));
        host.refresh ();
        host.refresh ();
        assertTrue (host.armedClipTargets ().isEmpty ());

        adapter.setActuatorSample (0, new SelectedTrackFillClipHost.ActuatorSample ("track-a", true, true, 3, "Fill A", true, true));
        host.refresh ();
        assertTrue (host.armedClipTargets ().isEmpty ());
        assertTrue (host.refresh ());
        assertEquals (Map.of (control, targetId), host.armedClipTargets ());

        final DrumFillClipHost.LaunchTarget launchTarget = host.prepare (control, catalog.generation (), targetId);
        launchTarget.press (LAUNCH_POLICY);
        assertThrows (IllegalStateException.class, () -> launchTarget.press (DIFFERENT_LAUNCH_POLICY));
        launchTarget.release ();
        assertEquals (List.of ("track-a:3"), adapter.presses);
        assertEquals (List.of ("track-a:3"), adapter.releases);
        assertEquals (List.of (LAUNCH_POLICY), adapter.launchPolicies);
        assertEquals (List.of (ClipReleaseTrigger.ALTERNATE), adapter.releaseTriggers);
    }


    @Test
    void selectedTrackChangeClearsCatalogAndIdleBindingsImmediately ()
    {
        final FakeAdapter adapter = new FakeAdapter ();
        adapter.selectTrack ("track-a", 4);
        adapter.putClip (1, "Fill A");
        final SelectedTrackFillClipHost host = new SelectedTrackFillClipHost (adapter);
        refreshUntil (host, () -> host.clipCatalog ().clips ().size () == 1);
        armFirstTarget (host, adapter);

        final long oldGeneration = host.clipCatalog ().generation ();
        final int oldActuatorSelections = adapter.actuatorSelections.get (0).intValue ();
        adapter.clearClips ();
        adapter.selectTrack ("track-b", 6);
        adapter.putClip (5, "Fill B");

        assertTrue (host.refresh ());
        assertEquals (oldGeneration + 1, host.clipCatalog ().generation ());
        assertTrue (host.clipCatalog ().clips ().isEmpty ());
        assertTrue (host.armedClipTargets ().isEmpty ());
        assertEquals (oldActuatorSelections, adapter.actuatorSelections.get (0).intValue ());
        assertThrows (IllegalArgumentException.class, () -> host.setDesiredBindings (oldGeneration, Map.of (CoreControls.DRUM_FILL_1, new ClipTargetId (0))));

        refreshUntil (host, () -> host.clipCatalog ().clips ().size () == 1);
        assertEquals ("Fill B", host.clipCatalog ().clips ().get (0).name ());
    }


    @Test
    void supportsTwelveIndependentSimultaneousLeases ()
    {
        final FakeAdapter adapter = new FakeAdapter ();
        adapter.selectTrack ("track-a", 12);
        for (int scene = 0; scene < 12; scene++)
            adapter.putClip (scene, "Fill " + (scene + 1));

        final SelectedTrackFillClipHost host = new SelectedTrackFillClipHost (adapter);
        refreshUntil (host, () -> host.clipCatalog ().clips ().size () == 12);

        final Map<ControlId, ClipTargetId> bindings = new LinkedHashMap<> ();
        final List<ClipTargetId> targets = host.clipCatalog ().clips ().stream ().map (clip -> clip.targetId ()).toList ();
        for (int index = 0; index < 12; index++)
            bindings.put (CoreControls.drumFills ().get (index), targets.get (index));
        host.setDesiredBindings (host.clipCatalog ().generation (), bindings);
        refreshUntil (host, () -> host.armedClipTargets ().size () == 12);

        final List<DrumFillClipHost.LaunchTarget> leases = new ArrayList<> (12);
        for (int index = 0; index < 12; index++)
        {
            final DrumFillClipHost.LaunchTarget lease = host.prepare (CoreControls.drumFills ().get (index), host.clipCatalog ().generation (), targets.get (index));
            lease.press (LAUNCH_POLICY);
            leases.add (lease);
        }
        for (int index = leases.size () - 1; index >= 0; index--)
            leases.get (index).release ();

        assertEquals (12, adapter.presses.size ());
        assertEquals (12, adapter.releases.size ());
    }


    @Test
    void heldActuatorStaysFrozenAcrossSelectionAndNewDesiredBinding ()
    {
        final FakeAdapter adapter = new FakeAdapter ();
        adapter.selectTrack ("track-a", 4);
        adapter.putClip (1, "Fill A");
        final SelectedTrackFillClipHost host = new SelectedTrackFillClipHost (adapter);
        refreshUntil (host, () -> host.clipCatalog ().clips ().size () == 1);
        final ClipTargetId firstTarget = armFirstTarget (host, adapter);
        final long firstGeneration = host.clipCatalog ().generation ();

        final DrumFillClipHost.LaunchTarget held = host.prepare (CoreControls.DRUM_FILL_1, firstGeneration, firstTarget);
        held.press (LAUNCH_POLICY);
        final int selectionsWhileParked = adapter.actuatorSelections.get (0).intValue ();
        final int movesWhileParked = adapter.actuatorMoves.get (0).intValue ();

        adapter.clearClips ();
        adapter.selectTrack ("track-b", 5);
        adapter.putClip (4, "Fill B");
        host.refresh ();
        refreshUntil (host, () -> host.clipCatalog ().clips ().size () == 1);

        final ClipTargetId secondTarget = host.clipCatalog ().clips ().get (0).targetId ();
        host.setDesiredBindings (host.clipCatalog ().generation (), Map.of (CoreControls.DRUM_FILL_1, secondTarget));
        host.refresh ();
        assertEquals (selectionsWhileParked, adapter.actuatorSelections.get (0).intValue ());
        assertEquals (movesWhileParked, adapter.actuatorMoves.get (0).intValue ());

        held.release ();
        assertEquals (List.of ("track-a:1"), adapter.releases);
        assertTrue (adapter.actuatorSelections.get (0).intValue () > selectionsWhileParked);
        assertTrue (adapter.actuatorMoves.get (0).intValue () > movesWhileParked);
    }


    @Test
    void failedConcreteReleaseKeepsTheOldSlotLockedUntilRetrySucceeds ()
    {
        final FakeAdapter adapter = new FakeAdapter ();
        adapter.selectTrack ("track-a", 2);
        adapter.putClip (0, "Fill A");
        adapter.putClip (1, "Fill B");
        final SelectedTrackFillClipHost host = new SelectedTrackFillClipHost (adapter);
        refreshUntil (host, () -> host.clipCatalog ().clips ().size () == 2);

        final ClipCatalogSnapshot catalog = host.clipCatalog ();
        final ClipTargetId firstTarget = catalog.clips ().get (0).targetId ();
        final ClipTargetId secondTarget = catalog.clips ().get (1).targetId ();
        host.setDesiredBindings (catalog.generation (), Map.of (CoreControls.DRUM_FILL_1, firstTarget));
        refreshUntil (host, () -> !host.armedClipTargets ().isEmpty ());

        final DrumFillClipHost.LaunchTarget held = host.prepare (CoreControls.DRUM_FILL_1, catalog.generation (), firstTarget);
        held.press (LAUNCH_POLICY);
        final int movesWhileHeld = adapter.actuatorMoves.get (0).intValue ();
        host.setDesiredBindings (catalog.generation (), Map.of (CoreControls.DRUM_FILL_1, secondTarget));
        adapter.remainingReleaseFailures = 1;

        assertThrows (IllegalStateException.class, held::release);
        assertEquals (movesWhileHeld, adapter.actuatorMoves.get (0).intValue ());
        assertTrue (adapter.releases.isEmpty ());

        held.release ();
        assertEquals (List.of ("track-a:0"), adapter.releases);
        assertTrue (adapter.actuatorMoves.get (0).intValue () > movesWhileHeld);
        refreshUntil (host, () -> secondTarget.equals (host.armedClipTargets ().get (CoreControls.DRUM_FILL_1)));
    }


    private static ClipTargetId armFirstTarget (final SelectedTrackFillClipHost host, final FakeAdapter adapter)
    {
        final ClipCatalogSnapshot catalog = host.clipCatalog ();
        final ClipTargetId targetId = catalog.clips ().get (0).targetId ();
        host.setDesiredBindings (catalog.generation (), Map.of (CoreControls.DRUM_FILL_1, targetId));
        refreshUntil (host, () -> !host.armedClipTargets ().isEmpty ());
        return targetId;
    }


    private static void refreshUntil (final SelectedTrackFillClipHost host, final Condition condition)
    {
        for (int attempt = 0; attempt < 100 && !condition.satisfied (); attempt++)
            host.refresh ();
        assertTrue (condition.satisfied (), "Condition did not stabilize within 100 refreshes");
    }


    @FunctionalInterface
    private interface Condition
    {
        boolean satisfied ();
    }


    private static final class FakeAdapter implements SelectedTrackFillClipHost.Adapter
    {
        private final Map<Integer, SelectedTrackFillClipHost.SlotSample> clips = new HashMap<> ();
        private final List<SelectedTrackFillClipHost.ActuatorSample> actuatorSamples = new ArrayList<> (SelectedTrackFillClipHost.ACTUATOR_COUNT);
        private final List<Integer> actuatorSelections = mutableZeroes ();
        private final List<Integer> actuatorMoves = mutableZeroes ();
        private final List<Integer> scannerMoves = new ArrayList<> ();
        private final List<String> presses = new ArrayList<> ();
        private final List<String> releases = new ArrayList<> ();
        private final List<ClipLaunchPolicy> launchPolicies = new ArrayList<> ();
        private final List<ClipReleaseTrigger> releaseTriggers = new ArrayList<> ();

        private SelectedTrackFillClipHost.SelectedTrackSample selected = new SelectedTrackFillClipHost.SelectedTrackSample ("", false);
        private String scannerTrackId = "";
        private int scannerSceneCount;
        private int scannerStart;
        private int remainingReleaseFailures;
        private boolean automaticallyParkActuators = true;


        private FakeAdapter ()
        {
            for (int index = 0; index < SelectedTrackFillClipHost.ACTUATOR_COUNT; index++)
                this.actuatorSamples.add (new SelectedTrackFillClipHost.ActuatorSample ("", false, false, -1, "", false, false));
        }


        private void selectTrack (final String trackId, final int sceneCount)
        {
            this.selected = new SelectedTrackFillClipHost.SelectedTrackSample (trackId, true);
            this.scannerSceneCount = sceneCount;
        }


        private void putClip (final int sceneIndex, final String name)
        {
            this.clips.put (Integer.valueOf (sceneIndex), new SelectedTrackFillClipHost.SlotSample (sceneIndex, name, true, true));
        }


        private void clearClips ()
        {
            this.clips.clear ();
        }


        private void setActuatorSample (final int index, final SelectedTrackFillClipHost.ActuatorSample sample)
        {
            this.actuatorSamples.set (index, sample);
        }


        /** {@inheritDoc} */
        @Override
        public SelectedTrackFillClipHost.SelectedTrackSample selectedTrack ()
        {
            return this.selected;
        }


        /** {@inheritDoc} */
        @Override
        public boolean selectScannerTrack (final String expectedTrackId)
        {
            if (!this.selected.exists () || !expectedTrackId.equals (this.selected.trackId ()))
                return false;
            this.scannerTrackId = expectedTrackId;
            return true;
        }


        /** {@inheritDoc} */
        @Override
        public void moveScanner (final int sceneStart)
        {
            this.scannerStart = sceneStart;
            this.scannerMoves.add (Integer.valueOf (sceneStart));
        }


        /** {@inheritDoc} */
        @Override
        public SelectedTrackFillClipHost.ScannerSample scannerSample ()
        {
            final List<SelectedTrackFillClipHost.SlotSample> slots = new ArrayList<> (SelectedTrackFillClipHost.SCANNER_PAGE_SIZE);
            for (int index = 0; index < SelectedTrackFillClipHost.SCANNER_PAGE_SIZE; index++)
            {
                final int scene = this.scannerStart + index;
                slots.add (this.clips.getOrDefault (Integer.valueOf (scene), new SelectedTrackFillClipHost.SlotSample (scene, "", scene < this.scannerSceneCount, false)));
            }
            return new SelectedTrackFillClipHost.ScannerSample (this.scannerTrackId, !this.scannerTrackId.isEmpty (), this.scannerSceneCount, this.scannerStart, slots);
        }


        /** {@inheritDoc} */
        @Override
        public boolean selectActuatorTrack (final int actuatorIndex, final String expectedTrackId)
        {
            if (!this.selected.exists () || !expectedTrackId.equals (this.selected.trackId ()))
                return false;
            increment (this.actuatorSelections, actuatorIndex);
            if (this.automaticallyParkActuators)
            {
                final SelectedTrackFillClipHost.ActuatorSample old = this.actuatorSamples.get (actuatorIndex);
                this.actuatorSamples.set (actuatorIndex, new SelectedTrackFillClipHost.ActuatorSample (expectedTrackId, true, true, old.sceneIndex (), old.name (), old.slotExists (), old.hasContent ()));
            }
            return true;
        }


        /** {@inheritDoc} */
        @Override
        public void moveActuator (final int actuatorIndex, final int sceneIndex)
        {
            increment (this.actuatorMoves, actuatorIndex);
            if (!this.automaticallyParkActuators)
                return;

            final SelectedTrackFillClipHost.SlotSample slot = this.clips.get (Integer.valueOf (sceneIndex));
            final SelectedTrackFillClipHost.ActuatorSample old = this.actuatorSamples.get (actuatorIndex);
            this.actuatorSamples.set (
                actuatorIndex,
                slot == null ? new SelectedTrackFillClipHost.ActuatorSample (old.trackId (), true, old.pinned (), sceneIndex, "", true, false) : new SelectedTrackFillClipHost.ActuatorSample (old.trackId (), true, old.pinned (), sceneIndex, slot.name (), true, true));
        }


        /** {@inheritDoc} */
        @Override
        public SelectedTrackFillClipHost.ActuatorSample actuatorSample (final int actuatorIndex)
        {
            return this.actuatorSamples.get (actuatorIndex);
        }


        /** {@inheritDoc} */
        @Override
        public void pressActuator (final int actuatorIndex, final ClipLaunchPolicy launchPolicy)
        {
            this.presses.add (coordinate (this.actuatorSamples.get (actuatorIndex)));
            this.launchPolicies.add (launchPolicy);
        }


        /** {@inheritDoc} */
        @Override
        public void releaseActuator (final int actuatorIndex, final ClipReleaseTrigger releaseTrigger)
        {
            if (this.remainingReleaseFailures > 0)
            {
                this.remainingReleaseFailures--;
                throw new IllegalStateException ("release failed");
            }
            this.releases.add (coordinate (this.actuatorSamples.get (actuatorIndex)));
            this.releaseTriggers.add (releaseTrigger);
        }


        private static List<Integer> mutableZeroes ()
        {
            final List<Integer> values = new ArrayList<> (SelectedTrackFillClipHost.ACTUATOR_COUNT);
            for (int index = 0; index < SelectedTrackFillClipHost.ACTUATOR_COUNT; index++)
                values.add (Integer.valueOf (0));
            return values;
        }


        private static void increment (final List<Integer> values, final int index)
        {
            values.set (index, Integer.valueOf (values.get (index).intValue () + 1));
        }


        private static String coordinate (final SelectedTrackFillClipHost.ActuatorSample sample)
        {
            return sample.trackId () + ":" + sample.sceneIndex ();
        }
    }
}
