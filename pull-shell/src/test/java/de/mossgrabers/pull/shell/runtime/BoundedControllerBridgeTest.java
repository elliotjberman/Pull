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

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void admitsOnlyTheInstalledStableButtonConsumptionTarget ()
    {
        final BridgeFixture fixture = new BridgeFixture ();

        fixture.bridge.apply (fixture.bridge.prepare (new ConsumeControllerButtonEffect (PushControlIds.button ("SELECT"))));

        assertThrows (IllegalArgumentException.class, () -> fixture.bridge.prepare (new ConsumeControllerButtonEffect (PushControlIds.button ("BROWSE"))));
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
        assertTrue (fixture.bridge.snapshot ().controllerMappingFeedback ().states ().isEmpty ());

        assertTrue (fixture.bridge.refresh (2, subscriptions (BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK), DesiredParameterBanks.empty ()));
        assertTrue (fixture.bridge.snapshot ().controllerMappingFeedback ().available ());
        assertEquals (Set.copyOf (CoreControllerMappings.DRUM_CONTROL_PADS), fixture.bridge.snapshot ().controllerMappingFeedback ().states ().keySet ());
        assertTrue (fixture.bridge.snapshot ().controllerMappingFeedback ().states ().values ().stream ().noneMatch (Boolean::booleanValue));

        assertTrue (fixture.bridge.refresh (3, DesiredBridgeSubscriptions.empty (), DesiredParameterBanks.empty ()));
        assertFalse (fixture.bridge.snapshot ().controllerMappingFeedback ().available ());
        assertTrue (fixture.bridge.snapshot ().controllerMappingFeedback ().states ().isEmpty ());
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


    private static final class BridgeFixture
    {
        private final MutableSelectedTarget selected = new MutableSelectedTarget ();
        private final MutableTransport transport = new MutableTransport ();
        private final MutableDrum drum = new MutableDrum (this.selected);
        private final MutableDrum legacyDrum = new MutableDrum (this.selected);
        private final MutableProject project = new MutableProject ();
        private final MutableApplication application = new MutableApplication ();
        private final List<MidiMessage> noteInputMidiMessages = new ArrayList<> ();
        private final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        private final MutableNoteRepeat noteRepeat;
        private final ManualRepeatConfiguration configuration;
        private final PushControlSurface surface;
        private final BoundedControllerBridge bridge;
        private int newClipCount;
        private int masterVuReadCount;
        private boolean failNeutralMidi;


        private BridgeFixture ()
        {
            this (true);
        }


        private BridgeFixture (final boolean manualRepeatActive)
        {
            this.noteRepeat = new MutableNoteRepeat (manualRepeatActive);
            final ITransport transportProxy = this.transport.proxy ();
            final ICursorTrack cursorTrack = this.drum.cursorTrack ();
            final IDrumDevice drumDevice = this.drum.device ();
            final IDrumDevice legacyDrumDevice = this.legacyDrum.device ();
            final Scales scales = new Scales (this.valueChanger, 36, 100, 8, 8);
            final IModel model = proxy (IModel.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "getTransport" -> transportProxy;
                case "getCursorTrack" -> cursorTrack;
                case "getDrumDevice" -> arguments == null || arguments.length == 0 ? drumDevice : legacyDrumDevice;
                case "getScales" -> scales;
                case "getValueChanger" -> this.valueChanger;
                case "getProject" -> this.project.proxy ();
                case "getApplication" -> this.application.proxy ();
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
                new ControllerMappingHost (this.surface));
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
        private boolean engineActive;
        private int engineWriteCount;


        private IApplication proxy ()
        {
            return BoundedControllerBridgeTest.proxy (IApplication.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "isEngineActive" -> Boolean.valueOf (this.engineActive);
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


    private static PushControlSurface createSurface (final ISelectedTrackNoteTarget selectedTarget, final ITrack drumModelTrack, final IValueChanger valueChanger, final MutableNoteRepeat noteRepeat, final boolean manualRepeatActive)
    {
        final IHwButton button = relaxedProxy (IHwButton.class);
        final IHwLight light = relaxedProxy (IHwLight.class);
        final IHwSurfaceFactory surfaceFactory = proxy (IHwSurfaceFactory.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "createButton" -> button;
            case "createLight" -> light;
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
