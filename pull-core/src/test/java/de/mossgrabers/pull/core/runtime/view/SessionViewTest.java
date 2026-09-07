// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.effect.SessionActionEffect.Action;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.view.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Drives the production gesture router; host changes happen only when the fixture advances them. */
class SessionViewTest
{
    private static final ControlId PAD = PushControlIds.pad (57);
    private static final ControlId SCENE = PushControlIds.button ("SCENE1");
    private static final RgbColor BLUE = new RgbColor (0, 85, 255);

    @ParameterizedTest
    @ValueSource (ints = { 4, 8 })
    void everyPadKeepsItsCapturedLaunchLaneAcrossSelectedTrackAndModifierChanges (final int rows)
    {
        for (final boolean alternate: List.of (false, true))
        {
            final Fixture f = new Fixture (rows);
            for (int row = 0; row < rows; row++)
            {
                for (int column = 0; column < 8; column++)
                {
                    final ControlId control = PushControlIds.pad ((7 - row) * 8 + column + 1);
                    f.modifier ("SHIFT", alternate);
                    final var target = f.target (column, row);
                    assertEquals (List.of (new SessionActionEffect (target, alternate ? Action.LAUNCH_ALT : Action.LAUNCH)), f.edge (control, true));
                    final var before = f.view.render (f.snapshot ());
                    f.selectedGeneration++;
                    f.modifier ("SHIFT", !alternate);
                    assertEquals (List.of (new SessionActionEffect (target, alternate ? Action.RELEASE_ALT : Action.RELEASE)), f.edge (control, false));
                    assertEquals (before, f.view.render (f.snapshot ()), "launch submission and unrelated selection do not invent playback feedback");
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource (ints = { 4, 8 })
    void cancellationReleasesTheOldLocationOnceAndNeverRevivesItsPhysicalTail (final int rows)
    {
        for (final ControlId control: List.of (PAD, SCENE))
        {
            final Fixture f = new Fixture (rows);
            final var target = f.target (control.equals (PAD) ? 0 : -1, 0);
            f.edge (control, true);
            f.sceneOffset += rows;
            f.generation++;
            assertEquals (List.of (new SessionActionEffect (target, Action.RELEASE)), f.tick ());
            f.sceneOffset -= rows;
            f.generation++;
            assertTrue (f.edge (control, false).isEmpty ());
            assertTrue (f.edge (control, false).isEmpty ());
            assertTrue (f.edge (control, true).contains (new SessionActionEffect (f.target (control.equals (PAD) ? 0 : -1, 0), Action.LAUNCH)));
        }
    }

    @Test
    void viewDepartureAndProjectLossCancelCapturedLaunches ()
    {
        final Fixture f = new Fixture (8);
        final var target = f.target (0, 0);
        f.edge (PAD, true);
        final var empty = CompiledWorkspace.compile ("empty", List.of ());
        assertEquals (List.of (new SessionActionEffect (target, Action.RELEASE)), f.workspace.activate (empty, f.snapshot ()).effects ());
        assertTrue (f.edge (PAD, false).isEmpty ());
        final Fixture project = new Fixture (8);
        project.edge (PAD, true);
        project.project = "different-project";
        assertEquals (List.of (new SessionActionEffect (target, Action.RELEASE)), project.tick ());
    }

    @ParameterizedTest
    @ValueSource (strings = { "SELECT", "DELETE", "DUPLICATE", "STOP_CLIP", "BROWSE" })
    void padModifiersConsumeTheirOwnButtonAndDoNotCreateOrphanReleases (final String modifier)
    {
        final Fixture f = new Fixture (8);
        f.modifier (modifier, true);
        final var effects = f.edge (PAD, true);
        assertEquals (new ConsumeControllerButtonEffect (PushControlIds.button (modifier)), effects.getFirst ());
        final CoreEffect expected = switch (modifier)
        {
            case "SELECT" -> new SessionActionEffect (f.target (0, 0), Action.SELECT);
            case "DELETE" -> new SessionActionEffect (f.target (0, 0), Action.REMOVE);
            case "BROWSE" -> new SessionActionEffect (f.target (0, 0), Action.BROWSE);
            case "STOP_CLIP" -> new StopSessionTrackEffect (f.generation, f.shape, 0, "track-0", false);
            default -> null;
        };
        assertEquals (expected == null ? 1 : 2, effects.size ());
        if (expected != null) assertEquals (expected, effects.getLast ());
        assertTrue (f.edge (PAD, false).isEmpty ());
        if (modifier.equals ("STOP_CLIP")) assertTrue (f.edge (PushControlIds.button (modifier), false).isEmpty ());
    }

    @Test
    void clipboardSurvivesDuplicateReleaseWithinItsWindowButCannotFollowReboundProxies ()
    {
        final Fixture f = new Fixture (8);
        final var source = f.target (0, 0);
        f.modifier ("DUPLICATE", true);
        f.edge (PAD, true);
        f.edge (PAD, false);
        f.modifier ("DUPLICATE", false);
        f.content = false;
        f.modifier ("DUPLICATE", true);
        assertEquals (List.of (new ConsumeControllerButtonEffect (PushControlIds.button ("DUPLICATE")), new CopySessionClipEffect (source, f.target (1, 0))), f.edge (PushControlIds.pad (58), true));
        f.edge (PushControlIds.pad (58), false);
        f.sceneOffset = 8;
        f.generation++;
        f.tick ();
        assertEquals (List.of (new ConsumeControllerButtonEffect (PushControlIds.button ("DUPLICATE"))), f.edge (PushControlIds.pad (58), true));
    }

    @ParameterizedTest
    @ValueSource (strings = { "SELECT", "DELETE", "DUPLICATE", "SHIFT", "" })
    void scenesPreserveSelectionNotificationAndMatchedAlternateRelease (final String modifier)
    {
        final Fixture f = new Fixture (4);
        if (!modifier.isEmpty ()) f.modifier (modifier, true);
        final var down = f.edge (SCENE, true);
        final var target = f.target (-1, 0);
        if (modifier.equals ("DELETE") || modifier.equals ("DUPLICATE"))
            assertEquals (List.of (new ConsumeControllerButtonEffect (PushControlIds.button (modifier)), new SessionActionEffect (target, modifier.equals ("DELETE") ? Action.REMOVE : Action.DUPLICATE)), down);
        else
        {
            assertEquals (new SessionActionEffect (target, Action.SELECT), down.getFirst ());
            assertEquals (new ShowHostNotificationEffect ("Scene 0"), down.get (1));
        }
        f.modifier ("SHIFT", !modifier.equals ("SHIFT"));
        assertEquals (modifier.isEmpty () || modifier.equals ("SHIFT") ? List.of (new SessionActionEffect (target, modifier.equals ("SHIFT") ? Action.RELEASE_ALT : Action.RELEASE)) : List.of (), f.edge (SCENE, false));
        assertTrue (f.edge (PushControlIds.button ("SCENE5"), true).isEmpty (), "VS lower scenes are not claimed");
    }

    @ParameterizedTest
    @ValueSource (ints = { 0, 1, 2 })
    void armedEmptySlotsWaitForReadbackBeforeDependentLaunchAndHonorNoAction (final int action)
    {
        final Fixture f = new Fixture (8);
        f.armed = true;
        f.content = false;
        f.slotExists = false;
        f.recordAction = action;
        final var target = f.target (0, 0);
        final var down = f.edge (PAD, true);
        assertEquals (action == 2 ? List.of () : List.of (action == 0 ? new SessionActionEffect (target, Action.START_RECORDING) : new CreateSessionClipEffect (target, 16)), down);
        assertTrue (f.tick ().isEmpty ());
        f.slotExists = true;
        f.content = true;
        f.recording = action == 0;
        final var later = f.tick ();
        if (action == 0) assertEquals (List.of (new SessionActionEffect (target, Action.LAUNCH)), later);
        if (action == 1) assertEquals (List.of (new SessionActionEffect (target, Action.SELECT), new SessionActionEffect (target, Action.LAUNCH), new SetTransportStateEffect (TransportState.LAUNCHER_OVERDUB, true)), later);
        if (action == 2) assertTrue (later.isEmpty ());
        assertEquals (action == 2 ? List.of () : List.of (new SessionActionEffect (target, Action.RELEASE)), f.edge (PAD, false));
    }

    @ParameterizedTest
    @ValueSource (ints = { 0, 1 })
    void cancelledRecordingIntentCannotLaunchAfterLateHostAcknowledgement (final int action)
    {
        final Fixture f = new Fixture (8);
        f.armed = true;
        f.content = false;
        f.recordAction = action;
        f.edge (PAD, true);
        assertTrue (f.workspace.activate (CompiledWorkspace.compile ("away", List.of ()), f.snapshot ()).effects ().isEmpty ());
        assertTrue (f.edge (PAD, false).isEmpty ());
        f.content = true;
        f.recording = true;
        assertTrue (f.tick ().isEmpty ());
        assertFalse (f.view.executionRequirements ().ticksRequested ());
    }

    @ParameterizedTest
    @ValueSource (ints = { 0, 1 })
    void quickTapCreateAndRecordFinishTheirMatchedLaunchAfterHostReadback (final int action)
    {
        final Fixture f = new Fixture (8);
        f.armed = true;
        f.content = false;
        f.recordAction = action;
        final var target = f.target (0, 0);
        f.edge (PAD, true);
        assertTrue (f.edge (PAD, false).isEmpty ());
        assertTrue (f.view.executionRequirements ().ticksRequested ());
        assertTrue (f.view.executionRequirements ().replacementBlocked ());
        assertTrue (f.edge (PAD, true).isEmpty (), "a fresh press cannot overwrite the pending host operation");
        assertTrue (f.edge (PAD, false).isEmpty ());
        assertTrue (f.tick ().isEmpty ());
        f.content = true;
        f.recording = action == 0;
        final var result = f.tick ();
        assertTrue (result.contains (new SessionActionEffect (target, Action.LAUNCH)));
        assertEquals (new SessionActionEffect (target, Action.RELEASE), result.getLast ());
        assertTrue (result.indexOf (new SessionActionEffect (target, Action.LAUNCH)) < result.size () - 1);
        assertTrue (f.tick ().isEmpty ());
        assertFalse (f.view.executionRequirements ().ticksRequested ());
        assertFalse (f.view.executionRequirements ().replacementBlocked ());
    }

    @Test
    void selectionPreferenceAndModifierPrecedenceRemainExplicit ()
    {
        final Fixture f = new Fixture (8);
        f.selectOnLaunch = true;
        assertEquals (List.of (new SessionActionEffect (f.target (0, 0), Action.SELECT), new SessionActionEffect (f.target (0, 0), Action.LAUNCH)), f.edge (PAD, true));
        f.edge (PAD, false);
        f.modifier ("DELETE", true);
        f.modifier ("SELECT", true);
        assertEquals (List.of (new ConsumeControllerButtonEffect (PushControlIds.button ("SELECT")), new SessionActionEffect (f.target (0, 0), Action.SELECT)), f.edge (PAD, true));
    }

    @ParameterizedTest
    @ValueSource (ints = { 4, 8 })
    void birdseyeUsesTheInstalledWindowAndNeverLaunches (final int rows)
    {
        final Fixture f = new Fixture (rows);
        f.modifier ("SHIFT", true);
        f.modifier ("SELECT", true);
        final ControlId control = PushControlIds.pad (50);
        assertEquals (List.of (new ConsumeControllerButtonEffect (PushControlIds.button ("SELECT")), new SetSessionBankPositionEffect (f.generation, f.shape, 8, rows)), f.edge (control, true));
        assertTrue (f.edge (control, false).isEmpty ());
        assertEquals (new RgbColor (0, 89, 0), f.view.render (f.snapshot ()).lights ().get (PAD));
        assertEquals (new RgbColor (89, 29, 0), f.view.render (f.snapshot ()).lights ().get (control));
    }

    @ParameterizedTest
    @ValueSource (strings = { "PAGE_RIGHT", "OCTAVE_DOWN" })
    void navigationSelectsOnlyAfterTheRequestedWindowIsObserved (final String button)
    {
        final Fixture f = new Fixture (8);
        final boolean scene = button.equals ("OCTAVE_DOWN");
        final ControlId control = PushControlIds.button (button);
        assertEquals (List.of (new SetSessionBankPositionEffect (f.generation, f.shape, scene ? -1 : 8, scene ? 8 : -1)), f.edge (control, true));
        assertTrue (f.edge (control, false).isEmpty ());
        assertTrue (f.tick ().isEmpty ());
        if (scene) f.sceneOffset = 8; else f.trackOffset = 8;
        f.generation++;
        assertEquals (scene ? List.of (new SessionActionEffect (f.target (-1, 0), Action.SELECT)) : List.of (new SelectSessionTrackEffect (f.generation, f.shape, 0, "track-8")), f.tick ());
        assertTrue (f.tick ().isEmpty ());
    }

    @ParameterizedTest
    @ValueSource (booleans = { false, true })
    void legacyParameterPageKeepsHorizontalOwnershipWhileVerticalSessionArrowsMoveAndRenderInCore (final boolean shift)
    {
        final Fixture f = new Fixture (8, true);
        f.modifier ("SHIFT", shift);
        final var routes = f.workspace.activate (f.snapshot ()).desiredInputRoutes ();
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), routes.mode (PushControlIds.button ("ARROW_DOWN"), InputKind.BUTTON));
        assertEquals (Optional.empty (), routes.mode (PushControlIds.button ("ARROW_LEFT"), InputKind.BUTTON));
        assertEquals (List.of (new SetSessionBankPositionEffect (f.generation, f.shape, -1, shift ? 8 : 1)), f.edge (PushControlIds.button ("ARROW_DOWN"), true));
        assertTrue (f.edge (PushControlIds.button ("ARROW_DOWN"), false).isEmpty ());
        f.sceneOffset = shift ? 8 : 1;
        f.generation++;
        assertEquals (shift ? List.of (new SessionActionEffect (f.target (-1, 0), Action.SELECT)) : List.of (), f.tick ());
        assertEquals (new RgbColor (30, 30, 30), f.workspace.activate (f.snapshot ()).desiredOutput ().lights ().get (PushControlIds.button ("ARROW_DOWN")));
    }

    @Test
    void conflictingNavigationAndUnavailableWindowsCannotReviveASelectionIntent ()
    {
        final Fixture f = new Fixture (8);
        f.edge (PushControlIds.button ("OCTAVE_DOWN"), true);
        f.edge (PushControlIds.button ("OCTAVE_DOWN"), false);
        f.sceneOffset = 16;
        f.generation++;
        assertTrue (f.tick ().isEmpty ());
        f.sceneOffset = 8;
        f.generation++;
        assertTrue (f.tick ().isEmpty ());
        f.aligned = false;
        assertTrue (f.edge (PAD, true).isEmpty ());
        assertLight (f, new RgbColor (0, 0, 0), null);
    }

    @Test
    void observedClipStateAloneControlsTheEntireBlinkAndEmptyStripePalette ()
    {
        final Fixture f = new Fixture (8);
        final RgbColor green = new RgbColor (0, 89, 0);
        f.selectedSlot = true;
        assertLight (f, BLUE, new LightBlink (new RgbColor (255, 255, 255), false));
        f.muted = true;
        assertLight (f, new RgbColor (30, 30, 30), null);
        f.playing = true;
        assertLight (f, BLUE, new LightBlink (green, false));
        f.playbackQueued = true;
        assertLight (f, BLUE, new LightBlink (green, true));
        f.playbackQueued = false;
        f.stopQueued = true;
        assertLight (f, BLUE, new LightBlink (green, true));
        f.recording = true;
        assertLight (f, BLUE, new LightBlink (new RgbColor (114, 17, 106), false));
        f.recordingQueued = true;
        assertLight (f, new RgbColor (114, 17, 106), new LightBlink (new RgbColor (0, 0, 0), true));
        final Fixture empty = new Fixture (8);
        empty.content = false;
        empty.armed = true;
        assertLight (empty, new RgbColor (89, 29, 0), null);
        empty.stripe = false;
        assertLight (empty, new RgbColor (0, 0, 0), null);
    }

    private static void assertLight (final Fixture fixture, final RgbColor color, final LightBlink blink)
    {
        final var output = fixture.view.render (fixture.snapshot ());
        assertEquals (color, output.lights ().get (PAD));
        assertEquals (blink, output.lightBlinks ().get (PAD));
    }

    private static final class Fixture
    {
        final SessionBankShape shape;
        final SessionView view;
        final RoutedWorkspace workspace;
        final Set<ControlId> pressed = new HashSet<> ();
        long sequence = 1;
        long generation = 1;
        long selectedGeneration = 1;
        String project = "project";
        int trackOffset;
        int sceneOffset;
        int recordAction;
        boolean content = true;
        boolean slotExists = true;
        boolean armed;
        boolean selectOnLaunch;
        boolean stripe = true;
        boolean selectedSlot;
        boolean muted;
        boolean playing;
        boolean recording;
        boolean playbackQueued;
        boolean recordingQueued;
        boolean stopQueued;
        boolean aligned = true;

        Fixture (final int rows) { this (rows, false); }
        Fixture (final int rows, final boolean legacy)
        {
            this.shape = new SessionBankShape (8, rows);
            this.view = rows == 8 ? SessionView.full () : SessionView.upper (true);
            this.workspace = new RoutedWorkspace (CompiledWorkspace.compile ("Session", this.shape, legacy ? List.of (this.view, this.view.legacyPageNavigation ()) : List.of (this.view)));
            this.workspace.start (this.snapshot ());
        }
        void modifier (final String name, final boolean down) { this.edge (PushControlIds.button (name), down); }
        List<CoreEffect> edge (final ControlId control, final boolean down)
        {
            if (down) this.pressed.add (control); else this.pressed.remove (control);
            this.sequence++;
            return this.workspace.handle (new ControllerInputEvent (this.sequence, this.sequence, control,
                control.value ().startsWith ("push.pad.") ? InputKind.PAD : InputKind.BUTTON, down ? InputPhase.BEGIN : InputPhase.END, down ? 100 : 0), this.snapshot ()).effects ();
        }
        List<CoreEffect> tick ()
        {
            this.sequence++;
            return this.workspace.handle (new ControllerTickEvent (this.sequence, this.sequence), this.snapshot ()).effects ();
        }
        SessionLocation target (final int track, final int row)
        {
            return new SessionLocation (this.project, this.generation, this.shape, track, track < 0 ? "" : "track-" + (this.trackOffset + track), this.sceneOffset + row);
        }
        ControllerSnapshot snapshot ()
        {
            final List<SessionTrackSnapshot> tracks = new ArrayList<> ();
            final List<SessionSlotSnapshot> slots = new ArrayList<> ();
            final List<SessionSceneSnapshot> scenes = new ArrayList<> ();
            for (int track = 0; track < 8; track++)
            {
                tracks.add (new SessionTrackSnapshot ("track-" + (this.trackOffset + track), this.trackOffset + track, "Track " + track, true, false, true, this.armed, false, false, false, SessionTrackType.INSTRUMENT, BLUE));
                for (int row = 0; row < this.shape.scenes (); row++)
                    slots.add (this.slotExists ? new SessionSlotSnapshot (true, this.sceneOffset + row, "Clip", this.content, this.selectedSlot, this.muted, this.playing, this.recording, this.playbackQueued, this.recordingQueued, this.stopQueued, BLUE) : SessionSlotSnapshot.empty ());
            }
            for (int row = 0; row < this.shape.scenes (); row++) scenes.add (new SessionSceneSnapshot (true, this.sceneOffset + row, "Scene " + row, row == 0, BLUE));
            final var extent = new BankNavigationSnapshot (128, true, true, true, true);
            final var bank = new SessionBankSnapshot (this.generation, this.shape, this.trackOffset, this.sceneOffset, tracks, new SessionClipWindowSnapshot (this.shape, this.aligned, slots, scenes, extent, extent));
            final var e = ControllerBridgeSnapshot.empty ();
            final var selected = new SelectedTrackSnapshot (this.selectedGeneration, "selected-" + this.selectedGeneration, "Selected", 0, "INSTRUMENT", true, false, false, true, false, true, false, TrackMonitorMode.OFF, false, false, false, false, .5, .5, BLUE);
            final var settings = new ControllerSettingsSnapshot (true, false, "VOLUME", 0, CursorSendBankSnapshot.empty (), false, 127,
                new SessionSettingsSnapshot (true, this.selectOnLaunch, this.recordAction, 16, this.stripe));
            final var bridge = new ControllerBridgeSnapshot (e.transport (), selected, bank, e.layout (), e.noteView (), e.noteRepeat (), e.drum (), e.parameters (), e.controllerMappingFeedback (), e.master (),
                new ProjectSnapshot (true, this.project, "Project", true, false, false, false), e.automation (), e.encoderConfiguration (), e.currentTrackBank (), e.transportSettings (), settings);
            return new ControllerSnapshot (this.sequence, this.sequence, new ShellCapabilities (Map.of ()), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), this.pressed, Set.of ());
        }
    }
}
