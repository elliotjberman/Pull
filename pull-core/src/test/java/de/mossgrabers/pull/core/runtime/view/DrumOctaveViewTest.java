// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.DesiredNoteInputTranslation;
import de.mossgrabers.pull.core.api.DrumContextSnapshot;
import de.mossgrabers.pull.core.api.DrumPadSnapshot;
import de.mossgrabers.pull.core.api.GridPressureConfiguration;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.MasterSnapshot;
import de.mossgrabers.pull.core.api.NoteRepeatSnapshot;
import de.mossgrabers.pull.core.api.NoteViewSnapshot;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ProjectSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.TrackMonitorMode;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetDrumBankPositionEffect;
import de.mossgrabers.pull.core.api.effect.SendNoteInputMidiEffect;
import de.mossgrabers.pull.core.api.effect.ShowHostNotificationEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerTickEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.RetainedControllerView;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class DrumOctaveViewTest
{
    private static final ControlId UP = PushControlIds.button ("OCTAVE_UP");
    private static final ControlId DOWN = PushControlIds.button ("OCTAVE_DOWN");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId PLAY_PAD = PushControlIds.pad (1);
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor ON = new RgbColor (255, 255, 255);


    @ParameterizedTest
    @ValueSource (strings = {"DRUM_PAD", "WORKSPACE"})
    void standaloneAndCompositeAcquireOctaveInputAndLightsTogether (final String layout)
    {
        final Fixture fixture = new Fixture (layout);
        final var result = CompiledWorkspace.compile ("Drum", List.of (fixture.view)).start (fixture.snapshot ());
        assertEquals (InputRouteMode.EXCLUSIVE, result.desiredInputRoutes ().modeOrNull (UP, InputKind.BUTTON));
        assertEquals (InputRouteMode.EXCLUSIVE, result.desiredInputRoutes ().modeOrNull (DOWN, InputKind.BUTTON));
        assertEquals (InputRouteMode.OBSERVE, result.desiredInputRoutes ().modeOrNull (PLAY_PAD, InputKind.PAD));
        assertEquals (InputRouteMode.OBSERVE, result.desiredInputRoutes ().modeOrNull (SHIFT, InputKind.BUTTON));
        assertEquals (Map.of (UP, ON, DOWN, ON), result.desiredOutput ().lights ());
    }


    @Test
    void movementComposesFromRetainedRequestedBaseWithoutFakingHostReadback ()
    {
        final Fixture fixture = new Fixture ("DRUM_PAD");
        fixture.press (UP);
        assertEquals (List.of (new SetDrumBankPositionEffect (1, "track-a", 52, false)), fixture.submitted);
        assertEquals (36, fixture.observedBase);
        assertEquals (36, fixture.requestedBase);
        assertEquals (Map.of (UP, ON, DOWN, ON), fixture.view.render (fixture.snapshot ()).lights ());

        fixture.applyRequests ();
        assertEquals (52, fixture.requestedBase);
        assertEquals (36, fixture.observedBase);
        assertTrue (DrumOctaveView.translation (fixture.snapshot ()).owned ());
        assertFalse (DrumOctaveView.translation (fixture.snapshot ()).allowsNotes ());
        assertEquals (Map.of (UP, OFF, DOWN, OFF), fixture.view.render (fixture.snapshot ()).lights ());

        // The shell-held requested base survives a core reload before asynchronous bank movement.
        fixture.view = new DrumOctaveView ();
        fixture.press (UP);
        assertEquals (68, ((SetDrumBankPositionEffect) fixture.submitted.get (0)).baseMidiNote ());
        fixture.applyRequests ();
        fixture.advanceHost ();
        fixture.tick ();
        assertEquals (68, fixture.observedBase);
        assertEquals (List.of (new ShowHostNotificationEffect ("Offset: 32 (Ab3)")), fixture.submitted);
        assertEquals (Map.of (UP, OFF, DOWN, OFF), fixture.view.render (fixture.snapshot ()).lights ());

        fixture.applyNativeTranslation ();
        assertTrue (DrumOctaveView.mappingApplied (fixture.snapshot ()));
        assertEquals (Map.of (UP, ON, DOWN, ON), fixture.view.render (fixture.snapshot ()).lights ());
    }


    @Test
    void onlyBeginMovesAndShiftIsCapturedAtBegin ()
    {
        final Fixture fixture = new Fixture ("WORKSPACE");
        fixture.pressed = Set.of (SHIFT);
        fixture.press (DOWN);
        assertEquals (new SetDrumBankPositionEffect (1, "track-a", 32, false), fixture.submitted.get (0));
        fixture.applyRequests ();
        fixture.pressed = Set.of ();
        fixture.event (DOWN, InputKind.BUTTON, InputPhase.LONG);
        fixture.event (DOWN, InputKind.BUTTON, InputPhase.END);
        fixture.event (SHIFT, InputKind.BUTTON, InputPhase.BEGIN);
        assertTrue (fixture.submitted.isEmpty ());
        fixture.advanceHost ();
        fixture.tick ();
        assertEquals (List.of (new ShowHostNotificationEffect ("Offset: -4 (Ab0)")), fixture.submitted);
    }


    @ParameterizedTest
    @ValueSource (ints = {4, 12, 20, 36, 84, 92, 100})
    void lightsUseTheNormalStepEvenWhenShiftAllowsASmallerMovement (final int base)
    {
        final Fixture fixture = new Fixture ("DRUM_PAD");
        fixture.base (base);
        fixture.pressed = Set.of (SHIFT);
        assertEquals (Map.of (DOWN, base >= 20 ? ON : OFF, UP, base <= 84 ? ON : OFF), fixture.view.render (fixture.snapshot ()).lights ());
        fixture.press (DOWN);
        if (base == 4)
            assertEquals (List.of (new ShowHostNotificationEffect ("Offset: -32 (E-2)")), fixture.submitted);
        else
            assertEquals (base - 4, ((SetDrumBankPositionEffect) fixture.submitted.get (0)).baseMidiNote ());
    }


    @Test
    void boundsNotifyTheAlreadyAuthoritativeRangeWithoutARedundantWrite ()
    {
        final Fixture fixture = new Fixture ("DRUM_PAD");
        fixture.base (4);
        fixture.press (DOWN);
        assertEquals (List.of (new ShowHostNotificationEffect ("Offset: -32 (E-2)")), fixture.submitted);
        fixture.submitted.clear ();
        fixture.base (100);
        fixture.press (UP);
        assertEquals (List.of (new ShowHostNotificationEffect ("Offset: 64 (E6)")), fixture.submitted);
    }


    @Test
    void heldMusicalPadsQueueOneAccumulatedAbsoluteIntentUntilTheLastRelease ()
    {
        final Fixture fixture = new Fixture ("WORKSPACE");
        final ControlId secondPad = PushControlIds.pad (28);
        fixture.pressed = Set.of (PLAY_PAD, secondPad);
        fixture.press (UP);
        fixture.event (UP, InputKind.BUTTON, InputPhase.END);
        fixture.pressed = Set.of (PLAY_PAD, secondPad, SHIFT);
        fixture.press (DOWN);
        assertTrue (fixture.submitted.isEmpty ());
        assertEquals (36, fixture.requestedBase);
        assertTrue (DrumOctaveView.mappingApplied (fixture.snapshot ()));

        fixture.pressed = Set.of (secondPad);
        fixture.event (PLAY_PAD, InputKind.PAD, InputPhase.END);
        assertTrue (fixture.submitted.isEmpty ());
        fixture.pressed = Set.of ();
        fixture.event (secondPad, InputKind.PAD, InputPhase.END);
        assertEquals (new SetDrumBankPositionEffect (1, "track-a", 48, false), fixture.submitted.get (0));
        assertEquals (1, fixture.submitted.size ());
        fixture.submitted.clear ();
        fixture.event (secondPad, InputKind.PAD, InputPhase.END);
        fixture.tick ();
        assertTrue (fixture.submitted.isEmpty ());
    }


    @Test
    void submittedOrRejectedMovementDoesNotAnnounceAnUnobservedRange ()
    {
        final Fixture fixture = new Fixture ("DRUM_PAD");
        fixture.press (UP);
        // The command was rejected: neither requested controller state nor host state advances.
        fixture.submitted.clear ();
        fixture.tick ();
        assertTrue (fixture.submitted.isEmpty ());
        fixture.targetId = "other-track";
        fixture.tick ();
        fixture.base (52);
        fixture.tick ();
        assertTrue (fixture.submitted.isEmpty ());
    }


    @Test
    void playablePadPressureAndLightsWaitForTheActuallyAppliedNativeMap ()
    {
        final Fixture fixture = new Fixture ("WORKSPACE");
        fixture.pressure = GridPressureConfiguration.POLY;
        final DrumPlayPadView pads = new DrumPlayPadView ();
        final var pressure = new ControllerInputEvent (1, 1, PLAY_PAD, InputKind.POLY_PRESSURE, InputPhase.UPDATE, 91);
        assertEquals (ON, pads.render (fixture.snapshot ()).lights ().get (PLAY_PAD));
        assertEquals (List.of (new SendNoteInputMidiEffect (0xA0, 36, 91)), pads.handle (pressure, fixture.snapshot ()));

        fixture.applied = DesiredNoteInputTranslation.silent ();
        assertEquals (OFF, pads.render (fixture.snapshot ()).lights ().get (PLAY_PAD));
        assertTrue (pads.handle (pressure, fixture.snapshot ()).isEmpty ());
        fixture.applyNativeTranslation ();
        fixture.press (UP);
        fixture.applyRequests ();
        assertEquals (OFF, pads.render (fixture.snapshot ()).lights ().get (PLAY_PAD));
        assertTrue (pads.handle (pressure, fixture.snapshot ()).isEmpty ());
        fixture.advanceHost ();
        assertEquals (OFF, pads.render (fixture.snapshot ()).lights ().get (PLAY_PAD));
        assertTrue (pads.handle (pressure, fixture.snapshot ()).isEmpty ());
        fixture.applyNativeTranslation ();
        assertEquals (ON, pads.render (fixture.snapshot ()).lights ().get (PLAY_PAD));
        assertEquals (List.of (new SendNoteInputMidiEffect (0xA0, 52, 91)), pads.handle (pressure, fixture.snapshot ()));
    }


    @Test
    void masterRetainsTheHeldMoveButLeavingTheDrumCompositionCancelsIt ()
    {
        final Fixture fixture = new Fixture ("WORKSPACE");
        final var retained = new RetainedControllerView (fixture.view);
        final var drum = CompiledWorkspace.compile ("Drum", List.of (retained));
        final var master = CompiledWorkspace.compile ("Master", List.of (retained));
        final var other = CompiledWorkspace.compile ("Other", List.of ());
        drum.start (fixture.snapshot ());
        fixture.pressed = Set.of (PLAY_PAD);
        fixture.press (UP);
        drum.deactivateExcept (master);
        master.activate (fixture.snapshot ());
        fixture.pressed = Set.of ();
        fixture.event (PLAY_PAD, InputKind.PAD, InputPhase.END);
        assertEquals (List.of (new SetDrumBankPositionEffect (1, "track-a", 52, false)), fixture.submitted);

        fixture.submitted.clear ();
        fixture.pressed = Set.of (PLAY_PAD);
        fixture.press (UP);
        master.deactivateExcept (other);
        other.activate (fixture.snapshot ());
        fixture.pressed = Set.of ();
        drum.activate (fixture.snapshot ());
        fixture.event (PLAY_PAD, InputKind.PAD, InputPhase.END);
        assertTrue (fixture.submitted.isEmpty ());
    }


    @Test
    void nonMusicalSessionAndControlPadsDoNotDelayAnOctaveRequest ()
    {
        final Fixture fixture = new Fixture ("WORKSPACE");
        fixture.pressed = Set.of (PushControlIds.pad (5), PushControlIds.pad (32), PushControlIds.pad (33), PushControlIds.pad (64));
        fixture.press (UP);
        assertEquals (52, ((SetDrumBankPositionEffect) fixture.submitted.get (0)).baseMidiNote ());
    }


    @ParameterizedTest
    @ValueSource (strings = {"track", "device", "window", "requested-base", "layout", "deactivate"})
    void queuedMovementIsCancelledWhenItsExactContextDisappears (final String change)
    {
        final Fixture fixture = new Fixture ("DRUM_PAD");
        fixture.pressed = Set.of (PLAY_PAD);
        fixture.press (UP);
        switch (change)
        {
            case "track" -> fixture.targetId = "track-b";
            case "device" -> fixture.deviceId = "other-drum";
            case "window" -> fixture.drumGeneration++;
            case "requested-base" -> fixture.requestedBase = 52;
            case "layout" -> fixture.layoutActive = false;
            case "deactivate" -> fixture.view.deactivate ();
            default -> throw new AssertionError (change);
        }
        fixture.pressed = Set.of ();
        fixture.event (PLAY_PAD, InputKind.PAD, InputPhase.END);
        fixture.tick ();
        assertTrue (fixture.submitted.isEmpty ());
    }


    @ParameterizedTest
    @ValueSource (ints = {4, 36, 100})
    void completeNativeMapEnablesOnlyTheLowerLeftSixteenPadsAndKeepsVelocityIdentity (final int base)
    {
        final Fixture fixture = new Fixture ("DRUM_PAD");
        fixture.base (base);
        final DesiredNoteInputTranslation translation = DrumOctaveView.translation (fixture.snapshot ());
        assertTrue (translation.owned ());
        assertEquals (128, translation.keyTranslation ().size ());
        assertEquals (128, translation.velocityTranslation ().size ());
        for (int key = 0; key < 128; key++)
        {
            final int physicalOffset = key - 36;
            final boolean playable = physicalOffset >= 0 && physicalOffset < 32 && physicalOffset % 8 < 4;
            final int expected = playable ? base + physicalOffset / 8 * 4 + physicalOffset % 8 : -1;
            assertEquals (expected, translation.keyTranslation ().get (key).intValue (), "physical MIDI note " + key);
            assertEquals (key, translation.velocityTranslation ().get (key).intValue (), "velocity " + key);
        }
    }


    @ParameterizedTest
    @ValueSource (strings = {"no-track", "no-notes", "no-drum", "model", "note-target", "note-generation", "note-position", "drum-target", "drum-generation", "layout", "engagement"})
    void unavailableOrDisagreeingTargetsAreSilentDarkAndInert (final String mismatch)
    {
        final Fixture fixture = new Fixture ("DRUM_PAD");
        fixture.mismatch = mismatch;
        fixture.press (UP);
        assertTrue (fixture.submitted.isEmpty ());
        assertEquals (DesiredNoteInputTranslation.silent (), DrumOctaveView.translation (fixture.snapshot ()));
        assertEquals (Map.of (UP, OFF, DOWN, OFF), fixture.view.render (fixture.snapshot ()).lights ());
    }


    @Test
    void observedAccentChangesDesiredVelocityButWaitsForAppliedNativeMap ()
    {
        final Fixture fixture = new Fixture ("DRUM_PAD");
        assertTrue (DrumOctaveView.mappingApplied (fixture.snapshot ()));
        fixture.accent = true;
        fixture.accentVelocity = 73;
        final var desired = DrumOctaveView.translation (fixture.snapshot ());
        assertEquals (0, desired.velocityTranslation ().get (0));
        for (int velocity = 1; velocity < 128; velocity++) assertEquals (73, desired.velocityTranslation ().get (velocity));
        assertFalse (DrumOctaveView.mappingApplied (fixture.snapshot ()), "configuration read-back is not native-map application");
        fixture.applyNativeTranslation ();
        assertTrue (DrumOctaveView.mappingApplied (fixture.snapshot ()));
        fixture.accent = false;
        assertEquals (64, DrumOctaveView.translation (fixture.snapshot ()).velocityTranslation ().get (64));
        assertFalse (DrumOctaveView.mappingApplied (fixture.snapshot ()));
        fixture.settingsAvailable = false;
        assertEquals (DesiredNoteInputTranslation.silent (), DrumOctaveView.translation (fixture.snapshot ()));
    }

    private static final class Fixture
    {
        private DrumOctaveView view = new DrumOctaveView ();
        private final String layout;
        private final List<CoreEffect> submitted = new ArrayList<> ();
        private Set<ControlId> pressed = Set.of ();
        private int requestedBase = 36;
        private int observedBase = 36;
        private long drumGeneration = 1;
        private long sequence;
        private String targetId = "track-a";
        private String deviceId = "drum-a";
        private boolean layoutActive = true;
        private String mismatch = "";
        private boolean accent;
        private int accentVelocity = 127;
        private boolean settingsAvailable = true;
        private GridPressureConfiguration pressure = GridPressureConfiguration.OFF;
        private DesiredNoteInputTranslation applied = DesiredNoteInputTranslation.unowned ();


        private Fixture (final String layout)
        {
            this.layout = layout;
            this.applyNativeTranslation ();
            this.view.start (this.snapshot ());
        }


        private void base (final int base)
        {
            this.requestedBase = base;
            this.observedBase = base;
            this.applyNativeTranslation ();
        }


        private void press (final ControlId control)
        {
            this.event (control, InputKind.BUTTON, InputPhase.BEGIN);
        }


        private void event (final ControlId control, final InputKind kind, final InputPhase phase)
        {
            final long next = ++this.sequence;
            this.submitted.addAll (this.view.handle (new ControllerInputEvent (next, next, control, kind, phase, phase == InputPhase.END ? 0 : 127), this.snapshot ()));
        }


        private void tick ()
        {
            final long next = ++this.sequence;
            this.submitted.addAll (this.view.handle (new ControllerTickEvent (next, next), this.snapshot ()));
        }


        private void applyRequests ()
        {
            for (final CoreEffect effect: this.submitted)
            {
                if (effect instanceof final SetDrumBankPositionEffect position)
                    this.requestedBase = position.baseMidiNote ();
            }
            this.submitted.clear ();
        }


        private void advanceHost ()
        {
            this.observedBase = this.requestedBase;
            this.drumGeneration++;
        }


        private void applyNativeTranslation ()
        {
            this.applied = DrumOctaveView.translation (this.snapshot ());
        }


        private ControllerSnapshot snapshot ()
        {
            final SelectedTrackSnapshot selected = new SelectedTrackSnapshot (1, this.targetId, "Drums", 0, "INSTRUMENT", !"no-track".equals (this.mismatch), false, false, !"no-notes".equals (this.mismatch), false, true, false, TrackMonitorMode.AUTO, false, false, false, false, 0.5, 0.5, ON);
            final ControllerLayoutSnapshot layoutState = new ControllerLayoutSnapshot (1, this.layout, "TRACK", this.layoutActive && !"layout".equals (this.mismatch), !"engagement".equals (this.mismatch), this.requestedBase, this.pressure, this.applied);
            final NoteViewSnapshot note = new NoteViewSnapshot ("note-generation".equals (this.mismatch) ? 2 : 1, "note-target".equals (this.mismatch) ? "other-track" : this.targetId, "note-position".equals (this.mismatch) ? 1 : 0, ControllerNoteView.DRUM_PAD, !"no-drum".equals (this.mismatch));
            final DrumPadSnapshot firstPad = new DrumPadSnapshot (0, this.observedBase, "pad-a", true, "Kick", ON, true, true, false, false, false, 0.5, 0.5, 0);
            final DrumContextSnapshot drum = new DrumContextSnapshot (this.drumGeneration, "drum-generation".equals (this.mismatch) ? 2 : 1, "drum-target".equals (this.mismatch) ? "other-track" : this.targetId, this.deviceId, true, !"model".equals (this.mismatch), this.observedBase, List.of (firstPad));
            final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (TransportSnapshot.empty (), selected, de.mossgrabers.pull.core.api.SessionBankSnapshot.empty (), layoutState, note, NoteRepeatSnapshot.empty (), drum, ParameterBridgeSnapshot.empty (), de.mossgrabers.pull.core.api.ControllerMappingFeedbackSnapshot.empty (), MasterSnapshot.empty (), ProjectSnapshot.empty (), de.mossgrabers.pull.core.api.AutomationSnapshot.empty (), de.mossgrabers.pull.core.api.EncoderConfigurationSnapshot.empty (), de.mossgrabers.pull.core.api.CurrentTrackBankSnapshot.empty (), de.mossgrabers.pull.core.api.TransportSettingsSnapshot.empty (), this.settingsAvailable ? new de.mossgrabers.pull.core.api.ControllerSettingsSnapshot (true, false, "VOLUME", 0, de.mossgrabers.pull.core.api.CursorSendBankSnapshot.empty (), this.accent, this.accentVelocity) : de.mossgrabers.pull.core.api.ControllerSettingsSnapshot.empty ());
            return new ControllerSnapshot (this.sequence, this.sequence, ShellCapabilities.empty (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), java.util.Optional.empty (), this.pressed, Set.of ());
        }
    }
}
