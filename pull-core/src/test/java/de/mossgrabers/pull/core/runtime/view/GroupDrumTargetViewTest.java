package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.event.ControllerTickEvent;
import de.mossgrabers.pull.core.api.output.RgbColor;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GroupDrumTargetViewTest
{
    private static final SelectedTrackSnapshot GROUP = track (1, "group", true);
    private static final SelectedTrackSnapshot DRUMS = track (3, "drums", false);
    private static final List<GroupDrumScanSnapshot.Child> CHILDREN = List.of (
        new GroupDrumScanSnapshot.Child ("drums", "Drums", true), new GroupDrumScanSnapshot.Child ("samples", "Samples", true));

    @Test void scansInOrderWaitsForHostAndKeepsTheFirstKit ()
    {
        final var view = new GroupDrumTargetView ();
        view.start (snapshot (scan (1, "", false, false), false));
        assertEquals ("drums", view.desiredDrumTarget ().trackId ());
        view.handle (new ControllerTickEvent (1, 1), snapshot (scan (1, "drums", false, false), false));
        assertEquals ("drums", view.desiredDrumTarget ().trackId (), "pending discovery is not an empty track");
        view.handle (new ControllerTickEvent (2, 2), snapshot (scan (1, "drums", true, true), true));
        assertEquals ("drums", view.desiredDrumTarget ().trackId (), "do not composite the Samples sibling");
        view.handle (new ControllerTickEvent (3, 3), snapshot (scan (1, "drums", true, false), false));
        assertEquals ("samples", view.desiredDrumTarget ().trackId ());
        view.handle (new ControllerTickEvent (4, 4), snapshot (scan (2, "", false, false), false));
        assertEquals ("drums", view.desiredDrumTarget ().trackId (), "a topology revision reconsiders earlier children");
    }

    @Test void groupSelectionKeepsMixerOwnerWhileMusicUsesAcknowledgedChild ()
    {
        final var pending = snapshot (scan (1, "drums", false, false), false);
        assertFalse (ResolvedNoteViewer.resolve (pending, true).noteInputRoute ().active ());
        final var ready = snapshot (scan (1, "drums", true, true), true);
        assertEquals (DRUMS, ResolvedNoteViewer.target (ready));
        final var music = ResolvedNoteViewer.resolve (ready, true);
        assertEquals (ControllerNoteView.DRUM_PAD, music.layout ());
        assertEquals (DesiredNoteInputRoute.selectedTrack (3, "drums"), music.noteInputRoute ());
        assertFalse (ResolvedNoteViewer.target (track (2, "other-group", true), ready.bridge ().noteView ()).exists (), "old child readback cannot authorize a new group");
    }

    @Test void octaveAndFillRequestsUseTheChildWhileMixerSelectionStaysOnGroup ()
    {
        final var ready = snapshot (scan (1, "drums", true, true), true);
        final var octave = new DrumOctaveView ();
        octave.start (ready);
        final var input = new de.mossgrabers.pull.core.api.event.ControllerInputEvent (2, 2, PushControlIds.button ("OCTAVE_UP"), de.mossgrabers.pull.core.api.event.InputKind.BUTTON, de.mossgrabers.pull.core.api.event.InputPhase.BEGIN, 127);
        assertEquals (List.of (new de.mossgrabers.pull.core.api.effect.SetDrumBankPositionEffect (1, "drums", 52, false)), octave.handle (input, ready));
        assertEquals (36, ready.bridge ().drum ().baseMidiNote (), "submitted octave is not host readback");
        final var fill = new DrumFillView ();
        fill.start (ready);
        assertEquals ("drums", fill.desiredClipScan ().channelId ());
        assertEquals ("group", ready.bridge ().selectedTrack ().channelId ());
    }

    @Test void completeCoreResultsPreserveTheChildRequestThroughDecorators ()
    {
        final var state = snapshot (scan (1, "", false, false), false);
        final var core = new de.mossgrabers.pull.core.runtime.PullCoreProvider ().create ();
        final var expected = new DesiredDrumTarget (1, "group", 1, "drums");
        assertEquals (expected, core.start (state, Optional.empty ()).desiredBridgeSubscriptions ().drumTarget ());
        assertEquals (expected, core.handle (new ControllerTickEvent (1, 1), state).desiredBridgeSubscriptions ().drumTarget ());
        final var touch = new de.mossgrabers.pull.core.api.event.TouchInputEvent (2, 2, PushControlIds.continuous ("KNOB1"), true);
        assertEquals (expected, core.handle (touch, state).desiredBridgeSubscriptions ().drumTarget ());
    }

    private static GroupDrumScanSnapshot scan (long revision, String current, boolean complete, boolean drums)
    {
        return new GroupDrumScanSnapshot (1, "group", revision, CHILDREN, false, current, complete, drums);
    }
    private static ControllerSnapshot snapshot (GroupDrumScanSnapshot scan, boolean ready)
    {
        final var note = new NoteViewSnapshot (3, "drums", 0, ControllerNoteView.NONE, ready, false, ready ? DRUMS : SelectedTrackSnapshot.empty (), scan);
        final var layout = new ControllerLayoutSnapshot (1, "DRUM_PAD", "TRACK", true, true, 36, GridPressureConfiguration.OFF);
        final var pad = new DrumPadSnapshot (0, 36, "kick", true, "Kick", new RgbColor (100, 50, 0), true, true, false, false, false, 0.5, 0.5, 0);
        final var drum = ready ? new DrumContextSnapshot (1, 3, "drums", "kit", true, true, 36, List.of (pad)) : DrumContextSnapshot.empty ();
        final var bridge = new ControllerBridgeSnapshot (TransportSnapshot.empty (), GROUP, layout, note, NoteRepeatSnapshot.empty (), drum, ParameterBridgeSnapshot.empty (), ControllerMappingFeedbackSnapshot.empty (), MasterSnapshot.empty (), ProjectSnapshot.empty ());
        return new ControllerSnapshot (1, 1, ShellCapabilities.empty (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), Set.of (), Set.of ());
    }
    private static SelectedTrackSnapshot track (long generation, String id, boolean group)
    {
        return new SelectedTrackSnapshot (generation, id, id, 0, group ? "Group" : "Instrument", true, group, false, !group, false, true, false, TrackMonitorMode.AUTO, false, false, false, false, 0.5, 0.5, new RgbColor (1, 2, 3));
    }
}
