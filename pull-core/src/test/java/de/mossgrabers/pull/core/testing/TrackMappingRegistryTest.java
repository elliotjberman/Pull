// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.SetControllerMappingStorageEffect;
import de.mossgrabers.pull.core.api.event.ControllerTickEvent;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.view.DrumControlPadView;
import de.mossgrabers.pull.core.runtime.view.TrackMappingRegistry;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;


/** Keep registry submission, later host readback, selected ownership, and native-target feedback separate. */
class TrackMappingRegistryTest
{
    private static final String DOCUMENT = uuid (1000);
    private static final String FIRST = uuid (1);
    private static final String SECOND = uuid (2);
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor RED = new RgbColor (255, 0, 0);
    private static final RgbColor AMBER = new RgbColor (255, 100, 0);


    @Test
    void allocationWaitsForHostStorageReadbackAndDoesNotResubmitAnUnchangedRequest ()
    {
        final DrumControlPadView view = new DrumControlPadView ();
        final ControllerSnapshot initial = snapshot (FIRST, 7, "Original", 0, 1, "", feedback (0, 0.8));
        view.start (initial);
        final SetControllerMappingStorageEffect submitted = assertInstanceOf (SetControllerMappingStorageEffect.class, view.handle (new SnapshotChangedEvent (1, 1), initial).getFirst ());

        assertEquals (new ControllerMappingContext (7, FIRST, 1, DOCUMENT), submitted.context ());
        assertEquals ("", submitted.expectedValue ());
        assertEquals (List.of (FIRST), TrackMappingRegistry.parse (DOCUMENT, submitted.value ()).orElseThrow ().trackIds ());
        assertTrue (view.handle (new ControllerTickEvent (2, 2), initial).isEmpty ());
        assertTrue (view.render (initial).controllerMappings ().bindings ().isEmpty (), "submitted storage cannot activate a mapping");
        assertEquals (OFF, view.render (initial).lights ().get (CoreControls.DRUM_CONTROL_PADS.getFirst ()));

        final ControllerSnapshot acknowledged = snapshot (FIRST, 7, "Original", 0, 2, submitted.value (), feedback (0, 0.8));
        assertTrue (view.handle (new SnapshotChangedEvent (3, 3), acknowledged).isEmpty ());
        final ControllerMappingBinding binding = firstBinding (view, acknowledged);
        assertEquals (CoreControllerMappings.trackBank (0).getFirst (), binding.mappingId ());
        assertEquals (new ControllerMappingContext (7, FIRST, 2, DOCUMENT), binding.context ());
        assertEquals (ControllerMappingValue.MINIMUM, binding.value ());
        assertEquals (RED, view.render (acknowledged).lights ().get (binding.physicalControl ()));
    }


    @Test
    void selectedTracksUseIndependentBanksAcrossRenameReorderAndCoreReplacement ()
    {
        final String persisted = new TrackMappingRegistry (DOCUMENT, List.of (FIRST, SECOND)).encode ();
        final Map<ControllerMappingId, ControllerMappingTarget> feedback = new LinkedHashMap<> (feedback (0, 0.8));
        feedback.putAll (feedback (1, 0.2));
        final DrumControlPadView view = new DrumControlPadView ();
        final ControllerSnapshot first = snapshot (FIRST, 3, "Drums A", 0, 4, persisted, feedback);
        final ControllerSnapshot second = snapshot (SECOND, 4, "Drums B", 1, 4, persisted, feedback);
        assertEquals (ControllerMappingValue.MINIMUM, firstBinding (view, first).value ());
        assertEquals (CoreControllerMappings.trackBank (0).getFirst (), firstBinding (view, first).mappingId ());
        assertEquals (RED, view.render (first).lights ().get (CoreControls.DRUM_CONTROL_PADS.getFirst ()));
        assertEquals (ControllerMappingValue.MAXIMUM, firstBinding (view, second).value ());
        assertEquals (CoreControllerMappings.trackBank (1).getFirst (), firstBinding (view, second).mappingId ());
        assertEquals (OFF, view.render (second).lights ().get (CoreControls.DRUM_CONTROL_PADS.getFirst ()));

        final ControllerSnapshot renamed = snapshot (FIRST, 5, "Renamed", 12, 4, persisted, feedback);
        assertEquals (firstBinding (view, first).mappingId (), firstBinding (view, renamed).mappingId ());
        assertEquals (5, firstBinding (view, renamed).context ().targetGeneration ());
        final DrumControlPadView replacement = new DrumControlPadView ();
        replacement.start (second);
        assertEquals (view.render (second), replacement.render (second));
        assertTrue (replacement.handle (new SnapshotChangedEvent (1, 1), second).isEmpty ());
    }


