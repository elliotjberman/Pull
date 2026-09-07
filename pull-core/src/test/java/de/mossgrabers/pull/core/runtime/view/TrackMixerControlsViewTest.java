// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.RoutedWorkspace;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TrackMixerControlsViewTest
{
    private static final ControlId DELETE = PushControlIds.button ("DELETE");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId SELECT = PushControlIds.button ("SELECT");
    private static final RgbColor COLOR = new RgbColor (0, 128, 255);
    private static final EncoderConfigurationSnapshot CONFIG = new EncoderConfigurationSnapshot (true, 1024, 10, 0, -90);

    @Test
    void menusChooseNamedSlotsAndEmptyMetadataPageWithoutHardwareRebinding ()
    {
        final Fixture fixture = new Fixture (false);
        final ControllerSnapshot snapshot = snapshot (true, 8, Set.of (), Set.of ());
        final CoreResult initial = fixture.workspace.start (snapshot);
        assertEquals (Set.of (ParameterBankId.SELECTED_TRACK, ParameterBankId.SELECTED_TRACK_SENDS), initial.desiredParameterBanks ().banks ());
        assertEquals (ParameterSlot.selectedTrackSend (5), fixture.workspace.parameterSlotOrNull (knob (7), snapshot));
        assertEquals (new RgbColor (255, 255, 255), initial.desiredOutput ().lights ().get (upper (7)));
        final CoreResult next = fixture.page (7, snapshot);
        assertEquals (4, fixture.state.sendOffset ());
        assertEquals (ParameterSlot.selectedTrackSend (4), fixture.workspace.parameterSlotOrNull (knob (2), snapshot));
        assertNull (fixture.workspace.parameterSlotOrNull (knob (6), snapshot));
        assertEquals (new RgbColor (255, 255, 255), next.desiredOutput ().lights ().get (upper (6)));
        final CoreResult metadata = fixture.page (1, snapshot);
        assertTrue (fixture.state.inputOutputSelected ());
        for (int index = 0; index < 8; index++) assertNull (fixture.workspace.parameterSlotOrNull (knob (index), snapshot));
        assertTrue (metadata.desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof DisplayCommand.TextAt text && "Instrument".equals (text.text ())));
        assertTrue (metadata.desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof DisplayCommand.TextAt text && "Auto".equals (text.text ())));
        assertTrue (fixture.workspace.handle (relative (knob (0), 3), snapshot).effects ().isEmpty ());
        fixture.page (7, snapshot);
        assertEquals (4, fixture.state.sendOffset ());
        fixture.page (0, snapshot);
        fixture.page (6, snapshot);
        assertEquals (0, fixture.state.sendOffset ());
    }

    @Test
    void disappearanceOfAdditionalSendsReconcilesPageFromAnAlignedHostSample ()
    {
        final Fixture fixture = new Fixture (false);
        fixture.state.selectSendOffset (4);
        fixture.workspace.start (snapshot (true, 8, Set.of (), Set.of ()));
        assertEquals (4, fixture.state.sendOffset ());
        fixture.workspace.activate (snapshot (true, 6, Set.of (), Set.of ()));
        assertEquals (0, fixture.state.sendOffset ());
    }

    @Test
    void deleteShiftSelectKeepsResetTouchEnableOrderAndFeedbackWaitsForReadback ()
    {
        final Fixture fixture = new Fixture (false);
        final ControllerSnapshot before = snapshot (true, 8, Set.of (), Set.of ());
        final var display = fixture.workspace.start (before).desiredOutput ().display ();
        final ControlId knob = knob (2);
        final ParameterTargetRef send = target (ParameterSlot.selectedTrackSend (0));
        final CoreResult begin = fixture.workspace.handle (touch (knob, InputPhase.BEGIN), snapshot (true, 8, Set.of (knob), Set.of (DELETE, SHIFT, SELECT)));
        assertEquals (List.of (new ConsumeControllerButtonEffect (DELETE), new ResetParameterEffect (send), new AcquireParameterTouchEffect (knob, send), new ConsumeControllerButtonEffect (SELECT), new SetParameterEnabledEffect (send, false)), begin.effects ());
        assertEquals (Map.of (knob, send), begin.desiredParameterTouches ().targets ());
        assertEquals (display, begin.desiredOutput ().display ());
        assertNotEquals (display, fixture.workspace.activate (snapshot (false, 8, Set.of (knob), Set.of ())).desiredOutput ().display ());
        final CoreResult duplicate = fixture.workspace.handle (touch (knob, InputPhase.BEGIN), snapshot (false, 8, Set.of (knob), Set.of (DELETE, SHIFT, SELECT)));
        assertTrue (duplicate.effects ().isEmpty ());
    }

    @Test
    void dependentSendTogglesWaitForAuthoritativeAcknowledgement ()
    {
        final Fixture fixture = new Fixture (false);
        final ControlId knob = knob (2);
        final ParameterTargetRef send = target (ParameterSlot.selectedTrackSend (0));
        fixture.workspace.start (snapshot (true, 8, Set.of (), Set.of ()));
        fixture.workspace.handle (touch (knob, InputPhase.BEGIN), snapshot (true, 8, Set.of (knob), Set.of (SHIFT, SELECT)));
        fixture.workspace.handle (touch (knob, InputPhase.END), snapshot (true, 8, Set.of (), Set.of ()));
        final CoreResult second = fixture.workspace.handle (touch (knob, InputPhase.BEGIN), snapshot (true, 8, Set.of (knob), Set.of (SHIFT, SELECT)));
        assertFalse (second.effects ().stream ().anyMatch (SetParameterEnabledEffect.class::isInstance));
        assertTrue (second.executionRequirements ().ticksRequested ());
        final CoreResult ack = fixture.workspace.handle (new SnapshotChangedEvent (2, 2), snapshot (false, 8, Set.of (knob), Set.of ()));
        assertEquals (List.of (new SetParameterEnabledEffect (send, true)), ack.effects ());
        assertTrue (fixture.workspace.handle (new SnapshotChangedEvent (3, 3), snapshot (false, 8, Set.of (knob), Set.of ())).effects ().isEmpty ());
        final CoreResult finalAck = fixture.workspace.handle (new SnapshotChangedEvent (4, 4), snapshot (true, 8, Set.of (knob), Set.of ()));
        assertFalse (finalAck.executionRequirements ().ticksRequested ());
    }

    @Test
    void emptySendConsumesSelectButMetadataAndOutOfCapacitySlotsDoNot ()
    {
        final Fixture fixture = new Fixture (false);
        fixture.workspace.start (snapshot (true, 1, Set.of (), Set.of ()));
        final ControlId emptySend = knob (3);
        assertEquals (List.of (new ConsumeControllerButtonEffect (SELECT)), fixture.workspace.handle (touch (emptySend, InputPhase.BEGIN), snapshot (true, 1, Set.of (emptySend), Set.of (SHIFT, SELECT))).effects ());
        fixture.workspace.handle (touch (emptySend, InputPhase.END), snapshot (true, 1, Set.of (), Set.of ()));
        fixture.page (1, snapshot (true, 1, Set.of (), Set.of ()));
        assertTrue (fixture.workspace.handle (touch (emptySend, InputPhase.BEGIN), snapshot (true, 1, Set.of (emptySend), Set.of (SHIFT, SELECT))).effects ().isEmpty ());
    }

    @Test
    void pageChangeRetiresExactTouchWithoutInventingTouchOnTheNewSend ()
    {
        final Fixture fixture = new Fixture (false);
        final ControlId knob = knob (2);
        fixture.workspace.start (snapshot (true, 8, Set.of (), Set.of ()));
        fixture.workspace.handle (touch (knob, InputPhase.BEGIN), snapshot (true, 8, Set.of (knob), Set.of ()));
        assertTrue (fixture.page (7, snapshot (true, 8, Set.of (knob), Set.of ())).desiredParameterTouches ().targets ().isEmpty ());
        assertEquals (ParameterSlot.selectedTrackSend (4), fixture.workspace.parameterSlotOrNull (knob, snapshot (true, 8, Set.of (knob), Set.of ())));
    }

    @Test
    void normalResponsePreservesConfiguredVolumeCurveFineSpeedAndPanDetent ()
    {
        final ParameterTargetSnapshot volume = new ParameterTargetSnapshot (target (ParameterSlot.SELECTED_TRACK_VOLUME), 1023, 0);
        assertEquals (List.of (new AdjustParameterValueEffect (volume.target (), 12)), TrackEncoderResponse.adjust (ParameterSlot.SELECTED_TRACK_VOLUME, volume, 1, false, CONFIG));
        assertEquals (1.2, ((AdjustParameterValueEffect) TrackEncoderResponse.adjust (ParameterSlot.SELECTED_TRACK_VOLUME, volume, 1, true, CONFIG).getFirst ()).delta (), 1e-10);
        final ParameterTargetSnapshot pan = new ParameterTargetSnapshot (target (ParameterSlot.SELECTED_TRACK_PAN), 495, 0);
        assertEquals (List.of (new SetParameterNormalizedValueEffect (pan.target (), 0.5)), TrackEncoderResponse.adjust (ParameterSlot.SELECTED_TRACK_PAN, pan, 1, false, CONFIG));
        final ParameterTargetSnapshot centered = new ParameterTargetSnapshot (pan.target (), 512, 0);
        assertEquals (List.of (new AdjustParameterValueEffect (pan.target (), 5)), TrackEncoderResponse.adjust (ParameterSlot.SELECTED_TRACK_PAN, centered, 1, false, CONFIG));
        final Fixture vs = new Fixture (false);
        vs.workspace.start (snapshot (true, 8, Set.of (), Set.of ()));
        assertEquals (List.of (new AdjustParameterValueEffect (target (ParameterSlot.SELECTED_TRACK_VOLUME), 10)), vs.workspace.handle (relative (knob (0), 1), snapshot (true, 8, Set.of (), Set.of (SHIFT))).effects ());
    }

    private static ControlId knob (final int index) { return PushControlIds.continuous ("KNOB" + (index + 1)); }
    private static ControlId upper (final int index) { return PushControlIds.button ("ROW2_" + (index + 1)); }
    private static ControllerInputEvent touch (final ControlId control, final InputPhase phase) { return new ControllerInputEvent (1, 1, control, InputKind.TOUCH, phase, phase == InputPhase.END ? 0 : 127); }
    private static ControllerInputEvent relative (final ControlId control, final long value) { return new ControllerInputEvent (1, 1, control, InputKind.RELATIVE, InputPhase.UPDATE, value); }
    private static ParameterTargetRef target (final ParameterSlot slot) { return new ParameterTargetRef (ParameterTargetKind.LIVE, slot.bank ().name () + slot.index (), 1); }
    private long revision;
    private ControllerSnapshot snapshot (final boolean enabled, final int sends, final Set<ControlId> touched, final Set<ControlId> pressed)
    {
        final Map<ParameterSlot, ParameterTargetSnapshot> parameters = new LinkedHashMap<> ();
        for (final ParameterSlot slot: List.of (ParameterSlot.SELECTED_TRACK_VOLUME, ParameterSlot.SELECTED_TRACK_PAN))
            parameters.put (slot, new ParameterTargetSnapshot (target (slot), "Parameter", 512, 512, "64 units", 128, 0, Optional.empty (), new ParameterTargetIdentitySnapshot (slot.index () == 0 ? "channel-volume" : "channel-pan", "track-a", 0, 0)));
        for (int index = 0; index < sends; index++)
        {
            final ParameterSlot slot = ParameterSlot.selectedTrackSend (index);
            parameters.put (slot, new ParameterTargetSnapshot (target (slot), "Send " + index, 64, 64, "64 units", 128, 0, Optional.of (enabled), new ParameterTargetIdentitySnapshot ("channel-send", "track-a", 0, index)));
        }
        final SelectedTrackSnapshot selected = new SelectedTrackSnapshot (1, "track-a", "Track", 0, "Instrument", true, false, false, true, false, true, false, TrackMonitorMode.AUTO, false, false, false, false, 0.5, 0.5, COLOR);
        final List<CurrentTrackSnapshot> tracks = new ArrayList<> ();
        tracks.add (new CurrentTrackSnapshot (new SessionTrackSnapshot ("track-a", 0, "Track", true, true, true, false, false, false, false, SessionTrackType.INSTRUMENT, COLOR), false, 0.5, 0.4));
        for (int index = 1; index < 8; index++) tracks.add (CurrentTrackSnapshot.empty ());
        final CurrentTrackBankSnapshot bank = new CurrentTrackBankSnapshot (1, "main", 0, tracks, "track-a", false, 1, true);
        final ControllerBridgeSnapshot empty = ControllerBridgeSnapshot.empty ();
        final ControllerBridgeSnapshot bridge = new ControllerBridgeSnapshot (empty.transport (), selected, empty.sessionBank (), empty.layout (), empty.noteView (), empty.noteRepeat (), empty.drum (), new ParameterBridgeSnapshot (parameters, Map.of (), java.util.Set.of ()), empty.controllerMappingFeedback (), empty.master (), empty.project (), new AutomationSnapshot ("project-a", false, true), CONFIG, bank);
        return new ControllerSnapshot (++this.revision, this.revision, new ShellCapabilities (Map.of ()), bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), pressed, touched);
    }
    private static final class Fixture
    {
        private final TrackMixerPageState state = new TrackMixerPageState ();
        private final RoutedWorkspace workspace;
        private Fixture (final boolean normal) { this.workspace = new RoutedWorkspace (CompiledWorkspace.compile ("Track", List.of (new TrackMixerControlsView (this.state, normal), new TrackSelectionStripView ()))); }
        private CoreResult page (final int index, final ControllerSnapshot snapshot)
        {
            final var event = new ControllerInputEvent (1, 1, upper (index), InputKind.BUTTON, InputPhase.BEGIN, 127);
            final CoreResult result = this.workspace.handle (event, snapshot);
            this.workspace.handle (new ControllerInputEvent (2, 2, upper (index), InputKind.BUTTON, InputPhase.END, 0), snapshot);
            return result;
        }
    }
}
