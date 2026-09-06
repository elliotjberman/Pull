// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import de.mossgrabers.pull.core.view.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TrackMixControlViewTest
{
    private static final ControlId BUTTON = PushControlIds.button ("TRACK");

    @Test
    void trackAndEveryGlobalPageToggleOnBeginAndDoNotRestoreOnLongEnd ()
    {
        for (final String mode: List.of ("TRACK", "VOLUME", "PAN", "CROSSFADER", "SEND1", "SEND2", "SEND3", "SEND4", "SEND5", "SEND6", "SEND7", "SEND8"))
        {
            final Fixture f = new Fixture ();
            f.mode (mode);
            assertTrue (f.edge (InputPhase.BEGIN).effects ().isEmpty ());
            assertEquals (mode.equals ("TRACK") ? "PAN" : "TRACK", f.navigation.legacyAlias ());
            assertTrue (f.edge (InputPhase.LONG).effects ().isEmpty ());
            assertTrue (f.edge (InputPhase.END).effects ().isEmpty ());
        }
    }

    @Test
    void otherPagesEnterTrackAndOnlyLongReleaseRestoresTheUnderlyingPage ()
    {
        final Fixture f = new Fixture ();
        f.mode ("DEVICE_PARAMS");
        f.navigation.temporary (f.navigation.origin (), f.navigation.resolve ("AUTOMATION"));
        assertTrue (f.edge (InputPhase.BEGIN).effects ().isEmpty ());
        assertEquals ("TRACK", f.navigation.legacyAlias ());
        f.observe ("TRACK");
        f.edge (InputPhase.LONG);
        f.shift = true;
        assertTrue (f.edge (InputPhase.END).effects ().isEmpty ());
        assertEquals ("DEVICE_PARAMS", f.navigation.legacyAlias ());
        final Fixture shortPress = new Fixture ();
        shortPress.mode ("DEVICE_PARAMS");
        shortPress.edge (InputPhase.BEGIN);
        shortPress.observe ("TRACK");
        assertTrue (shortPress.edge (InputPhase.END).effects ().isEmpty ());
    }

    @Test
    void shiftIsFrozenAtBeginAndVuWritesSerializeAgainstObservedSettings ()
    {
        final Fixture f = new Fixture ();
        f.shift = true;
        final var action = f.resolve ();
        assertEquals (ControllerActionId.SET_CONTROLLER_PREFERENCE, action.intent ().action ());
        assertEquals (Set.of (ControllerStateScope.CONTROLLER_SETTINGS), action.intent ().invalidates ());
        f.shift = false;
        assertEquals (List.of (new SetControllerBooleanSettingEffect (SetControllerBooleanSettingEffect.Setting.VU_METERS, true)), f.dispatch (action).effects ());
        assertTrue (f.edge (InputPhase.LONG).effects ().isEmpty ());
        assertTrue (f.edge (InputPhase.END).effects ().isEmpty ());
        f.shift = true;
        assertTrue (f.edge (InputPhase.BEGIN).effects ().isEmpty ());
        f.edge (InputPhase.END);
        f.vu = true;
        assertEquals (List.of (new SetControllerBooleanSettingEffect (SetControllerBooleanSettingEffect.Setting.VU_METERS, false)), f.tick ().effects ());
    }

    @Test
    void selectsExactSlotZeroOnlyWhenThereIsNoCurrentBankSelection ()
    {
        final Fixture f = new Fixture ();
        f.selected = false;
        final var action = f.resolve ();
        assertEquals (Set.of (ControllerStateScope.ACTIVE_PARAMETERS), action.intent ().invalidates ());
        f.bankGeneration = 9;
        f.channel = "later-track";
        f.shift = true;
        final var effects = f.dispatch (action).effects ();
        assertEquals (List.of (new CurrentTrackActionEffect (new CurrentTrackTarget (7, "main", 0, "track-a"), CurrentTrackActionEffect.Action.SELECT)), effects);
        final Fixture empty = new Fixture ();
        empty.bankAvailable = false;
        assertTrue (empty.edge (InputPhase.BEGIN).effects ().isEmpty ());
        assertEquals ("PAN", empty.navigation.legacyAlias ());
    }

    @Test
    void deferredBeginKeepsItsLongAndReleaseWithLocalContinuation ()
    {
        final Fixture f = new Fixture ();
        f.mode ("DEVICE_PARAMS");
        final var action = f.resolve ();
        f.edge (InputPhase.LONG);
        f.edge (InputPhase.END);
        assertTrue (f.dispatch (action).effects ().isEmpty ());
        assertEquals ("TRACK", f.navigation.legacyAlias ());
        assertTrue (f.tick ().effects ().isEmpty ());
        assertEquals ("DEVICE_PARAMS", f.navigation.legacyAlias ());
        assertTrue (f.tick ().effects ().isEmpty ());
    }

    @Test
    void admittedEntryKeepsCapturedReturnAfterAnExternalPageChange ()
    {
        final Fixture f = new Fixture ();
        f.mode ("DEVICE_PARAMS");
        f.edge (InputPhase.BEGIN);
        f.observe ("TRACK");
        f.tick ();
        f.edge (InputPhase.LONG);
        f.observe ("TRANSPORT");
        assertTrue (f.edge (InputPhase.END).effects ().isEmpty ());
        assertEquals ("DEVICE_PARAMS", f.navigation.legacyAlias ());
    }

    @Test
    void staleEntryCannotRestoreAnUnrelatedPage ()
    {
        final Fixture f = new Fixture ();
        f.mode ("DEVICE_PARAMS");
        final var action = f.resolve ();
        f.edge (InputPhase.LONG);
        f.edge (InputPhase.END);
        f.observe ("TRANSPORT");
        assertTrue (f.dispatch (action).effects ().isEmpty ());
        assertTrue (f.tick ().effects ().isEmpty ());
        assertEquals ("TRANSPORT", f.navigation.legacyAlias ());
        assertFalse (f.view.executionRequirements ().ticksRequested ());
    }

    @Test
    void lightComesFromCorePageStateIncludingLegacyDetailsAndArmModes ()
    {
        final Fixture f = new Fixture ();
        f.mode ("DEVICE_PARAMS");
        assertEquals (new RgbColor (255, 255, 255), f.edge (InputPhase.BEGIN).desiredOutput ().lights ().get (BUTTON));
        assertEquals (new RgbColor (255, 255, 255), f.tick ().desiredOutput ().lights ().get (BUTTON));
        for (final String mode: List.of ("TRACK", "VOLUME", "PAN", "CROSSFADER", "SEND8", "TRACK_DETAILS", "REC_ARM"))
        {
            f.observe (mode);
            assertEquals (new RgbColor (255, 255, 255), f.tick ().desiredOutput ().lights ().get (BUTTON));
        }
        f.mode ("");
        assertEquals (new RgbColor (0, 0, 0), f.tick ().desiredOutput ().lights ().get (BUTTON));
    }

    @Test
    void retainedViewSurvivesPageCompositionButNewGenerationRejectsOrphanRelease ()
    {
        final Fixture f = new Fixture ();
        f.mode ("DEVICE_PARAMS");
        final var action = f.resolve ();
        f.dispatch (action);
        f.edge (InputPhase.LONG);
        final CompiledWorkspace nextPage = CompiledWorkspace.compile ("next-page", List.of (f.retained));
        f.workspace.deactivateExcept (nextPage);
        nextPage.start (f.snapshot ());
        f.observe ("TRACK");
        assertTrue (nextPage.handle (f.input (InputPhase.END), f.snapshot ()).effects ().isEmpty ());
        assertEquals ("DEVICE_PARAMS", f.navigation.legacyAlias ());
        f.view.deactivate ();
        assertTrue (f.dispatch (action).effects ().isEmpty ());
        assertTrue (f.edge (InputPhase.END).effects ().isEmpty ());
        assertTrue (f.tick ().effects ().isEmpty ());
    }

    private static final class Fixture
    {
        private final PageNavigation navigation = PageNavigation.defaults ();
        private final TrackMixControlView view = new TrackMixControlView (this.navigation);
        private final RetainedControllerView retained = new RetainedControllerView (this.view);
        private final CompiledWorkspace workspace = CompiledWorkspace.compile ("mix", List.of (this.retained));
        private String mode = "TRACK";
        private String activeMode = "TRACK";
        private boolean temporary;
        private long generation = 1;
        private long bankGeneration = 7;
        private String channel = "track-a";
        private boolean selected = true;
        private boolean bankAvailable = true;
        private boolean shift;
        private boolean vu;
        private long sequence;
        private long time;

        private Fixture () { this.workspace.start (this.snapshot ()); }
        private void mode (final String mode) { this.navigation.select (mode.isEmpty () ? ControllerPageRef.core ("unaliased") : this.navigation.resolve (mode)); }
        private void observe (final String mode) { this.generation++; this.mode (mode); }
        private ControllerInputEvent input (final InputPhase phase) { this.sequence++; this.time++; return new ControllerInputEvent (this.sequence, this.time, BUTTON, InputKind.BUTTON, phase, phase == InputPhase.END ? 0 : 127); }
        private ResolvedControllerAction resolve () { return this.workspace.resolveAction (this.input (InputPhase.BEGIN), this.snapshot ()); }
        private CoreResult dispatch (final ResolvedControllerAction action) { return this.workspace.handleAction (action, this.snapshot ()); }
        private CoreResult edge (final InputPhase phase) { if (phase == InputPhase.BEGIN) return this.dispatch (this.resolve ()); return this.workspace.handle (this.input (phase), this.snapshot ()); }
        private CoreResult tick () { this.sequence++; this.time++; return this.workspace.handle (new ControllerTickEvent (this.sequence, this.time), this.snapshot ()); }
        private ControllerSnapshot snapshot ()
        {
            final var empty = ControllerBridgeSnapshot.empty ();
            final var tracks = new ArrayList<CurrentTrackSnapshot> (Collections.nCopies (8, CurrentTrackSnapshot.empty ()));
            tracks.set (0, new CurrentTrackSnapshot (new SessionTrackSnapshot (this.channel, 0, "First", true, this.selected, true, false, false, false, false, SessionTrackType.AUDIO, new RgbColor (10, 20, 30)), false, 0, 0));
            final var bank = this.bankAvailable ? new CurrentTrackBankSnapshot (this.bankGeneration, "main", 0, tracks, "", false, 0, false) : CurrentTrackBankSnapshot.empty ();
            final var layout = new ControllerLayoutSnapshot (this.generation, "PLAY", this.mode, false, false, 0, GridPressureConfiguration.OFF, DesiredNoteInputTranslation.unowned (), this.activeMode, "OLDER", this.temporary);
            final var bridge = new ControllerBridgeSnapshot (empty.transport (), empty.selectedTrack (), empty.sessionBank (), layout, empty.noteView (), empty.noteRepeat (), empty.drum (), empty.parameters (), empty.controllerMappingFeedback (), empty.master (), empty.project (), empty.automation (), empty.encoderConfiguration (), bank, empty.transportSettings (), new ControllerSettingsSnapshot (true, this.vu, "PAN", 0, CursorSendBankSnapshot.empty ()));
            return new ControllerSnapshot (this.sequence, this.time, new PullCoreProvider ().descriptor ().requiredCapabilities (), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), this.shift ? Set.of (PushControlIds.button ("SHIFT")) : Set.of (), Set.of ());
        }
    }
}