    @Test
    void pendingAllocationRechecksSelectionAndStorageRevision ()
    {
        final DrumControlPadView view = new DrumControlPadView ();
        final String firstRegistered = new TrackMappingRegistry (DOCUMENT, List.of (FIRST)).encode ();
        final ControllerSnapshot secondSelected = snapshot (SECOND, 8, "Duplicate", 1, 3, firstRegistered, feedback (1, 0.2));
        final SetControllerMappingStorageEffect request = assertInstanceOf (SetControllerMappingStorageEffect.class, view.handle (new SnapshotChangedEvent (1, 1), secondSelected).getFirst ());
        assertEquals (List.of (FIRST, SECOND), TrackMappingRegistry.parse (DOCUMENT, request.value ()).orElseThrow ().trackIds ());
        assertTrue (view.render (secondSelected).controllerMappings ().bindings ().isEmpty ());

        final ControllerSnapshot changedRevision = snapshot (SECOND, 8, "Duplicate", 1, 4, firstRegistered, feedback (1, 0.2));
        final SetControllerMappingStorageEffect revised = assertInstanceOf (SetControllerMappingStorageEffect.class, view.handle (new SnapshotChangedEvent (2, 2), changedRevision).getFirst ());
        assertEquals (4, revised.context ().storageRevision ());
        final ControllerSnapshot backToFirst = snapshot (FIRST, 9, "Original", 0, 4, firstRegistered, feedback (0, 0.8));
        assertTrue (view.handle (new SnapshotChangedEvent (3, 3), backToFirst).isEmpty ());
        assertEquals (CoreControllerMappings.trackBank (0).getFirst (), firstBinding (view, backToFirst).mappingId ());

        final TrackMappingRegistry afterReadback = TrackMappingRegistry.parse (DOCUMENT, request.value ()).orElseThrow ();
        final TrackMappingRegistry afterDeleteAndNewTrack = afterReadback.append (uuid (3));
        assertEquals (0, afterDeleteAndNewTrack.bank (FIRST), "restoring an original UUID recovers its bank");
        assertEquals (1, afterDeleteAndNewTrack.bank (SECOND), "deletion never returns a historical bank to the pool");
        assertEquals (2, afterDeleteAndNewTrack.bank (uuid (3)));
    }


    @Test
    void fullOrInvalidRegistryFailsClosedWithoutOverwritingStorage ()
    {
        final TrackMappingRegistry full = new TrackMappingRegistry (DOCUMENT, IntStream.rangeClosed (1, CoreControllerMappings.TRACK_BANK_COUNT).mapToObj (TrackMappingRegistryTest::uuid).toList ());
        assertTrue (full.full ());
        assertThrows (IllegalArgumentException.class, () -> full.append (uuid (200)));
        assertSame (full, full.append (FIRST));
        final DrumControlPadView view = new DrumControlPadView ();
        for (final String payload : List.of (full.encode (), "broken", "v1\n" + uuid (1001) + "\n" + FIRST, "v1\n" + DOCUMENT + "\n" + FIRST + "\n" + FIRST, "v1\n" + DOCUMENT + "\nnot-a-uuid"))
        {
            final ControllerSnapshot unavailable = snapshot (uuid (200), 1, "New track", 0, 1, payload, feedback (0, 0.8));
            assertTrue (view.handle (new SnapshotChangedEvent (1, 1), unavailable).isEmpty ());
            assertTrue (view.render (unavailable).controllerMappings ().bindings ().isEmpty ());
            CoreControls.DRUM_CONTROL_PADS.forEach (pad -> assertEquals (AMBER, view.render (unavailable).lights ().get (pad)));
        }
        assertEquals (CoreControllerMappings.trackBank (0).getFirst (), firstBinding (view, snapshot (FIRST, 1, "Original", 0, 1, full.encode (), feedback (0, 0.8))).mappingId ());
    }


