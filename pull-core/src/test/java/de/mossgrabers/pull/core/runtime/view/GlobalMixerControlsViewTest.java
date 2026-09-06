// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class GlobalMixerControlsViewTest
{
    private static final ControlId DELETE = PushControlIds.button ("DELETE");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId SELECT = PushControlIds.button ("SELECT");
    private static final RgbColor COLOR = new RgbColor (30, 120, 240);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);
    private static final EncoderConfigurationSnapshot CONFIG = new EncoderConfigurationSnapshot (true, 1024, 10, 0, -90);

    @Test
    void boundedPageContractUsesCurrentBankSlotsAndLeavesNavigationUnclaimed ()
    {
        for (final GlobalMixerControlsView.Role role: List.of (GlobalMixerControlsView.Role.VOLUME, GlobalMixerControlsView.Role.PAN))
        {
            final Fixture fixture = new Fixture (role);
            final CoreResult result = fixture.workspace.activate (fixture.snapshot ());
            assertEquals (role.name (), result.desiredControllerState ().workspace ().installedModeId ());
            assertEquals (Set.of (role == GlobalMixerControlsView.Role.VOLUME ? ParameterBankId.TRACK_VOLUME : ParameterBankId.TRACK_PAN), result.desiredParameterBanks ().banks ());
            assertTrue (fixture.view.profile ().controllerFacets ().isEmpty ());
            for (int index = 0; index < 8; index++) assertEquals (fixture.slot (index), fixture.workspace.parameterSlotOrNull (knob (index), fixture.snapshot ()));
            assertNull (fixture.workspace.parameterSlotOrNull (PushControlIds.continuous ("MASTER_KNOB"), fixture.snapshot ()));
        }
    }

    @Test
    void fullBankVolumeAndPanUseRoleCalibrationAndFineSensitivity ()
    {
        final Fixture volume = new Fixture (GlobalMixerControlsView.Role.VOLUME);
        volume.value = 1023;
        assertEquals (List.of (new AdjustParameterValueEffect (volume.target (0), 12)), volume.turn (0, 1).effects ());
        volume.pressed = Set.of (SHIFT);
        assertEquals (1.2, ((AdjustParameterValueEffect) volume.turn (0, 1).effects ().getFirst ()).delta (), 1e-10);
        final Fixture pan = new Fixture (GlobalMixerControlsView.Role.PAN);
        pan.value = 495;
        assertEquals (List.of (new SetParameterNormalizedValueEffect (pan.target (0), 0.5)), pan.turn (0, 1).effects ());
        pan.value = 512;
        assertEquals (List.of (new AdjustParameterValueEffect (pan.target (0), -5)), pan.turn (0, -1).effects ());
        pan.value = 900;
        assertEquals (List.of (new AdjustParameterValueEffect (pan.target (0), 15)), pan.turn (0, 3).effects ());
        pan.configuration = EncoderConfigurationSnapshot.empty ();
        assertTrue (pan.turn (0, 3).effects ().isEmpty ());
    }

    @Test
    void parameterWritesAndModeRequestsDoNotOptimisticallyChangeFeedback ()
    {
        final Fixture fixture = new Fixture (GlobalMixerControlsView.Role.PAN);
        final DesiredHardwareOutput before = fixture.workspace.activate (fixture.snapshot ()).desiredOutput ();
        assertEquals (before, fixture.turn (0, 8).desiredOutput ());
        final CoreResult requested = fixture.menu (0);
        assertEquals (List.of (new SetControllerModeSettingEffect (SetControllerModeSettingEffect.Setting.GLOBAL_MIX_MODE, "VOLUME"), new SelectControllerModeEffect (7, "VOLUME")), requested.effects ());
        assertEquals (before, requested.desiredOutput ());
        fixture.value = 700;
        assertNotEquals (before.display (), fixture.workspace.activate (fixture.snapshot ()).desiredOutput ().display ());
    }

    @Test
    void deferredMixerMenusCancelTheirWholeRecipeWhenTheOriginChanges ()
    {
        for (final GlobalMixerControlsView.Role role: GlobalMixerControlsView.Role.values ())
            for (final boolean hiddenChange: List.of (false, true))
            {
                final Fixture fixture = new Fixture (role);
                final var action = fixture.workspace.resolveAction (new ControllerInputEvent (1, 1, upper (0), InputKind.BUTTON, InputPhase.BEGIN, 127), fixture.snapshot ());
                final var origin = fixture.layout;
                fixture.layout = new ControllerLayoutSnapshot (hiddenChange ? 7 : 8, "PLAY", origin.modeId (), false, false, 0, GridPressureConfiguration.OFF, DesiredNoteInputTranslation.unowned (), origin.activeModeId (), hiddenChange ? "TRACK" : "", false);
                assertTrue (fixture.workspace.dispatchAction (action, fixture.snapshot ()).isEmpty ());
                assertFalse (fixture.menu (0).effects ().isEmpty (), "a fresh action remains usable after cancellation");
            }
    }

    @Test
    void globalMenuUsesSixthSendThresholdAndOnlyReadbackMovesItsArrow ()
    {
        final Fixture fixture = new Fixture (GlobalMixerControlsView.Role.VOLUME);
        fixture.sendCount = 5;
        final CoreResult five = fixture.workspace.activate (fixture.snapshot ());
        assertFalse (texts (five).contains (">"));
        assertEquals ("SEND5", ((SelectControllerModeEffect) fixture.menu (6).effects ().getLast ()).modeId ());
        fixture.sendCount = 6;
        final CoreResult six = fixture.workspace.activate (fixture.snapshot ());
        assertTrue (texts (six).contains (">"));
        assertEquals (WHITE, six.desiredOutput ().lights ().get (upper (6)));
        final CoreResult request = fixture.menu (6);
        assertEquals (List.of (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.MIX_SEND_OFFSET, 4)), request.effects ());
        assertEquals (six.desiredOutput (), request.desiredOutput ());
        fixture.sendOffset = 4;
        final CoreResult advanced = fixture.workspace.activate (fixture.snapshot ());
        assertTrue (texts (advanced).contains ("<"));
        assertFalse (texts (advanced).contains (">"));
        assertEquals (WHITE, advanced.desiredOutput ().lights ().get (upper (2)));
        assertEquals ("SEND5", ((SelectControllerModeEffect) fixture.menu (3).effects ().getLast ()).modeId ());
        assertEquals ("SEND8", ((SelectControllerModeEffect) fixture.menu (6).effects ().getLast ()).modeId ());
        assertEquals (List.of (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.MIX_SEND_OFFSET, 0)), fixture.menu (2).effects ());
    }

    @Test
    void blankSendsRemainSelectableAndShrinkingWindowRequestsOffsetReset ()
    {
        final Fixture fixture = new Fixture (GlobalMixerControlsView.Role.VOLUME);
        fixture.sendOffset = 4;
        fixture.sendCount = 0;
        fixture.workspace.activate (fixture.snapshot ());
        assertEquals (List.of (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.MIX_SEND_OFFSET, 0), new SetControllerModeSettingEffect (SetControllerModeSettingEffect.Setting.GLOBAL_MIX_MODE, "SEND1"), new SelectControllerModeEffect (7, "SEND1")), fixture.menu (2).effects ());
        assertEquals (List.of (new SetControllerIntegerSettingEffect (SetControllerIntegerSettingEffect.Setting.MIX_SEND_OFFSET, 0)), fixture.workspace.handle (new SnapshotChangedEvent (3, 3), fixture.snapshot ()).effects ());
        fixture.settingsAvailable = false;
        assertTrue (fixture.menu (2).effects ().isEmpty ());
    }

    @Test
    void modifierChordsDoNotReplacePlainMenuSelectionOrToggleBackToTrack ()
    {
        final Fixture fixture = new Fixture (GlobalMixerControlsView.Role.VOLUME);
        fixture.pressed = Set.of (SHIFT, SELECT, DELETE);
        assertEquals (List.of (new SetControllerModeSettingEffect (SetControllerModeSettingEffect.Setting.GLOBAL_MIX_MODE, "VOLUME"), new SelectControllerModeEffect (7, "VOLUME")), fixture.menu (0).effects ());
        assertEquals ("CROSSFADER", ((SelectControllerModeEffect) fixture.menu (7).effects ().getLast ()).modeId ());
        assertTrue (fixture.workspace.handle (new ControllerInputEvent (1, 1, upper (1), InputKind.BUTTON, InputPhase.END, 0), fixture.snapshot ()).effects ().isEmpty ());
    }

    @Test
    void deleteTouchResetsThenAcquiresExactParameterAndReleaseStopsAutomation ()
    {
        final Fixture fixture = new Fixture (GlobalMixerControlsView.Role.VOLUME);
        fixture.pressed = Set.of (DELETE, SHIFT, SELECT);
        final CoreResult begin = fixture.touch (0, true);
        assertEquals (List.of (new ConsumeControllerButtonEffect (DELETE), new ResetParameterEffect (fixture.target (0))), begin.effects ());
        assertEquals (Map.of (knob (0), fixture.target (0)), begin.desiredParameterTouches ().targets ());
        assertTrue (fixture.touch (0, true).effects ().isEmpty ());
        fixture.writing = true;
        assertEquals (List.of (new SetAutomationWriteEffect ("project", false)), fixture.touch (0, false).effects ());
        assertTrue (fixture.touch (0, false).effects ().isEmpty ());
    }

    @Test
    void absentParameterStillConsumesDeleteAndAdmitsReleasePolicy ()
    {
        final Fixture fixture = new Fixture (GlobalMixerControlsView.Role.PAN);
        fixture.parametersAvailable = false;
        fixture.pressed = Set.of (DELETE);
        fixture.writing = true;
        final CoreResult begin = fixture.touch (7, true);
        assertEquals (List.of (new ConsumeControllerButtonEffect (DELETE)), begin.effects ());
        assertTrue (begin.desiredParameterTouches ().targets ().isEmpty ());
        assertEquals (List.of (new SetAutomationWriteEffect ("project", false)), fixture.touch (7, false).effects ());
    }

    @Test
    void unrelatedSelectionKeepsCurrentBankTouchButReboundTargetRetiresIt ()
    {
        final Fixture fixture = new Fixture (GlobalMixerControlsView.Role.PAN);
        fixture.touch (1, true);
        fixture.selectedGeneration = 20;
        assertEquals (Map.of (knob (1), fixture.target (1)), fixture.workspace.activate (fixture.snapshot ()).desiredParameterTouches ().targets ());
        fixture.targetGeneration++;
        assertTrue (fixture.workspace.activate (fixture.snapshot ()).desiredParameterTouches ().targets ().isEmpty ());
        assertTrue (fixture.touch (1, true).desiredParameterTouches ().targets ().isEmpty (), "a duplicate BEGIN cannot retouch a rebound owner");
    }

    @Test
    void volumeDrawsEightIndependentObservedMetersAndModulatedFaders ()
    {
        final Fixture fixture = new Fixture (GlobalMixerControlsView.Role.VOLUME);
        fixture.modulated = 900;
        final CoreResult enabled = fixture.workspace.activate (fixture.snapshot ());
        final double meterTop = 160.0 / 12 + 4 + 28;
        assertEquals (16, enabled.desiredOutput ().display ().commands ().stream ().filter (command -> command instanceof DisplayCommand.Rectangle rectangle && rectangle.width () == 18 && rectangle.y () == meterTop).count ());
        fixture.vu = true;
        final var meters = fixture.workspace.activate (fixture.snapshot ()).desiredOutput ().display ();
        assertNotEquals (enabled.desiredOutput ().display (), meters);
        assertEquals (8, meters.commands ().stream ().filter (command -> command instanceof DisplayCommand.Rectangle rectangle && rectangle.width () == 13 && rectangle.height () == 1).count ());
        assertFalse (enabled.desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof DisplayCommand.TextAt text && "Volume".equals (text.text ())));
    }

    @Test
    void differentSnapshotEpochsAndUnclassifiedOwnersFailClosedWithoutStrandingRelease ()
    {
        final Fixture fixture = new Fixture (GlobalMixerControlsView.Role.VOLUME);
        fixture.touch (0, true);
        fixture.aligned = false;
        fixture.writing = true;
        final CoreResult mismatched = fixture.workspace.activate (fixture.snapshot ());
        assertTrue (mismatched.desiredParameterTouches ().targets ().isEmpty ());
        assertFalse (texts (mismatched).contains ("-6.0"));
        assertTrue (fixture.turn (0, 2).effects ().isEmpty ());
        assertEquals (List.of (new SetAutomationWriteEffect ("project", false)), fixture.touch (0, false).effects ());
        fixture.aligned = true;
        fixture.classified = false;
        assertTrue (fixture.turn (0, 2).effects ().isEmpty ());
        assertTrue (fixture.touch (0, true).desiredParameterTouches ().targets ().isEmpty ());
    }

    @Test
    void panKeepsNormalTextDirectionAndSliderGeometryWithoutTrackMixLabels ()
    {
        final Fixture fixture = new Fixture (GlobalMixerControlsView.Role.PAN);
        fixture.value = 0;
        final CoreResult result = fixture.workspace.activate (fixture.snapshot ());
        assertEquals (8, result.desiredOutput ().display ().commands ().stream ().filter (command -> command instanceof DisplayCommand.TextAt text && "L 100".equals (text.text ()) && text.baselineY () == 55).count ());
        assertEquals (8, result.desiredOutput ().display ().commands ().stream ().filter (command -> command instanceof DisplayCommand.RoundedRectangle rectangle && rectangle.width () == 5 && rectangle.height () == 16).count ());
        assertFalse (result.desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof DisplayCommand.TextAt text && "Pan".equals (text.text ())));
        fixture.value = 1023;
        assertTrue (texts (fixture.workspace.activate (fixture.snapshot ())).contains ("R 100"));
    }

    private static List<String> texts (final CoreResult result) { return result.desiredOutput ().display ().commands ().stream ().flatMap (command -> command instanceof DisplayCommand.TextAt text ? java.util.stream.Stream.of (text.text ()) : command instanceof DisplayCommand.TextBox text ? java.util.stream.Stream.of (text.text ()) : java.util.stream.Stream.empty ()).toList (); }
    private static ControlId knob (final int index) { return PushControlIds.continuous ("KNOB" + (index + 1)); }
    private static ControlId upper (final int index) { return PushControlIds.button ("ROW2_" + (index + 1)); }

    private static final class Fixture
    {
        private final GlobalMixerControlsView.Role role;
        private final GlobalMixerControlsView view;
        private final CompiledWorkspace workspace;
        private ControllerLayoutSnapshot layout;
        private double value = 512;
        private double modulated = -1;
        private boolean vu;
        private boolean writing;
        private boolean settingsAvailable = true;
        private boolean parametersAvailable = true;
        private boolean aligned = true;
        private boolean classified = true;
        private int sendCount = 8;
        private int sendOffset;
        private long targetGeneration = 1;
        private long selectedGeneration = 1;
        private Set<ControlId> pressed = Set.of ();
        private Set<ControlId> touched = Set.of ();
        private EncoderConfigurationSnapshot configuration = CONFIG;
        private Fixture (final GlobalMixerControlsView.Role role)
        {
            this.role = role;
            final ParameterTouchSession session = new ParameterTouchSession ();
            this.view = role == GlobalMixerControlsView.Role.SEND ? GlobalMixerControlsView.send (0, session) : new GlobalMixerControlsView (role, session);
            this.layout = new ControllerLayoutSnapshot (7, "PLAY", this.view.installedModeId (), false, false, 0, GridPressureConfiguration.OFF);
            this.workspace = CompiledWorkspace.compile (role.name (), List.of (this.view, new CurrentTrackFooterView (), new ParameterTouchReleaseView (session)));
            this.workspace.start (this.snapshot ());
        }
        private ParameterSlot slot (final int index) { return this.role == GlobalMixerControlsView.Role.SEND ? ParameterSlot.trackSend (0, index) : this.role == GlobalMixerControlsView.Role.VOLUME ? ParameterSlot.trackVolume (index) : ParameterSlot.trackPan (index); }
        private ParameterTargetRef target (final int index) { return new ParameterTargetRef (ParameterTargetKind.LIVE, this.role + "-" + index, this.targetGeneration); }
        private CoreResult turn (final int index, final int amount) { return this.workspace.handle (new ControllerInputEvent (1, 1, knob (index), InputKind.RELATIVE, InputPhase.UPDATE, amount), this.snapshot ()); }
        private CoreResult touch (final int index, final boolean begin)
        {
            this.touched = begin ? Set.of (knob (index)) : Set.of ();
            return this.workspace.handle (new ControllerInputEvent (1, 1, knob (index), InputKind.TOUCH, begin ? InputPhase.BEGIN : InputPhase.END, begin ? 127 : 0), this.snapshot ());
        }
        private CoreResult menu (final int index)
        {
            final ControllerSnapshot snapshot = this.snapshot ();
            final var input = new ControllerInputEvent (1, 1, upper (index), InputKind.BUTTON, InputPhase.BEGIN, 127);
            return this.workspace.handleAction (this.workspace.resolveAction (input, snapshot), snapshot);
        }
        private ControllerSnapshot snapshot ()
        {
            final Map<ParameterSlot, ParameterTargetSnapshot> parameters = new LinkedHashMap<> ();
            final List<CurrentTrackSnapshot> tracks = new ArrayList<> ();
            for (int index = 0; index < 8; index++)
            {
                if (this.parametersAvailable) parameters.put (this.slot (index), new ParameterTargetSnapshot (this.target (index), "Parameter", this.value, this.modulated, "-6.0 dB", 128, 0, Optional.empty (), this.classified ? new ParameterTargetIdentitySnapshot ("channel-" + this.role.name ().toLowerCase (java.util.Locale.ROOT), this.aligned ? "track-" + index : "different-" + index, 0, 0) : ParameterTargetIdentitySnapshot.empty ()));
                tracks.add (new CurrentTrackSnapshot (new SessionTrackSnapshot ("track-" + index, index, "Track " + index, true, index == 0, true, false, false, false, false, SessionTrackType.AUDIO, COLOR), false, (index + 1) / 8.0, (8 - index) / 8.0));
            }
            final List<CursorSendBankSnapshot.Send> sends = new ArrayList<> ();
            for (int index = 0; index < 8; index++) sends.add (new CursorSendBankSnapshot.Send (index < this.sendCount, index < this.sendCount ? "Send " + (index + 1) : ""));
            final ControllerSettingsSnapshot settings = this.settingsAvailable ? new ControllerSettingsSnapshot (true, this.vu, "VOLUME", this.sendOffset, new CursorSendBankSnapshot (1, "pinned-cursor", 0, sends)) : ControllerSettingsSnapshot.empty ();
            final var empty = ControllerBridgeSnapshot.empty ();
            final SelectedTrackSnapshot selected = new SelectedTrackSnapshot (this.selectedGeneration, "other-private-track", "Other", 0, "Audio", true, false, false, true, false, false, true, TrackMonitorMode.AUTO, false, false, false, false, 0.5, 0.5, COLOR);
            final var bridge = new ControllerBridgeSnapshot (empty.transport (), selected, empty.sessionBank (), this.layout, empty.noteView (), empty.noteRepeat (), empty.drum (), new ParameterBridgeSnapshot (parameters, Map.of ()), empty.controllerMappingFeedback (), empty.master (), empty.project (), new AutomationSnapshot ("project", this.writing, true), this.configuration, new CurrentTrackBankSnapshot (1, "effect-bank", 8, tracks, "pinned-cursor", true, 1, true), empty.transportSettings (), settings);
            return new ControllerSnapshot (1, 1, new ShellCapabilities (Map.of ()), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), this.pressed, this.touched);
        }
    }
}
