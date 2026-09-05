// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.workspace.SessionBankRegistry;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwLight;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IApplication;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.IProject;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.IDrumDevice;
import de.mossgrabers.framework.daw.data.IDrumPad;
import de.mossgrabers.framework.daw.data.ISlot;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.IMasterTrack;
import de.mossgrabers.framework.daw.data.bank.IDrumPadBank;
import de.mossgrabers.framework.daw.data.bank.ISlotBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.pull.core.api.effect.SelectControllerModeEffect;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.daw.midi.INoteInput;
import de.mossgrabers.framework.daw.midi.INoteRepeat;
import de.mossgrabers.framework.daw.midi.ArpeggiatorMode;
import de.mossgrabers.framework.daw.constants.Resolution;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.daw.midi.SelectedTrackMonitorMode;
import de.mossgrabers.framework.daw.midi.SelectedTrackNoteTargetSnapshot;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.IView;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.scale.Scales;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerMappingContext;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.CoreControllerMappings;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.NoteRepeatMode;
import de.mossgrabers.pull.core.api.DesiredParameterBanks;
import de.mossgrabers.pull.core.api.DesiredParameterInteraction;
import de.mossgrabers.pull.core.api.DrumContextSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.effect.SelectDrumPadEffect;
import de.mossgrabers.pull.core.api.effect.TapTempoEffect;
import de.mossgrabers.pull.core.api.effect.ProjectHistoryEffect;
import de.mossgrabers.pull.core.api.effect.ProjectHistoryAction;
import de.mossgrabers.pull.core.api.effect.SetDrumBankPositionEffect;
import de.mossgrabers.pull.core.api.effect.ShowHostNotificationEffect;
import de.mossgrabers.pull.core.api.effect.SetControllerMappingStorageEffect;
import de.mossgrabers.pull.core.api.effect.ConsumeControllerButtonEffect;
import de.mossgrabers.pull.core.api.effect.NavigateProjectEffect;
import de.mossgrabers.pull.core.api.effect.ProjectNavigationDirection;
import de.mossgrabers.pull.core.api.effect.SetProjectEngineEffect;
import de.mossgrabers.pull.core.api.effect.SetProjectTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.SetNoteViewPreferenceEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackAction;
import de.mossgrabers.pull.core.api.effect.SelectedTrackActionEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackBoolean;
import de.mossgrabers.pull.core.api.effect.SendNoteInputMidiEffect;
import de.mossgrabers.pull.core.api.effect.SetSelectedTrackBooleanEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.SetTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.TransportState;

import org.junit.jupiter.api.Test;

import com.bitwig.extension.callback.StringValueChangedCallback;
import com.bitwig.extension.controller.api.DocumentState;
import com.bitwig.extension.controller.api.SettableStringValue;
import com.bitwig.extension.controller.api.Setting;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Safety-boundary tests for the permanent bounded controller bridge.
 */
class BoundedControllerBridgeTest
{
    @Test
    void admitsOnlyTheInstalledStableButtonConsumptionTargets ()
    {
        final BridgeFixture fixture = new BridgeFixture ();

        fixture.bridge.apply (fixture.bridge.prepare (new ConsumeControllerButtonEffect (PushControlIds.button ("SELECT"))));
        fixture.bridge.apply (fixture.bridge.prepare (new ConsumeControllerButtonEffect (PushControlIds.button ("DUPLICATE"))));
        fixture.bridge.apply (fixture.bridge.prepare (new ConsumeControllerButtonEffect (PushControlIds.button ("RECORD"))));
        for (int index = 1; index <= 8; index++)
            fixture.bridge.apply (fixture.bridge.prepare (new ConsumeControllerButtonEffect (PushControlIds.button ("ROW1_" + index))));

        assertThrows (IllegalArgumentException.class, () -> fixture.bridge.prepare (new ConsumeControllerButtonEffect (PushControlIds.button ("BROWSE"))));
        assertThrows (IllegalArgumentException.class, () -> fixture.bridge.prepare (new ConsumeControllerButtonEffect (PushControlIds.button ("ROW2_1"))));
    }


    @Test
    void modeSelectionRequiresInstalledDestinationAndObservedOriginLayout ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.surface.getModeManager ().register (Modes.TRACK, relaxedProxy (IMode.class));
        fixture.surface.getModeManager ().register (Modes.DEVICE_PARAMS, relaxedProxy (IMode.class));
        fixture.surface.getModeManager ().setActive (Modes.TRACK);
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        final long generation = fixture.bridge.snapshot ().layout ().generation ();
        assertThrows (IllegalArgumentException.class, () -> fixture.bridge.prepare (new SelectControllerModeEffect (generation, "UNINSTALLED")));
        assertThrows (IllegalArgumentException.class, () -> fixture.bridge.prepare (new SelectControllerModeEffect (generation, "MASTER")));
        assertThrows (IllegalArgumentException.class, () -> fixture.bridge.prepare (new SelectControllerModeEffect (generation + 1, "DEVICE_PARAMS")));
        fixture.bridge.apply (fixture.bridge.prepare (new SelectControllerModeEffect (generation, "DEVICE_PARAMS")));
        assertEquals (Modes.DEVICE_PARAMS, fixture.surface.getModeManager ().getActiveID ());
        assertEquals ("TRACK", fixture.bridge.snapshot ().layout ().modeId (), "submitted mode change needs a later bridge sample");
        fixture.bridge.refresh (2, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        assertEquals ("DEVICE_PARAMS", fixture.bridge.snapshot ().layout ().modeId ());
    }