    @Test
    void onlyTheSelectedBanksFourFeedbackLanesMustBeReady ()
    {
        final DrumControlPadView view = new DrumControlPadView ();
        final String persisted = new TrackMappingRegistry (DOCUMENT, List.of (FIRST, SECOND)).encode ();
        final Map<ControllerMappingId, ControllerMappingTarget> selectedFeedback = new LinkedHashMap<> (feedback (1, 0.2));
        final ControllerSnapshot ready = snapshot (SECOND, 3, "Drums B", 1, 2, persisted, selectedFeedback);
        assertEquals (4, view.render (ready).controllerMappings ().bindings ().size ());
        assertTrue (view.render (snapshot (FIRST, 4, "Drums A", 0, 2, persisted, selectedFeedback)).controllerMappings ().bindings ().isEmpty ());
        selectedFeedback.remove (CoreControllerMappings.trackBank (1).getFirst ());
        final ControllerSnapshot partial = snapshot (SECOND, 3, "Drums B", 1, 2, persisted, selectedFeedback);
        assertTrue (view.render (partial).controllerMappings ().bindings ().isEmpty ());
        CoreControls.DRUM_CONTROL_PADS.forEach (pad -> assertEquals (OFF, view.render (partial).lights ().get (pad)));
    }


    private static ControllerMappingBinding firstBinding (final DrumControlPadView view, final ControllerSnapshot snapshot)
    {
        return view.render (snapshot).controllerMappings ().bindings ().stream ().filter (binding -> binding.physicalControl ().equals (CoreControls.DRUM_CONTROL_PADS.getFirst ())).findFirst ().orElseThrow ();
    }


    private static Map<ControllerMappingId, ControllerMappingTarget> feedback (final int bank, final double value)
    {
        final Map<ControllerMappingId, ControllerMappingTarget> targets = new LinkedHashMap<> ();
        CoreControllerMappings.trackBank (bank).forEach (id -> targets.put (id, new ControllerMappingTarget (true, value)));
        return targets;
    }


    private static ControllerSnapshot snapshot (final String track, final long generation, final String name, final int position, final long revision, final String persisted, final Map<ControllerMappingId, ControllerMappingTarget> targets)
    {
        final SelectedTrackSnapshot selected = new SelectedTrackSnapshot (generation, track, name, position, "INSTRUMENT", true, false, false, true, false, true, false, TrackMonitorMode.AUTO, false, false, false, false, 0.75, 0.5, OFF);
        final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (
            TransportSnapshot.empty (), selected,
            new ControllerLayoutSnapshot (1, "DRUM_PAD", "TRACK", true, true, 36, GridPressureConfiguration.OFF),
            NoteViewSnapshot.empty (), NoteRepeatSnapshot.empty (), DrumContextSnapshot.empty (), ParameterBridgeSnapshot.empty (),
            new ControllerMappingFeedbackSnapshot (true, targets, new ControllerMappingStorageSnapshot (true, revision, DOCUMENT, persisted)),
            MasterSnapshot.empty (), ProjectSnapshot.empty ());
        return new ControllerSnapshot (revision, 1, new ShellCapabilities (Map.of ()), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), Set.of (), Set.of ());
    }


    private static String uuid (final int number)
    {
        return String.format ("00000000-0000-0000-0000-%012d", number);
    }
}