    @Test
    void modeSelectionRechecksLiveLayoutBeforeApply ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.surface.getModeManager ().register (Modes.TRACK, relaxedProxy (IMode.class));
        fixture.surface.getModeManager ().register (Modes.MASTER, relaxedProxy (IMode.class));
        fixture.surface.getModeManager ().register (Modes.DEVICE_PARAMS, relaxedProxy (IMode.class));
        fixture.surface.getModeManager ().setActive (Modes.TRACK);
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        final var prepared = fixture.bridge.prepare (new SelectControllerModeEffect (fixture.bridge.snapshot ().layout ().generation (), "DEVICE_PARAMS"));
        fixture.surface.getModeManager ().setActive (Modes.MASTER);
        fixture.bridge.apply (prepared);
        assertEquals (Modes.MASTER, fixture.surface.getModeManager ().getActiveID ());
    }


    @Test
    void temporaryModeOperationsPreserveTheOneSlotAndPreviousModeAndWaitForReadback ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        for (final Modes mode: List.of (Modes.TRACK, Modes.DEVICE_PARAMS, Modes.TRANSPORT, Modes.AUTOMATION))
            fixture.surface.getModeManager ().register (mode, relaxedProxy (IMode.class));
        fixture.surface.getModeManager ().setActive (Modes.TRACK);
        fixture.surface.getModeManager ().setActive (Modes.DEVICE_PARAMS);
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        final var base = fixture.bridge.snapshot ().layout ();
        assertEquals ("DEVICE_PARAMS", base.activeModeId ());
        assertEquals ("TRACK", base.previousModeId ());
        assertFalse (base.temporaryMode ());
        fixture.bridge.apply (fixture.bridge.prepare (new SelectControllerModeEffect (base.generation (), "TRANSPORT", SelectControllerModeEffect.Operation.TEMPORARY)));
        assertEquals (base, fixture.bridge.snapshot ().layout ());
        fixture.bridge.refresh (2, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        final var firstTemporary = fixture.bridge.snapshot ().layout ();
        assertEquals ("TRANSPORT", firstTemporary.modeId ());
        assertEquals ("DEVICE_PARAMS", firstTemporary.activeModeId ());
        assertEquals ("TRACK", firstTemporary.previousModeId ());
        assertTrue (firstTemporary.temporaryMode ());
        fixture.bridge.apply (fixture.bridge.prepare (new SelectControllerModeEffect (firstTemporary.generation (), "AUTOMATION", SelectControllerModeEffect.Operation.TEMPORARY)));
        fixture.bridge.refresh (3, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        final var secondTemporary = fixture.bridge.snapshot ().layout ();
        assertEquals ("AUTOMATION", secondTemporary.modeId ());
        assertEquals ("DEVICE_PARAMS", secondTemporary.activeModeId ());
        assertEquals ("TRACK", secondTemporary.previousModeId ());
        fixture.bridge.apply (fixture.bridge.prepare (SelectControllerModeEffect.restore (secondTemporary.generation ())));
        fixture.bridge.refresh (4, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        assertEquals ("DEVICE_PARAMS", fixture.bridge.snapshot ().layout ().modeId ());
        assertFalse (fixture.bridge.snapshot ().layout ().temporaryMode ());
        fixture.bridge.apply (fixture.bridge.prepare (SelectControllerModeEffect.restore (fixture.bridge.snapshot ().layout ().generation ())));
        fixture.bridge.refresh (5, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        assertEquals ("TRACK", fixture.bridge.snapshot ().layout ().modeId ());
        assertEquals ("TRACK", fixture.bridge.snapshot ().layout ().previousModeId ());
        fixture.bridge.refresh (6, DesiredBridgeSubscriptions.empty (), DesiredParameterBanks.empty ());
        assertEquals (de.mossgrabers.pull.core.api.ControllerLayoutSnapshot.empty (), fixture.bridge.snapshot ().layout ());
    }


    @Test
    void unchangedVisibleModeStillFencesChangesToItsUnderlyingAndPreviousModes ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        for (final Modes mode: List.of (Modes.TRACK, Modes.MASTER, Modes.TRANSPORT, Modes.AUTOMATION))
            fixture.surface.getModeManager ().register (mode, relaxedProxy (IMode.class));
        fixture.surface.getModeManager ().setActive (Modes.TRACK);
        fixture.surface.getModeManager ().setTemporary (Modes.TRANSPORT);
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        final long original = fixture.bridge.snapshot ().layout ().generation ();
        fixture.surface.getModeManager ().setPreviousID (Modes.MASTER);
        assertThrows (IllegalArgumentException.class, () -> fixture.bridge.prepare (SelectControllerModeEffect.restore (original)));
        fixture.bridge.refresh (2, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        assertEquals (original + 1, fixture.bridge.snapshot ().layout ().generation ());
        assertEquals ("TRANSPORT", fixture.bridge.snapshot ().layout ().modeId ());
        final var pending = fixture.bridge.prepare (SelectControllerModeEffect.restore (fixture.bridge.snapshot ().layout ().generation ()));
        fixture.surface.getModeManager ().setActive (Modes.MASTER);
        fixture.surface.getModeManager ().setTemporary (Modes.TRANSPORT);
        fixture.bridge.apply (pending);
        assertEquals (Modes.TRANSPORT, fixture.surface.getModeManager ().getActiveID ());
        fixture.bridge.refresh (3, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        assertEquals (original + 2, fixture.bridge.snapshot ().layout ().generation ());
        assertEquals ("MASTER", fixture.bridge.snapshot ().layout ().activeModeId ());
    }


    @Test
    void modeOperationsRecheckInstalledTargetsAndRestoreWithoutHistoryIsANoop ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.surface.getModeManager ().register (Modes.TRACK, relaxedProxy (IMode.class));
        fixture.surface.getModeManager ().register (Modes.TRANSPORT, relaxedProxy (IMode.class));
        fixture.surface.getModeManager ().setActive (Modes.TRACK);
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        final long generation = fixture.bridge.snapshot ().layout ().generation ();
        fixture.bridge.apply (fixture.bridge.prepare (SelectControllerModeEffect.restore (generation)));
        assertEquals (Modes.TRACK, fixture.surface.getModeManager ().getActiveID ());
        assertThrows (IllegalArgumentException.class, () -> fixture.bridge.prepare (new SelectControllerModeEffect (generation, "AUTOMATION", SelectControllerModeEffect.Operation.TEMPORARY)));
        final var pending = fixture.bridge.prepare (new SelectControllerModeEffect (generation, "TRANSPORT", SelectControllerModeEffect.Operation.TEMPORARY));
        fixture.surface.getModeManager ().register (Modes.TRANSPORT, null);
        fixture.bridge.apply (pending);
        assertEquals (Modes.TRACK, fixture.surface.getModeManager ().getActiveID ());
    }


    @Test
    void onlyRegisteredInertAdaptersDeclareExclusivePageInputsAndLights ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.surface.addGraphicsDisplay (relaxedProxy (de.mossgrabers.framework.controller.display.IGraphicDisplay.class));
        final var mode = new de.mossgrabers.controller.ableton.push.mode.CorePageMode ("Core page", fixture.surface, relaxedProxy (IModel.class), new ReloadableControllerRuntime (relaxedProxy (com.bitwig.extension.controller.api.ControllerHost.class)));
        fixture.surface.getModeManager ().register (Modes.TRANSPORT, mode);
        fixture.surface.getModeManager ().register (Modes.AUTOMATION, relaxedProxy (IMode.class));
        fixture.surface.getModeManager ().register (Modes.TRACK, relaxedProxy (IMode.class));
        fixture.surface.getModeManager ().setActive (Modes.TRACK);
        final var workspace = new de.mossgrabers.pull.core.api.DesiredControllerWorkspace ("Temporary page", Set.of (), de.mossgrabers.pull.core.api.SessionBankShape.empty (), "TRANSPORT");
        final var host = new de.mossgrabers.controller.ableton.push.workspace.ControllerWorkspaceHost (fixture.surface);
        assertEquals (workspace, host.prepare (workspace));
        assertEquals (Modes.TRACK, fixture.surface.getModeManager ().getActiveID (), "footprint declaration must not select the page");
        for (int index = 1; index <= 8; index++)
        {
            assertTrue (fixture.bridge.supportsPageInput ("TRANSPORT", PushControlIds.continuous ("KNOB" + index), de.mossgrabers.pull.core.api.event.InputKind.TOUCH));
            assertTrue (fixture.bridge.supportsPageInput ("TRANSPORT", PushControlIds.continuous ("KNOB" + index), de.mossgrabers.pull.core.api.event.InputKind.RELATIVE));
            assertTrue (fixture.bridge.supportsPageInput ("TRANSPORT", PushControlIds.button ("ROW1_" + index), de.mossgrabers.pull.core.api.event.InputKind.BUTTON));
            assertTrue (fixture.bridge.supportsPageLight ("TRANSPORT", PushControlIds.button ("ROW2_" + index)));
        }
        assertFalse (fixture.bridge.supportsPageInput ("AUTOMATION", PushControlIds.continuous ("KNOB8"), de.mossgrabers.pull.core.api.event.InputKind.TOUCH));
        assertFalse (fixture.bridge.supportsPageInput ("unknown", PushControlIds.continuous ("KNOB8"), de.mossgrabers.pull.core.api.event.InputKind.RELATIVE));
        assertFalse (fixture.bridge.supportsPageInput ("TRANSPORT", PushControlIds.pad (1), de.mossgrabers.pull.core.api.event.InputKind.PAD));
        assertFalse (fixture.bridge.supportsPageLight ("TRANSPORT", PushControlIds.button ("PLAY")));
        assertThrows (IllegalArgumentException.class, () -> host.prepare (new de.mossgrabers.pull.core.api.DesiredControllerWorkspace ("Legacy", Set.of (), de.mossgrabers.pull.core.api.SessionBankShape.empty (), "AUTOMATION")));
    }


    @Test
    void layoutGenerationAdvancesOnlyWithAuthoritativeLayoutChanges ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.surface.getModeManager ().register (Modes.TRACK, relaxedProxy (IMode.class));
        fixture.surface.getViewManager ().register (Views.PLAY, relaxedProxy (IView.class));
        fixture.surface.getViewManager ().register (Views.SESSION, relaxedProxy (IView.class));
        fixture.surface.getModeManager ().setActive (Modes.TRACK);
        fixture.surface.getViewManager ().setActive (Views.PLAY);

        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        fixture.bridge.refresh (2, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        final long playGeneration = fixture.bridge.snapshot ().layout ().generation ();
        fixture.bridge.refresh (3, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        assertEquals (playGeneration, fixture.bridge.snapshot ().layout ().generation ());

        fixture.surface.getViewManager ().setActive (Views.SESSION);
        fixture.bridge.refresh (4, subscriptions (BridgeSubscription.CONTROLLER_LAYOUT), DesiredParameterBanks.empty ());
        assertEquals (playGeneration + 1, fixture.bridge.snapshot ().layout ().generation ());
    }


    @Test
    void noteViewPreferenceIsSelectedTargetFencedAtPrepareAndApply ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.SELECTED_TRACK), DesiredParameterBanks.empty ());
        final ControllerBridge.PreparedAction prepared = fixture.bridge.prepare (new SetNoteViewPreferenceEffect (1, "track-a", 2, ControllerNoteView.DRUM_PAD));

        fixture.selected.switchTo (2, "track-b");
        fixture.bridge.apply (prepared);
        assertNull (fixture.surface.getViewManager ().getPreferredView (2));

        fixture.bridge.refresh (2, subscriptions (BridgeSubscription.SELECTED_TRACK), DesiredParameterBanks.empty ());
        fixture.bridge.apply (fixture.bridge.prepare (new SetNoteViewPreferenceEffect (2, "track-b", 2, ControllerNoteView.DRUM_PAD)));
        assertEquals (Views.DRUM_PAD, fixture.surface.getViewManager ().getPreferredView (2));
    }


    @Test
    void automaticRollRetiresActiveStateAndRestoresManualSettingsAfterLaterReadback ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        final DesiredNoteRepeat automatic = new DesiredNoteRepeat (true, true, NoteRepeatMode.UP, 0, 0.25, 0.5, false, false, true, true);

        fixture.bridge.applyNoteRepeat (automatic);
        for (int tick = 0; tick < 5; tick++)
            fixture.refreshNoteRepeat (tick + 1);

        assertTrue (fixture.noteRepeat.active);
        assertEquals (ArpeggiatorMode.UP, fixture.noteRepeat.mode);
        assertEquals (0, fixture.noteRepeat.octaves);
        assertEquals (0.25, fixture.noteRepeat.period);
        assertFalse (fixture.noteRepeat.latch);
        assertFalse (fixture.noteRepeat.freeRunning);
        assertTrue (fixture.noteRepeat.usePressure);
        assertTrue (fixture.noteRepeat.shuffle);

        fixture.bridge.applyNoteRepeat (DesiredNoteRepeat.unowned ());
        for (int tick = 0; tick < 5; tick++)
            fixture.refreshNoteRepeat (tick + 10);

        assertFalse (fixture.noteRepeat.active);
        assertFalse (fixture.configuration.isNoteRepeatActive ());
        assertEquals (1, fixture.configuration.activeWriteCount);
        assertEquals (ArpeggiatorMode.RANDOM, fixture.noteRepeat.mode);
        assertEquals (2, fixture.noteRepeat.octaves);
        assertEquals (1.0 / 3.0, fixture.noteRepeat.period);
        assertEquals (0.25, fixture.noteRepeat.noteLength);
        assertTrue (fixture.noteRepeat.latch);
        assertTrue (fixture.noteRepeat.freeRunning);
        assertFalse (fixture.noteRepeat.usePressure);
        assertFalse (fixture.noteRepeat.shuffle);

        fixture.configuration.toggleNoteRepeatActive ();
        fixture.configuration.advanceHost ();
        fixture.refreshNoteRepeat (100);
        assertTrue (fixture.configuration.isNoteRepeatActive ());
        assertTrue (fixture.noteRepeat.active);
    }


    @Test
    void automaticRollDoesNotLeakIntoAFormerlyInactiveManualRepeatState ()
    {
        final BridgeFixture fixture = new BridgeFixture (false);
        final DesiredNoteRepeat automatic = new DesiredNoteRepeat (true, true, NoteRepeatMode.UP, 0, 0.25, 0.5, false, false, true, true);

        fixture.bridge.applyNoteRepeat (automatic);
        for (int tick = 0; tick < 5; tick++)
            fixture.refreshNoteRepeat (tick + 1);

        fixture.bridge.applyNoteRepeat (DesiredNoteRepeat.unowned ());
        for (int tick = 0; tick < 5; tick++)
            fixture.refreshNoteRepeat (tick + 10);

        assertFalse (fixture.noteRepeat.active);
    }


    @Test
    void publishesOnlyRequestedDomainsAndClearsThemWhenUnsubscribed ()
    {
        final BridgeFixture fixture = new BridgeFixture ();

        assertFalse (fixture.bridge.refresh (1, DesiredBridgeSubscriptions.empty (), DesiredParameterBanks.empty ()));
        assertEquals (ControllerBridgeSnapshot.empty (), fixture.bridge.snapshot ());
        assertEquals (0, fixture.selected.snapshotCount);
        assertEquals (0, fixture.transport.snapshotReadCount);

        fixture.application.engineActive = true;
        assertTrue (fixture.bridge.refresh (2, subscriptions (BridgeSubscription.TRANSPORT, BridgeSubscription.SELECTED_TRACK), DesiredParameterBanks.empty ()));
        assertTrue (fixture.bridge.snapshot ().transport ().available ());
        assertTrue (fixture.bridge.snapshot ().transport ().engineActive ());
        assertTrue (fixture.bridge.snapshot ().selectedTrack ().exists ());
        assertEquals (1, fixture.selected.snapshotCount);
        assertTrue (fixture.transport.snapshotReadCount > 0);

        final int transportReads = fixture.transport.snapshotReadCount;
        assertTrue (fixture.bridge.refresh (3, DesiredBridgeSubscriptions.empty (), DesiredParameterBanks.empty ()));
        assertEquals (ControllerBridgeSnapshot.empty (), fixture.bridge.snapshot ());
        assertEquals (1, fixture.selected.snapshotCount);
        assertEquals (transportReads, fixture.transport.snapshotReadCount);
    }


    @Test
    void publishesSemanticMappingFeedbackOnlyWhileItsDomainIsRequested ()
    {
        final BridgeFixture fixture = new BridgeFixture ();

        fixture.bridge.refresh (1, DesiredBridgeSubscriptions.empty (), DesiredParameterBanks.empty ());
        assertFalse (fixture.bridge.snapshot ().controllerMappingFeedback ().available ());
        assertTrue (fixture.bridge.snapshot ().controllerMappingFeedback ().targets ().isEmpty ());

        assertTrue (fixture.bridge.refresh (2, subscriptions (BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK), DesiredParameterBanks.empty ()));
        assertTrue (fixture.bridge.snapshot ().controllerMappingFeedback ().available ());
        final Set<ControllerMappingId> installed = new HashSet<> (CoreControllerMappings.DRUM_CONTROL_PADS);
        installed.addAll (CoreControllerMappings.TRACK_CONTROL_PADS);
        assertEquals (installed, fixture.bridge.snapshot ().controllerMappingFeedback ().targets ().keySet ());
        assertTrue (fixture.bridge.snapshot ().controllerMappingFeedback ().targets ().values ().stream ().allMatch (target -> !target.hasTarget () && target.value () == 0.8));
        assertTrue (fixture.bridge.snapshot ().controllerMappingFeedback ().storage ().available ());

        assertTrue (fixture.bridge.refresh (3, DesiredBridgeSubscriptions.empty (), DesiredParameterBanks.empty ()));
        assertFalse (fixture.bridge.snapshot ().controllerMappingFeedback ().available ());
        assertTrue (fixture.bridge.snapshot ().controllerMappingFeedback ().targets ().isEmpty ());
        assertFalse (fixture.bridge.snapshot ().controllerMappingFeedback ().storage ().available ());
    }


    @Test
    void mappingStorageApplyRechecksSelectedOwnerAndWaitsForLaterHostObservation ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        final ControllerMappingContext original = fixture.mappingContext ();
        assertTrue (fixture.bridge.controllerMappingContextMatches (original));
        assertFalse (fixture.bridge.controllerMappingContextMatches (ControllerMappingContext.empty ()));
        final ControllerBridge.PreparedAction prepared = fixture.bridge.prepare (new SetControllerMappingStorageEffect (original, "", "owner-a payload"));

        fixture.selected.switchTo (2, "track-b");
        assertFalse (fixture.bridge.controllerMappingContextMatches (original));
        fixture.bridge.apply (prepared);
        assertTrue (fixture.mappingStorage.submitted.isEmpty ());

        final ControllerMappingContext current = fixture.mappingContext ();
        fixture.bridge.apply (fixture.bridge.prepare (new SetControllerMappingStorageEffect (current, "", "owner-b payload")));
        assertEquals (List.of ("owner-b payload"), fixture.mappingStorage.submitted);
        fixture.bridge.refresh (2, subscriptions (BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK), DesiredParameterBanks.empty ());
        assertEquals ("", fixture.bridge.snapshot ().controllerMappingFeedback ().storage ().value (), "a submission is not persisted readback");

        fixture.mappingStorage.currentValue = "owner-b payload";
        assertFalse (fixture.bridge.controllerMappingContextMatches (current), "live storage changed before its observer revision arrived");
        fixture.mappingStorage.deliver ();
        fixture.bridge.refresh (3, subscriptions (BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK), DesiredParameterBanks.empty ());
        assertEquals ("owner-b payload", fixture.bridge.snapshot ().controllerMappingFeedback ().storage ().value ());
        assertFalse (fixture.bridge.controllerMappingContextMatches (current));
        assertTrue (fixture.bridge.controllerMappingContextMatches (fixture.mappingContext ()));
    }


    @Test
    void mappingStorageRevisionAndDocumentChangesInvalidatePreparedWrites ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        final ControllerMappingContext initial = fixture.mappingContext ();
        final ControllerBridge.PreparedAction beforeDelivery = fixture.bridge.prepare (new SetControllerMappingStorageEffect (initial, "", "stale revision"));
        fixture.mappingStorage.deliver ();
        assertFalse (fixture.bridge.controllerMappingContextMatches (initial));
        fixture.bridge.apply (beforeDelivery);
        assertTrue (fixture.mappingStorage.submitted.isEmpty ());

        final ControllerMappingContext beforeSwitch = fixture.mappingContext ();
        final ControllerBridge.PreparedAction beforeDocument = fixture.bridge.prepare (new SetControllerMappingStorageEffect (beforeSwitch, "", "stale document"));
        fixture.mappingStorage.documentId = "document-b";
        assertFalse (fixture.bridge.controllerMappingContextMatches (beforeSwitch));
        fixture.bridge.apply (beforeDocument);
        assertTrue (fixture.mappingStorage.submitted.isEmpty ());

        final ControllerMappingContext current = fixture.mappingContext ();
        assertEquals ("document-b", current.documentId ());
        assertTrue (fixture.bridge.controllerMappingContextMatches (current));
        fixture.bridge.apply (fixture.bridge.prepare (new SetControllerMappingStorageEffect (current, "wrong baseline", "rejected")));
        assertTrue (fixture.mappingStorage.submitted.isEmpty ());
    }


    @Test
    void projectNavigationWaitsForAuthoritativeIdentityAndLearnsABoundary ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        final DesiredBridgeSubscriptions master = subscriptions (BridgeSubscription.MASTER);
        fixture.bridge.refresh (1, master, DesiredParameterBanks.empty ());

        fixture.bridge.apply (fixture.bridge.prepare (new NavigateProjectEffect ("project-a", ProjectNavigationDirection.PREVIOUS)));
        assertEquals (1, fixture.project.previousCount);
        assertEquals (0, fixture.project.nextCount);

        final ControllerBridge.PreparedAction ignoredWhilePending = fixture.bridge.prepare (new NavigateProjectEffect ("project-a", ProjectNavigationDirection.NEXT));
        fixture.bridge.apply (ignoredWhilePending);
        assertEquals (0, fixture.project.nextCount);

        for (int tick = 0; tick < 100; tick++)
            fixture.bridge.refresh (2 + tick, master, DesiredParameterBanks.empty ());
        assertFalse (fixture.bridge.snapshot ().master ().commandPending ());
        assertFalse (fixture.bridge.snapshot ().master ().canPrevious ());
        assertTrue (fixture.bridge.snapshot ().master ().canNext ());
    }


    @Test
    void successfulNavigationAndEngineChangesRequireLaterHostReadback ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        final DesiredBridgeSubscriptions master = subscriptions (BridgeSubscription.MASTER);
        fixture.bridge.refresh (1, master, DesiredParameterBanks.empty ());

        fixture.bridge.apply (fixture.bridge.prepare (new NavigateProjectEffect ("project-a", ProjectNavigationDirection.NEXT)));
        fixture.project.identity = "project-b";
        fixture.bridge.refresh (2, master, DesiredParameterBanks.empty ());
        assertTrue (fixture.bridge.snapshot ().master ().commandPending ());
        fixture.bridge.refresh (3, master, DesiredParameterBanks.empty ());
        assertFalse (fixture.bridge.snapshot ().master ().commandPending ());
        assertTrue (fixture.bridge.snapshot ().master ().canPrevious ());

        fixture.bridge.apply (fixture.bridge.prepare (new SetProjectEngineEffect ("project-b", true)));
        assertEquals (1, fixture.application.engineWriteCount);
        assertFalse (fixture.application.engineActive);
        fixture.bridge.refresh (4, master, DesiredParameterBanks.empty ());
        assertTrue (fixture.bridge.snapshot ().master ().commandPending ());
        fixture.application.engineActive = true;
        fixture.bridge.refresh (5, master, DesiredParameterBanks.empty ());
        assertFalse (fixture.bridge.snapshot ().master ().commandPending ());
        assertTrue (fixture.bridge.snapshot ().master ().engineActive ());
    }


    @Test
    void remoteProjectTransportSurvivesCoreQuarantineAndReturnsAfterReadback ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        final DesiredBridgeSubscriptions requested = subscriptions (BridgeSubscription.PROJECT, BridgeSubscription.TRANSPORT);
        fixture.project.identity = "project-b";
        fixture.application.engineActive = false;
        fixture.transport.playing = true;
        fixture.bridge.refresh (1, requested, DesiredParameterBanks.empty ());

        fixture.bridge.apply (fixture.bridge.prepare (new SetProjectTransportStateEffect (
            "project-b", "project-a", TransportState.PLAYING, false)));
        assertEquals (1, fixture.project.previousCount);
        fixture.bridge.abandonActiveCore ();
        assertTrue (fixture.bridge.canReplaceActiveCore ());

        fixture.project.identity = "project-a";
        fixture.application.engineActive = true;
        fixture.bridge.refresh (2, DesiredBridgeSubscriptions.empty (), DesiredParameterBanks.empty ());
        fixture.bridge.refresh (3, requested, DesiredParameterBanks.empty ());
        assertEquals (1, fixture.transport.stopCount);
        assertTrue (fixture.transport.playing);
        assertTrue (fixture.bridge.snapshot ().project ().commandPending ());

        fixture.bridge.refresh (4, requested, DesiredParameterBanks.empty ());
        assertEquals (0, fixture.project.nextCount);
        fixture.transport.playing = false;
        fixture.bridge.refresh (5, requested, DesiredParameterBanks.empty ());
        assertEquals (1, fixture.project.nextCount);

        fixture.project.identity = "project-b";
        fixture.application.engineActive = false;
        fixture.bridge.refresh (6, requested, DesiredParameterBanks.empty ());
        fixture.bridge.refresh (7, requested, DesiredParameterBanks.empty ());
        assertFalse (fixture.bridge.snapshot ().project ().commandPending ());
        assertEquals ("project-b", fixture.bridge.snapshot ().project ().projectIdentity ());
    }


    @Test
    void timedOutRemoteReturnRetainsTheLaneAndRetriesUntilOriginReadback ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        final DesiredBridgeSubscriptions requested = subscriptions (BridgeSubscription.PROJECT, BridgeSubscription.TRANSPORT);
        fixture.project.identity = "project-b";
        fixture.transport.playing = true;
        fixture.bridge.refresh (1, requested, DesiredParameterBanks.empty ());
        fixture.bridge.apply (fixture.bridge.prepare (new SetProjectTransportStateEffect (
            "project-b", "project-a", TransportState.PLAYING, false)));

        fixture.project.identity = "project-a";
        fixture.application.engineActive = true;
        fixture.bridge.refresh (2, requested, DesiredParameterBanks.empty ());
        fixture.bridge.refresh (3, requested, DesiredParameterBanks.empty ());
        fixture.transport.playing = false;
        fixture.bridge.refresh (4, requested, DesiredParameterBanks.empty ());
        assertEquals (1, fixture.project.nextCount);

        for (int tick = 0; tick < 100; tick++)
            fixture.bridge.refresh (5 + tick, requested, DesiredParameterBanks.empty ());

        assertTrue (fixture.bridge.snapshot ().project ().commandPending ());
        assertTrue (fixture.project.nextCount >= 2, "the exact return is retried after timeout");
        assertTrue (fixture.bridge.canReplaceActiveCore ());

        fixture.project.identity = "project-b";
        fixture.application.engineActive = false;
        fixture.bridge.refresh (106, requested, DesiredParameterBanks.empty ());
        fixture.bridge.refresh (107, requested, DesiredParameterBanks.empty ());

        assertFalse (fixture.bridge.snapshot ().project ().commandPending ());
        assertEquals ("project-b", fixture.bridge.snapshot ().project ().projectIdentity ());
    }


    @Test
    void unexpectedProjectChangeRetainsTheLaneUntilOriginReadback ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        final DesiredBridgeSubscriptions requested = subscriptions (BridgeSubscription.PROJECT, BridgeSubscription.TRANSPORT);
        fixture.project.identity = "project-b";
        fixture.transport.playing = true;
        fixture.bridge.refresh (1, requested, DesiredParameterBanks.empty ());
        fixture.bridge.apply (fixture.bridge.prepare (new SetProjectTransportStateEffect (
            "project-b", "project-a", TransportState.PLAYING, false)));

        fixture.project.identity = "project-a";
        fixture.application.engineActive = true;
        fixture.bridge.refresh (2, requested, DesiredParameterBanks.empty ());
        fixture.bridge.refresh (3, requested, DesiredParameterBanks.empty ());
        assertEquals (1, fixture.transport.stopCount);

        fixture.project.identity = "project-c";
        fixture.bridge.refresh (4, requested, DesiredParameterBanks.empty ());
        assertTrue (fixture.bridge.snapshot ().project ().commandPending ());
        assertTrue (fixture.bridge.canReplaceActiveCore ());

        fixture.project.identity = "project-b";
        fixture.application.engineActive = false;
        fixture.bridge.refresh (5, requested, DesiredParameterBanks.empty ());
        fixture.bridge.refresh (6, requested, DesiredParameterBanks.empty ());

        assertFalse (fixture.bridge.snapshot ().project ().commandPending ());
        assertEquals ("project-b", fixture.bridge.snapshot ().project ().projectIdentity ());
    }


    @Test
    void failedMidiCleanupCannotPreventParameterRestore ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        final DesiredParameterBanks parameterBanks = new DesiredParameterBanks (Set.of (ParameterBankId.GLOBAL));
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.PARAMETERS), parameterBanks);
        final ParameterTargetRef tempo = fixture.bridge.snapshot ().parameters ().slots ().get (ParameterSlot.TEMPO).target ();
        final DesiredParameterInteraction interaction = new DesiredParameterInteraction (1, false, Map.of (tempo, 120.0), Set.of (), Set.of (), 0);
        final Map<ParameterTargetRef, ControllerBridge.ParameterLease> leases = fixture.bridge.prepareParameterLeases (interaction, parameterBanks);
        fixture.bridge.applyParameterLeases (leases, parameterBanks);
        fixture.bridge.apply (fixture.bridge.prepare (new SetParameterValueEffect (tempo, 98), leases));
        applyMidi (fixture, 0xB1, 74, 99);
        fixture.failNeutralMidi = true;

        fixture.bridge.abandonActiveCore ();

        assertEquals (120, fixture.transport.tempo);
    }


    @Test
    void nativeHistoryCommandsRecheckTheExactProjectAndAvailabilityAtApply ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.application.canUndo = true;
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.PROJECT), DesiredParameterBanks.empty ());
        assertTrue (fixture.bridge.snapshot ().project ().canUndo ());
        assertFalse (fixture.bridge.snapshot ().project ().canRedo ());
        final var undo = fixture.bridge.prepare (new ProjectHistoryEffect ("project-a", ProjectHistoryAction.UNDO));
        assertTrue (fixture.application.historyRequests.isEmpty ());
        fixture.bridge.apply (undo);
        assertEquals (List.of ("undo"), fixture.application.historyRequests);
        assertTrue (fixture.bridge.snapshot ().project ().canUndo (), "command submission cannot rewrite availability");

        fixture.project.identity = "project-b";
        fixture.bridge.apply (undo);
        fixture.bridge.apply (fixture.bridge.prepare (new ProjectHistoryEffect ("project-a", ProjectHistoryAction.UNDO)));
        assertEquals (List.of ("undo"), fixture.application.historyRequests);
        fixture.project.identity = "project-a";
        fixture.application.canUndo = false;
        fixture.bridge.apply (undo);
        assertEquals (List.of ("undo"), fixture.application.historyRequests);

        fixture.application.canRedo = true;
        fixture.bridge.refresh (2, subscriptions (BridgeSubscription.PROJECT), DesiredParameterBanks.empty ());
        assertFalse (fixture.bridge.snapshot ().project ().canUndo ());
        assertTrue (fixture.bridge.snapshot ().project ().canRedo ());
        fixture.bridge.apply (fixture.bridge.prepare (new ProjectHistoryEffect ("project-a", ProjectHistoryAction.REDO)));
        assertEquals (List.of ("undo", "redo"), fixture.application.historyRequests);
    }


    @Test
    void nativeTempoTapRechecksProjectAndEngineAndDoesNotInventTempoReadback ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.application.engineActive = true;
        final DesiredBridgeSubscriptions requested = subscriptions (BridgeSubscription.PROJECT, BridgeSubscription.TRANSPORT);
        fixture.bridge.refresh (1, requested, DesiredParameterBanks.empty ());
        final var prepared = fixture.bridge.prepare (new TapTempoEffect ("project-a"));
        assertEquals (0, fixture.transport.tapCount);
        fixture.bridge.apply (prepared);
        assertEquals (1, fixture.transport.tapCount);
        assertEquals (120, fixture.bridge.snapshot ().transport ().tempo ());

        fixture.project.identity = "project-b";
        fixture.bridge.apply (prepared);
        fixture.bridge.apply (fixture.bridge.prepare (new TapTempoEffect ("project-a")));
        assertEquals (1, fixture.transport.tapCount);
        fixture.project.identity = "project-a";
        fixture.application.engineActive = false;
        fixture.bridge.apply (prepared);
        final var inactive = fixture.bridge.prepare (new TapTempoEffect ("project-a"));
        fixture.application.engineActive = true;
        fixture.bridge.apply (inactive);
        assertEquals (1, fixture.transport.tapCount);

        fixture.transport.tempo = 123.45;
        fixture.bridge.refresh (2, requested, DesiredParameterBanks.empty ());
        assertEquals (123.45, fixture.bridge.snapshot ().transport ().tempo ());
    }


    @Test
    void masterMeterPublishesAuthoritativeReadbackWheneverMasterIsSubscribed ()
    {
        final BridgeFixture fixture = new BridgeFixture ();

        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.MASTER), DesiredParameterBanks.empty ());

        assertEquals (64, fixture.bridge.snapshot ().master ().vuLeft ());
        assertEquals (32, fixture.bridge.snapshot ().master ().vuRight ());
    }


    @Test
    void lightweightProjectSubscriptionDoesNotSampleMasterMeters ()
    {
        final BridgeFixture fixture = new BridgeFixture ();

        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.PROJECT), DesiredParameterBanks.empty ());

        assertTrue (fixture.bridge.snapshot ().project ().available ());
        assertEquals ("project-a", fixture.bridge.snapshot ().project ().projectIdentity ());
        assertEquals (0, fixture.masterVuReadCount);
        assertEquals (de.mossgrabers.pull.core.api.MasterSnapshot.empty (), fixture.bridge.snapshot ().master ());
    }


    @Test
    void projectTransportEffectRechecksVisibleProjectAtApplyTime ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        final DesiredBridgeSubscriptions requested = subscriptions (BridgeSubscription.PROJECT, BridgeSubscription.TRANSPORT);
        fixture.bridge.refresh (1, requested, DesiredParameterBanks.empty ());
        final ControllerBridge.PreparedAction stale = fixture.bridge.prepare (
            new SetProjectTransportStateEffect ("project-a", "project-a", TransportState.PLAYING, true));

        fixture.project.identity = "project-b";
        fixture.bridge.apply (stale);
        assertEquals (0, fixture.transport.playCount);

        fixture.project.identity = "project-a";
        fixture.bridge.refresh (2, requested, DesiredParameterBanks.empty ());
        fixture.bridge.apply (fixture.bridge.prepare (new SetProjectTransportStateEffect ("project-a", "project-a", TransportState.PLAYING, true)));
        assertEquals (1, fixture.transport.playCount);
        assertFalse (fixture.bridge.snapshot ().transport ().playing ());

        fixture.transport.playing = true;
        fixture.bridge.refresh (3, requested, DesiredParameterBanks.empty ());
        assertTrue (fixture.bridge.snapshot ().transport ().playing ());
    }


    @Test
    void appliesRecordingAndArrangerOverdubAsAbsoluteStates ()
    {
        final BridgeFixture fixture = new BridgeFixture ();

        fixture.bridge.apply (fixture.bridge.prepare (new SetTransportStateEffect (TransportState.RECORDING, true)));
        fixture.bridge.apply (fixture.bridge.prepare (new SetTransportStateEffect (TransportState.ARRANGER_OVERDUB, true)));
        fixture.bridge.apply (fixture.bridge.prepare (new SetTransportStateEffect (TransportState.RECORDING, false)));
        fixture.bridge.apply (fixture.bridge.prepare (new SetTransportStateEffect (TransportState.ARRANGER_OVERDUB, false)));

        assertEquals (List.of (
            "setRecording:true",
            "setArrangerOverdub:true",
            "setRecording:false",
            "setArrangerOverdub:false"), fixture.transport.writes);
        assertFalse (fixture.transport.recording);
        assertFalse (fixture.transport.arrangerOverdub);
    }


    @Test
    void commitsExactParameterLeasesIntoTheImmediateHotReloadSnapshot ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        final DesiredParameterBanks parameterBanks = new DesiredParameterBanks (Set.of (ParameterBankId.GLOBAL));
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.PARAMETERS), parameterBanks);
        final ParameterTargetRef tempo = fixture.bridge.snapshot ().parameters ().slots ().get (ParameterSlot.TEMPO).target ();
        final DesiredParameterInteraction interaction = new DesiredParameterInteraction (1, false, Map.of (tempo, 120.0), Set.of (), Set.of (), 0);
        final Map<ParameterTargetRef, ControllerBridge.ParameterLease> prepared = fixture.bridge.prepareParameterLeases (interaction, parameterBanks);
        final ControllerBridge.PreparedAction restore = fixture.bridge.prepare (new SetParameterValueEffect (tempo, 98), prepared);

        assertTrue (fixture.bridge.applyParameterLeases (prepared, parameterBanks));
        assertEquals (Map.of (tempo, 120.0), fixture.bridge.snapshot ().parameters ().retainedBaselines ());
        fixture.bridge.apply (restore);
        assertEquals (98, fixture.transport.tempo);
    }


    @Test
    void immediateParameterReconciliationCarriesIdentityWhenTheCurrentBankSnapshotIsOlder ()
    {
        final MutableMixWindow window = new MutableMixWindow ();
        final BridgeFixture fixture = new BridgeFixture (true, window);
        final DesiredParameterBanks banks = new DesiredParameterBanks (Set.of (ParameterBankId.TRACK_VOLUME));
        final DesiredBridgeSubscriptions requested = subscriptions (BridgeSubscription.CURRENT_TRACK_BANK, BridgeSubscription.PARAMETERS);
        fixture.bridge.refresh (1, requested, banks);
        final var before = fixture.bridge.snapshot ();
        assertEquals ("a-0", before.currentTrackBank ().tracks ().getFirst ().track ().channelId ());
        assertEquals ("a-0", before.parameters ().slots ().get (ParameterSlot.trackVolume (0)).identity ().ownerId ());

        window.prefix = "b";
        assertTrue (fixture.bridge.applyParameterLeases (Map.of (), banks));
        final var betweenSamples = fixture.bridge.snapshot ();
        assertEquals (before.currentTrackBank (), betweenSamples.currentTrackBank ());
        assertEquals ("b-0", betweenSamples.parameters ().slots ().get (ParameterSlot.trackVolume (0)).identity ().ownerId ());
        assertEquals ("channel-volume", betweenSamples.parameters ().slots ().get (ParameterSlot.trackVolume (0)).identity ().domain ());
        assertNotEquals (betweenSamples.currentTrackBank ().tracks ().getFirst ().track ().channelId (), betweenSamples.parameters ().slots ().get (ParameterSlot.trackVolume (0)).identity ().ownerId (), "the core must be able to reject this mixed-epoch pairing");

        fixture.bridge.refresh (2, requested, banks);
        final var after = fixture.bridge.snapshot ();
        assertEquals (after.currentTrackBank ().tracks ().getFirst ().track ().channelId (), after.parameters ().slots ().get (ParameterSlot.trackVolume (0)).identity ().ownerId ());
    }


    @Test
    void rejectsPreparedSelectedTrackActionAfterTargetHandoff ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.SELECTED_TRACK), DesiredParameterBanks.empty ());
        final ControllerBridge.PreparedAction prepared = fixture.bridge.prepare (
            new SetSelectedTrackBooleanEffect (1, "track-a", SelectedTrackBoolean.RECORD_ARMED, true));

        fixture.selected.switchTo (2, "track-b");
        fixture.bridge.apply (prepared);

        assertEquals (0, fixture.selected.armedWriteCount);
    }


    @Test
    void createsANewClipThroughTheDisplayIndependentSelectedTrackAction ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.SELECTED_TRACK), DesiredParameterBanks.empty ());

        fixture.bridge.apply (fixture.bridge.prepare (
            new SelectedTrackActionEffect (1, "track-a", SelectedTrackAction.CREATE_NEW_CLIP)));

        assertEquals (1, fixture.newClipCount);
    }


    @Test
    void keepsQuantizedAndImmediateSelectedTrackStopActuatorsDistinct ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.SELECTED_TRACK), DesiredParameterBanks.empty ());

        fixture.bridge.apply (fixture.bridge.prepare (
            new SelectedTrackActionEffect (1, "track-a", SelectedTrackAction.STOP)));
        fixture.bridge.apply (fixture.bridge.prepare (
            new SelectedTrackActionEffect (1, "track-a", SelectedTrackAction.STOP_IMMEDIATELY)));

        assertEquals (1, fixture.selected.stopCount);
        assertEquals (1, fixture.selected.immediateStopCount);
    }


    @Test
    void rechecksDrumDeviceBankAndPadIdentityAtApplyTime ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.DRUM_PADS), DesiredParameterBanks.empty ());
        final DrumContextSnapshot drum = fixture.bridge.snapshot ().drum ();
        final ControllerBridge.PreparedAction prepared = fixture.bridge.prepare (
            new SelectDrumPadEffect (drum.generation (), drum.targetChannelId (), 0));

        fixture.drum.deviceID = "device-b";
        fixture.bridge.apply (prepared);
        fixture.drum.deviceID = "device-a";
        fixture.drum.baseMidiNote = 48;
        fixture.bridge.apply (prepared);
        fixture.drum.baseMidiNote = 36;
        fixture.drum.padChannelID = "pad-b";
        fixture.bridge.apply (prepared);
        assertEquals (0, fixture.drum.selectionCount);

        fixture.drum.padChannelID = "pad-a";
        fixture.bridge.apply (prepared);
        assertEquals (1, fixture.drum.selectionCount);
    }


    @Test
    void drumWindowMovementRechecksLiveIdentityAndDoesNotInventBankReadBack ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        final DesiredBridgeSubscriptions requested = subscriptions (BridgeSubscription.DRUM_PADS, BridgeSubscription.CONTROLLER_LAYOUT);
        fixture.bridge.refresh (1, requested, DesiredParameterBanks.empty ());
        final DrumContextSnapshot initial = fixture.bridge.snapshot ().drum ();
        final ControllerBridge.PreparedAction action = fixture.bridge.prepare (new SetDrumBankPositionEffect (initial.generation (), initial.targetChannelId (), 52, false));

        fixture.drum.deviceID = "replacement";
        fixture.bridge.apply (action);
        fixture.drum.deviceID = "device-a";
        fixture.drum.baseMidiNote = 48;
        fixture.bridge.apply (action);
        assertTrue (fixture.drum.scrollRequests.isEmpty ());

        fixture.drum.baseMidiNote = 36;
        fixture.bridge.apply (action);
        assertEquals (List.of (Integer.valueOf (52)), fixture.drum.scrollRequests);
        fixture.bridge.refresh (100_000_000, requested, DesiredParameterBanks.empty ());
        assertEquals (52, fixture.bridge.snapshot ().layout ().drumBaseMidiNote ());
        assertEquals (36, fixture.bridge.snapshot ().drum ().baseMidiNote ());
        assertFalse (fixture.bridge.snapshot ().layout ().appliedNoteTranslation ().owned ());

        fixture.drum.baseMidiNote = 52;
        fixture.bridge.refresh (200_000_000, requested, DesiredParameterBanks.empty ());
        assertEquals (52, fixture.bridge.snapshot ().drum ().baseMidiNote ());
        assertThrows (IllegalArgumentException.class, () -> fixture.bridge.prepare (new SetDrumBankPositionEffect (initial.generation (), initial.targetChannelId (), 68, false)));
    }


    @Test
    void hostNotificationTransmitsCoreTextWithoutAddingControllerPolicy ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.bridge.apply (fixture.bridge.prepare (new ShowHostNotificationEffect ("C2 - D#3")));
        assertEquals (List.of ("C2 - D#3"), fixture.notifications);
    }


    @Test
    void publishesThePlayableMainDrumWindowInsteadOfTheLegacy64PadWindow ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.legacyDrum.deviceID = "legacy-device";
        fixture.legacyDrum.baseMidiNote = 0;

        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.DRUM_PADS), DesiredParameterBanks.empty ());

        final DrumContextSnapshot drum = fixture.bridge.snapshot ().drum ();
        assertTrue (drum.available ());
        assertEquals ("device-a", drum.deviceId ());
        assertEquals (36, drum.baseMidiNote ());
        assertEquals (1, drum.pads ().size ());
        assertEquals (0, fixture.legacyDrum.selectionCount);
    }


    @Test
    void neutralizesEveryStatefulMidiFamilyOnCoreHandoff ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.SELECTED_TRACK), DesiredParameterBanks.empty ());
        fixture.bridge.activateCoreGeneration (1);

        applyMidi (fixture, 0xB3, 74, 99);
        applyMidi (fixture, 0xA3, 60, 75);
        applyMidi (fixture, 0xD4, 80, 17);
        applyMidi (fixture, 0xE2, 5, 100);
        fixture.bridge.activateCoreGeneration (2);

        assertEquals (8, fixture.noteInputMidiMessages.size ());
        assertEquals (Set.of (
            new MidiMessage (0xB3, 74, 0),
            new MidiMessage (0xA3, 60, 0),
            new MidiMessage (0xD4, 0, 0),
            new MidiMessage (0xE2, 0, 64)),
            new HashSet<> (fixture.noteInputMidiMessages.subList (4, 8)));
    }


    @Test
    void abandoningAFaultedCoreRestoresRetainedParametersAndNeutralizesMidi ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        final DesiredParameterBanks parameterBanks = new DesiredParameterBanks (Set.of (ParameterBankId.GLOBAL));
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.PARAMETERS), parameterBanks);
        final ParameterTargetRef tempo = fixture.bridge.snapshot ().parameters ().slots ().get (ParameterSlot.TEMPO).target ();
        final DesiredParameterInteraction interaction = new DesiredParameterInteraction (1, false, Map.of (tempo, 120.0), Set.of (), Set.of (), 0);
        final Map<ParameterTargetRef, ControllerBridge.ParameterLease> leases = fixture.bridge.prepareParameterLeases (interaction, parameterBanks);
        fixture.bridge.applyParameterLeases (leases, parameterBanks);
        fixture.bridge.apply (fixture.bridge.prepare (new SetParameterValueEffect (tempo, 98), leases));
        applyMidi (fixture, 0xB1, 74, 99);

        fixture.bridge.abandonActiveCore ();

        assertEquals (120, fixture.transport.tempo);
        assertEquals (List.of (
            new MidiMessage (0xB1, 74, 99),
            new MidiMessage (0xB1, 74, 0)), fixture.noteInputMidiMessages);
    }


    @Test
    void neutralizesStatefulMidiWhenTheSelectedTargetChanges ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.SELECTED_TRACK), DesiredParameterBanks.empty ());
        applyMidi (fixture, 0xB1, 1, 127);

        fixture.selected.switchTo (2, "track-b");
        fixture.bridge.refresh (2, DesiredBridgeSubscriptions.empty (), DesiredParameterBanks.empty ());

        assertEquals (List.of (
            new MidiMessage (0xB1, 1, 127),
            new MidiMessage (0xB1, 1, 0)), fixture.noteInputMidiMessages);
    }


    private static void applyMidi (final BridgeFixture fixture, final int status, final int data1, final int data2)
    {
        fixture.bridge.apply (fixture.bridge.prepare (
            new SendNoteInputMidiEffect (status, data1, data2)));
    }


    private static DesiredBridgeSubscriptions subscriptions (final BridgeSubscription... subscriptions)
    {
        return new DesiredBridgeSubscriptions (Set.of (subscriptions));
    }


    @Test
    void applicationUiIsRequestedReadbackAndSurvivesParameterOnlyPublication ()
    {
        final BridgeFixture fixture = new BridgeFixture (true, new MutableMixWindow ());
        fixture.bridge.refresh (1, DesiredBridgeSubscriptions.empty (), DesiredParameterBanks.empty ());
        assertEquals (de.mossgrabers.pull.core.api.ApplicationUiSnapshot.empty (), fixture.bridge.snapshot ().applicationUi ());
        final var requested = new DesiredBridgeSubscriptions (Set.of (BridgeSubscription.APPLICATION_UI, BridgeSubscription.PARAMETERS));
        fixture.bridge.refresh (2, requested, DesiredParameterBanks.empty ());
        final var first = fixture.bridge.snapshot ().applicationUi ();
        assertTrue (first.available ());
        assertEquals ("ARRANGE", first.panelLayout ());
        final var layout = fixture.bridge.prepare (new de.mossgrabers.pull.core.api.effect.SetApplicationLayoutEffect (first.context (), de.mossgrabers.pull.core.api.effect.SetApplicationLayoutEffect.Layout.MIX));
        fixture.bridge.apply (layout);
        assertEquals (List.of ("MIX"), fixture.application.panelRequests);
        fixture.bridge.refresh (3, requested, DesiredParameterBanks.empty ());
        assertEquals (first, fixture.bridge.snapshot ().applicationUi ());
        fixture.application.panelLayout = "MIX";
        fixture.bridge.refresh (4, requested, DesiredParameterBanks.empty ());
        final var observed = fixture.bridge.snapshot ().applicationUi ();
        assertEquals ("MIX", observed.panelLayout ());
        assertTrue (fixture.bridge.applyParameterLeases (Map.of (), new DesiredParameterBanks (Set.of (ParameterBankId.TRACK_VOLUME))));
        assertEquals (observed, fixture.bridge.snapshot ().applicationUi ());
        fixture.bridge.refresh (5, DesiredBridgeSubscriptions.empty (), DesiredParameterBanks.empty ());
        assertEquals (de.mossgrabers.pull.core.api.ApplicationUiSnapshot.empty (), fixture.bridge.snapshot ().applicationUi ());
        assertThrows (IllegalArgumentException.class, () -> fixture.bridge.prepare (new de.mossgrabers.pull.core.api.effect.ToggleApplicationPanelEffect (observed.context (), de.mossgrabers.pull.core.api.effect.ToggleApplicationPanelEffect.Panel.MIXER)));
    }


    private static final class BridgeFixture
    {
        private final MutableSelectedTarget selected = new MutableSelectedTarget ();
        private final MutableTransport transport = new MutableTransport ();
        private final MutableDrum drum = new MutableDrum (this.selected);
        private final MutableDrum legacyDrum = new MutableDrum (this.selected);
        private final MutableProject project = new MutableProject ();
        private final MutableApplication application = new MutableApplication ();
        private final MutableMappingStorage mappingStorage = new MutableMappingStorage ();
        private final List<MidiMessage> noteInputMidiMessages = new ArrayList<> ();
        private final List<String> notifications = new ArrayList<> ();
        private final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        private final MutableNoteRepeat noteRepeat;
        private final ManualRepeatConfiguration configuration;
        private final PushControlSurface surface;
        private final BoundedControllerBridge bridge;
        private final ControllerMappingStorageHost mappingStorageHost;
        private int newClipCount;
        private int masterVuReadCount;
        private boolean failNeutralMidi;


        private BridgeFixture ()
        {
            this (true);
        }


        private BridgeFixture (final boolean manualRepeatActive)
        {
            this (manualRepeatActive, null);
        }


        private BridgeFixture (final boolean manualRepeatActive, final MutableMixWindow mixWindow)
        {
            this.noteRepeat = new MutableNoteRepeat (manualRepeatActive);
            final ITransport transportProxy = this.transport.proxy ();
            final ICursorTrack cursorTrack = this.drum.cursorTrack ();
            final IDrumDevice drumDevice = this.drum.device ();
            final IDrumDevice legacyDrumDevice = this.legacyDrum.device ();
            final Scales scales = new Scales (this.valueChanger, 36, 100, 8, 8);
            final IHost host = proxy (IHost.class, (ignored, method, arguments) -> {
                if ("showNotification".equals (method.getName ()))
                    this.notifications.add ((String) arguments[0]);
                return relaxedValue (method.getReturnType ());
            });
            final IApplication applicationProxy = this.application.proxy ();
            final de.mossgrabers.framework.daw.IArranger arrangerProxy = relaxedProxy (de.mossgrabers.framework.daw.IArranger.class);
            final de.mossgrabers.framework.daw.IMixer mixerProxy = relaxedProxy (de.mossgrabers.framework.daw.IMixer.class);
            final ITrackBank fullBank = mixWindow == null ? relaxedProxy (ITrackBank.class) : mixWindow.bank;
            final ITrackBank upperBank = relaxedProxy (ITrackBank.class);
            final ITrackBank effectBank = relaxedProxy (ITrackBank.class);
            final IModel model = proxy (IModel.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "getTrackBank" -> arguments != null && arguments.length == 2 && ((Integer) arguments[1]).intValue () == 4 ? upperBank : fullBank;
                case "getCurrentTrackBank" -> fullBank;
                case "getEffectTrackBank" -> effectBank;
                case "getHost" -> host;
                case "getTransport" -> transportProxy;
                case "getCursorTrack" -> cursorTrack;
                case "getDrumDevice" -> arguments == null || arguments.length == 0 ? drumDevice : legacyDrumDevice;
                case "getScales" -> scales;
                case "getValueChanger" -> this.valueChanger;
                case "getProject" -> this.project.proxy ();
                case "getApplication" -> applicationProxy;
                case "getArranger" -> arrangerProxy;
                case "getMixer" -> mixerProxy;
                case "getMasterTrack" -> this.masterTrack ();
                case "createNoteClip" -> {
                    this.newClipCount++;
                    yield null;
                }
                default -> relaxedValue (method.getReturnType ());
            });
            this.surface = createSurface (this.selected, cursorTrack, this.valueChanger, this.noteRepeat, manualRepeatActive);
            final SessionBankShape fullSession = new SessionBankShape (8, 8);
            this.surface.setSessionBankRegistry (new SessionBankRegistry (model, Set.of (fullSession, new SessionBankShape (8, 4)), fullSession));
            this.configuration = (ManualRepeatConfiguration) this.surface.getConfiguration ();
            this.mappingStorageHost = new ControllerMappingStorageHost (this.mappingStorage.document (), () -> model.getMasterTrack ().getChannelID ());
            this.mappingStorage.deliver ();
            this.bridge = new BoundedControllerBridge (
                model,
                this.selected,
                this::sendNoteInputMidi,
                this.surface,
                this.valueChanger,
                new RuntimeLog ()
                {
                    @Override
                    public void info (final String message)
                    {
                        // No test diagnostics.
                    }


                    @Override
                    public void warn (final String message)
                    {
                        // No test diagnostics.
                    }
                },
                new ControllerMappingHost (this.surface, this.mappingStorageHost));
        }


        private ControllerMappingContext mappingContext ()
        {
            final var storage = this.mappingStorageHost.snapshot ();
            return new ControllerMappingContext (this.selected.getGeneration (), this.selected.getChannelID (), storage.revision (), storage.documentId ());
        }


        private void refreshNoteRepeat (final long time)
        {
            this.bridge.refresh (time, subscriptions (BridgeSubscription.NOTE_REPEAT), DesiredParameterBanks.empty ());
            this.configuration.advanceHost ();
            this.noteRepeat.advanceHost ();
        }


        private void sendNoteInputMidi (final int status, final int data1, final int data2)
        {
            if (this.failNeutralMidi && data2 == 0)
                throw new IllegalStateException ("broken MIDI neutralization");
            this.noteInputMidiMessages.add (new MidiMessage (status, data1, data2));
        }


        private IMasterTrack masterTrack ()
        {
            return proxy (IMasterTrack.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "getName" -> "Master";
                case "getChannelID" -> this.mappingStorage.documentId;
                case "getColor" -> ColorEx.GRAY;
                case "isActivated" -> Boolean.TRUE;
                case "isSelected", "isRecArm" -> Boolean.FALSE;
                case "getVuLeft" -> {
                    this.masterVuReadCount++;
                    yield Integer.valueOf (64);
                }
                case "getVuRight" -> {
                    this.masterVuReadCount++;
                    yield Integer.valueOf (32);
                }
                default -> relaxedValue (method.getReturnType ());
            });
        }
    }


    private static final class MutableMixWindow
    {
        private String prefix = "a";
        private final ITrackBank bank;

        private MutableMixWindow ()
        {
            final List<ITrack> tracks = new ArrayList<> ();
            for (int index = 0; index < 8; index++)
            {
                final int slot = index;
                final de.mossgrabers.framework.parameter.IParameter volume = proxy (de.mossgrabers.framework.parameter.IParameter.class, (proxy, method, args) -> switch (method.getName ())
                {
                    case "doesExist" -> true;
                    case "getName" -> "Volume";
                    case "getValue", "getModulatedValue" -> 64;
                    case "getDisplayedValue" -> "-6.0 dB";
                    default -> relaxedValue (method.getReturnType ());
                });
                tracks.add (proxy (ITrack.class, (proxy, method, args) -> switch (method.getName ())
                {
                    case "doesExist", "isActivated" -> true;
                    case "getPosition" -> slot;
                    case "getChannelID" -> this.prefix + "-" + slot;
                    case "getName" -> "Track " + slot;
                    case "getVolumeParameter" -> volume;
                    case "getColor" -> ColorEx.GRAY;
                    default -> relaxedValue (method.getReturnType ());
                }));
            }
            this.bank = proxy (ITrackBank.class, (proxy, method, args) -> switch (method.getName ())
            {
                case "getPageSize" -> 8;
                case "getItem" -> tracks.get ((Integer) args[0]);
                default -> relaxedValue (method.getReturnType ());
            });
        }
    }


    private static final class MutableMappingStorage
    {
        private String documentId = "document-a";
        private String currentValue = "";
        private final List<String> submitted = new ArrayList<> ();
        private StringValueChangedCallback observer;


        private DocumentState document ()
        {
            final SettableStringValue setting = (SettableStringValue) Proxy.newProxyInstance (SettableStringValue.class.getClassLoader (),
                new Class<?> [] {SettableStringValue.class, Setting.class}, (proxy, method, arguments) -> {
                    switch (method.getName ())
                    {
                        case "get" -> { return this.currentValue; }
                        case "set" -> this.submitted.add ((String) arguments[0]);
                        case "addValueObserver" -> this.observer = (StringValueChangedCallback) arguments[0];
                        default -> { }
                    }
                    return relaxedValue (method.getReturnType ());
                });
            return proxy (DocumentState.class, (proxy, method, arguments) -> method.getName ().equals ("getStringSetting") ? setting : relaxedValue (method.getReturnType ()));
        }


        private void deliver ()
        {
            this.observer.valueChanged (this.currentValue);
        }
    }


    private static final class MutableProject
    {
        private String identity = "project-a";
        private int previousCount;
        private int nextCount;


        private IProject proxy ()
        {
            return BoundedControllerBridgeTest.proxy (IProject.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "getIdentity" -> this.identity;
                case "getName" -> "Show";
                case "isDirty" -> Boolean.FALSE;
                case "previous" -> {
                    this.previousCount++;
                    yield null;
                }
                case "next" -> {
                    this.nextCount++;
                    yield null;
                }
                default -> relaxedValue (method.getReturnType ());
            });
        }
    }


    private static final class MutableApplication
    {
        private String panelLayout = "ARRANGE";
        private final List<String> panelRequests = new ArrayList<> ();
        private boolean engineActive;
        private boolean canUndo;
        private boolean canRedo;
        private final List<String> historyRequests = new ArrayList<> ();
        private int engineWriteCount;


        private IApplication proxy ()
        {
            return BoundedControllerBridgeTest.proxy (IApplication.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "getPanelLayout" -> this.panelLayout;
                case "setPanelLayout" -> { this.panelRequests.add ((String) arguments[0]); yield null; }
                case "isEngineActive" -> Boolean.valueOf (this.engineActive);
                case "canUndo" -> Boolean.valueOf (this.canUndo);
                case "canRedo" -> Boolean.valueOf (this.canRedo);
                case "undo", "redo" -> {
                    this.historyRequests.add (method.getName ());
                    yield null;
                }
                case "setEngineActive" -> {
                    this.engineWriteCount++;
                    yield null;
                }
                default -> relaxedValue (method.getReturnType ());
            });
        }
    }


    private static final class MutableTransport
    {
        private final List<String> writes = new ArrayList<> ();
        private boolean recording;
        private boolean arrangerOverdub;
        private boolean playing;
        private double tempo = 120;
        private int tapCount;
        private int snapshotReadCount;
        private int playCount;
        private int stopCount;


        private ITransport proxy ()
        {
            return BoundedControllerBridgeTest.proxy (ITransport.class, (proxy, method, arguments) -> {
                switch (method.getName ())
                {
                    case "isPlaying":
                        this.snapshotReadCount++;
                        return Boolean.valueOf (this.playing);
                    case "isLauncherOverdub":
                    case "isLoop":
                    case "isMetronomeOn":
                    case "isFillModeActive":
                        this.snapshotReadCount++;
                        return Boolean.FALSE;
                    case "isRecording":
                        this.snapshotReadCount++;
                        return Boolean.valueOf (this.recording);
                    case "isArrangerOverdub":
                        this.snapshotReadCount++;
                        return Boolean.valueOf (this.arrangerOverdub);
                    case "getTempo":
                        this.snapshotReadCount++;
                        return Double.valueOf (this.tempo);
                    case "getPosition":
                        this.snapshotReadCount++;
                        return Double.valueOf (16.0);
                    case "getNumerator":
                        this.snapshotReadCount++;
                        return Integer.valueOf (4);
                    case "getDenominator":
                        this.snapshotReadCount++;
                        return Integer.valueOf (4);
                    case "getMinimumTempo":
                        return Double.valueOf (20.0);
                    case "getMaximumTempo":
                        return Double.valueOf (666.0);
                    case "setRecording":
                        this.recording = ((Boolean) arguments[0]).booleanValue ();
                        this.writes.add ("setRecording:" + this.recording);
                        return null;
                    case "setArrangerOverdub":
                        this.arrangerOverdub = ((Boolean) arguments[0]).booleanValue ();
                        this.writes.add ("setArrangerOverdub:" + this.arrangerOverdub);
                        return null;
                    case "setTempo":
                        this.tempo = ((Number) arguments[0]).doubleValue ();
                        return null;
                    case "play":
                        this.playCount++;
                        return null;
                    case "stop":
                        this.stopCount++;
                        return null;
                    case "toggleRecording":
                    case "toggleOverdub":
                        this.writes.add (method.getName ());
                        return null;
                    case "tapTempo":
                        this.tapCount++;
                        return null;
                    default:
                        return relaxedValue (method.getReturnType ());
                }
            });
        }
    }


    private static final class MutableSelectedTarget extends SelectedTrackNoteTargetAdapter
    {
        private boolean armed;
        private boolean noteInputRouteActive;
        private int snapshotCount;
        private int armedWriteCount;
        private int stopCount;
        private int immediateStopCount;


        @Override
        public void submitNoteInputRoute (final boolean active)
        {
            this.noteInputRouteActive = active;
        }


        @Override
        public SelectedTrackNoteTargetSnapshot snapshot ()
        {
            this.snapshotCount++;
            return new SelectedTrackNoteTargetSnapshot (
                this.generation,
                this.channelID,
                true,
                "Drums",
                0.8,
                0.2,
                0.1,
                "Instrument",
                2,
                true,
                false,
                false,
                false,
                true,
                this.armed,
                SelectedTrackMonitorMode.AUTO,
                false,
                false,
                false,
                false,
                true,
                0.75,
                0.5);
        }


        @Override
        public boolean hasDrumDevice ()
        {
            return true;
        }


        @Override
        public void setArmed (final boolean newArmed)
        {
            this.armedWriteCount++;
            this.armed = newArmed;
        }


        @Override
        public void stop ()
        {
            this.stopCount++;
        }


        @Override
        public void stopImmediately ()
        {
            this.immediateStopCount++;
        }


    }


    private static final class MutableDrum
    {
        private final MutableSelectedTarget selected;
        private String deviceID = "device-a";
        private String padChannelID = "pad-a";
        private int baseMidiNote = 36;
        private int selectionCount;
        private final List<Integer> scrollRequests = new ArrayList<> ();


        private MutableDrum (final MutableSelectedTarget selected)
        {
            this.selected = selected;
        }


        private ICursorTrack cursorTrack ()
        {
            final ISlot slot = relaxedProxy (ISlot.class);
            final ISlotBank slotBank = proxy (ISlotBank.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "getSelectedItem" -> java.util.Optional.empty ();
                case "getEmptySlot" -> java.util.Optional.of (slot);
                default -> relaxedValue (method.getReturnType ());
            });
            return proxy (ICursorTrack.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "doesExist" -> Boolean.TRUE;
                case "getChannelID" -> this.selected.channelID;
                case "getSlotBank" -> slotBank;
                default -> relaxedValue (method.getReturnType ());
            });
        }


        private IDrumDevice device ()
        {
            final IDrumPad pad = proxy (IDrumPad.class, (proxy, method, arguments) -> {
                switch (method.getName ())
                {
                    case "doesExist":
                    case "isActivated":
                    case "hasDevices":
                        return Boolean.TRUE;
                    case "isSelected":
                    case "isMute":
                    case "isSolo":
                        return Boolean.FALSE;
                    case "getChannelID":
                        return this.padChannelID;
                    case "getName":
                        return "Kick";
                    case "getColor":
                        return ColorEx.RED;
                    case "getVolume":
                    case "getPan":
                        return Integer.valueOf (64);
                    case "select":
                        this.selectionCount++;
                        return null;
                    default:
                        return relaxedValue (method.getReturnType ());
                }
            });
            final IDrumPadBank bank = proxy (IDrumPadBank.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "scrollTo" -> {
                    this.scrollRequests.add ((Integer) arguments[0]);
                    yield null;
                }
                case "getPageSize" -> Integer.valueOf (1);
                case "getScrollPosition" -> Integer.valueOf (this.baseMidiNote);
                case "getItem" -> pad;
                default -> relaxedValue (method.getReturnType ());
            });
            return proxy (IDrumDevice.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "doesExist", "hasDrumPads" -> Boolean.TRUE;
                case "getID" -> this.deviceID;
                case "getDrumPadBank" -> bank;
                default -> relaxedValue (method.getReturnType ());
            });
        }
    }


    @SuppressWarnings("unchecked")
    private static PushControlSurface createSurface (final ISelectedTrackNoteTarget selectedTarget, final ITrack drumModelTrack, final IValueChanger valueChanger, final MutableNoteRepeat noteRepeat, final boolean manualRepeatActive)
    {
        final IHwButton button = relaxedProxy (IHwButton.class);
        final IHwLight light = relaxedProxy (IHwLight.class);
        final IHwSurfaceFactory surfaceFactory = proxy (IHwSurfaceFactory.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "createButton" -> button;
            case "createLight" -> light;
            case "installMappedAbsoluteFeedback" -> {
                ((BiConsumer<Boolean, Double>) arguments[1]).accept (Boolean.FALSE, Double.valueOf (0.8));
                yield null;
            }
            default -> relaxedValue (method.getReturnType ());
        });
        final IHost host = proxy (IHost.class, (proxy, method, arguments) -> "createSurfaceFactory".equals (method.getName ()) ? surfaceFactory : relaxedValue (method.getReturnType ()));
        final INoteInput noteInput = proxy (INoteInput.class, (proxy, method, arguments) -> "getNoteRepeat".equals (method.getName ()) ? noteRepeat.proxy () : relaxedValue (method.getReturnType ()));
        final IMidiInput input = proxy (IMidiInput.class, (proxy, method, arguments) -> "getDefaultNoteInput".equals (method.getName ()) ? noteInput : relaxedValue (method.getReturnType ()));
        final IMidiOutput output = relaxedProxy (IMidiOutput.class);
        final PushConfiguration configuration = new ManualRepeatConfiguration (host, valueChanger, manualRepeatActive, noteRepeat);
        return new PushControlSurface (host, new PushColorManager (), configuration, output, input, selectedTarget, drumModelTrack, () -> true, null);
    }


    private static final class ManualRepeatConfiguration extends PushConfiguration
    {
        private final MutableNoteRepeat noteRepeat;
        private boolean active;
        private Boolean pendingActive;
        private int activeWriteCount;


        private ManualRepeatConfiguration (final IHost host, final IValueChanger valueChanger, final boolean active, final MutableNoteRepeat noteRepeat)
        {
            super (host, valueChanger, List.of (ArpeggiatorMode.values ()));
            this.active = active;
            this.noteRepeat = noteRepeat;
        }


        @Override
        public boolean isNoteRepeatActive ()
        {
            return this.active;
        }


        @Override
        public void setNoteRepeatActive (final boolean active)
        {
            assertTrue (this.pendingActive == null, "Repeat Active write must wait for setting read-back");
            this.pendingActive = Boolean.valueOf (active);
            this.activeWriteCount++;
        }


        private void advanceHost ()
        {
            if (this.pendingActive == null)
                return;
            this.active = this.pendingActive.booleanValue ();
            this.noteRepeat.active = this.active;
            this.pendingActive = null;
        }


        @Override
        public ArpeggiatorMode getNoteRepeatMode ()
        {
            return ArpeggiatorMode.RANDOM;
        }


        @Override
        public int getNoteRepeatOctave ()
        {
            return 2;
        }


        @Override
        public Resolution getNoteRepeatPeriod ()
        {
            return Resolution.RES_1_8T;
        }


        @Override
        public Resolution getNoteRepeatLength ()
        {
            return Resolution.RES_1_16;
        }
    }


    private static final class MutableNoteRepeat
    {
        private boolean active;
        private ArpeggiatorMode mode = ArpeggiatorMode.RANDOM;
        private int octaves = 2;
        private double period = 1.0 / 3.0;
        private double noteLength = 0.25;
        private boolean latch = true;
        private boolean freeRunning = true;
        private boolean usePressure;
        private boolean shuffle;
        private boolean freeRunningTogglePending;
        private boolean usePressureTogglePending;
        private boolean shuffleTogglePending;


        private MutableNoteRepeat (final boolean active)
        {
            this.active = active;
        }


        private INoteRepeat proxy ()
        {
            return BoundedControllerBridgeTest.proxy (INoteRepeat.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "isActive" -> Boolean.valueOf (this.active);
                case "setActive" -> {
                    this.active = ((Boolean) arguments[0]).booleanValue ();
                    yield null;
                }
                case "getMode" -> this.mode;
                case "setMode" -> {
                    this.mode = (ArpeggiatorMode) arguments[0];
                    yield null;
                }
                case "getOctaves" -> Integer.valueOf (this.octaves);
                case "setOctaves" -> {
                    this.octaves = ((Number) arguments[0]).intValue ();
                    yield null;
                }
                case "getPeriod" -> Double.valueOf (this.period);
                case "setPeriod" -> {
                    this.period = ((Number) arguments[0]).doubleValue ();
                    yield null;
                }
                case "getNoteLength" -> Double.valueOf (this.noteLength);
                case "setNoteLength" -> {
                    this.noteLength = ((Number) arguments[0]).doubleValue ();
                    yield null;
                }
                case "isLatchActive" -> Boolean.valueOf (this.latch);
                case "setLatchActive" -> {
                    this.latch = ((Boolean) arguments[0]).booleanValue ();
                    yield null;
                }
                case "isFreeRunning" -> Boolean.valueOf (this.freeRunning);
                case "toggleIsFreeRunning" -> {
                    assertFalse (this.freeRunningTogglePending, "Free-running toggle must wait for read-back");
                    this.freeRunningTogglePending = true;
                    yield null;
                }
                case "usePressure" -> Boolean.valueOf (this.usePressure);
                case "toggleUsePressure" -> {
                    assertFalse (this.usePressureTogglePending, "Pressure toggle must wait for read-back");
                    this.usePressureTogglePending = true;
                    yield null;
                }
                case "isShuffle" -> Boolean.valueOf (this.shuffle);
                case "toggleShuffle" -> {
                    assertFalse (this.shuffleTogglePending, "Shuffle toggle must wait for read-back");
                    this.shuffleTogglePending = true;
                    yield null;
                }
                default -> relaxedValue (method.getReturnType ());
            });
        }


        private void advanceHost ()
        {
            if (this.freeRunningTogglePending)
            {
                this.freeRunning = !this.freeRunning;
                this.freeRunningTogglePending = false;
            }
            if (this.usePressureTogglePending)
            {
                this.usePressure = !this.usePressure;
                this.usePressureTogglePending = false;
            }
            if (this.shuffleTogglePending)
            {
                this.shuffle = !this.shuffle;
                this.shuffleTogglePending = false;
            }
        }
    }


    private static <T> T proxy (final Class<T> type, final java.lang.reflect.InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?> []
        {
            type
        }, handler));
    }


    private static <T> T relaxedProxy (final Class<T> type)
    {
        return proxy (type, (proxy, method, arguments) -> relaxedValue (method.getReturnType ()));
    }


    private static Object relaxedValue (final Class<?> type)
    {
        if (type.isInterface ())
            return relaxedProxy (type);
        if (!type.isPrimitive () || void.class.equals (type))
            return null;
        if (boolean.class.equals (type))
            return Boolean.FALSE;
        if (char.class.equals (type))
            return Character.valueOf ('\0');
        if (byte.class.equals (type))
            return Byte.valueOf ((byte) 0);
        if (short.class.equals (type))
            return Short.valueOf ((short) 0);
        if (int.class.equals (type))
            return Integer.valueOf (0);
        if (long.class.equals (type))
            return Long.valueOf (0L);
        if (float.class.equals (type))
            return Float.valueOf (0.0F);
        return Double.valueOf (0.0);
    }


    private record MidiMessage (int status, int data1, int data2)
    {
    }
}
