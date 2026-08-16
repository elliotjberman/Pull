// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.CatalogClip;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerMappingFeedbackSnapshot;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.ControllerMappingBinding;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.CoreCapabilities;
import de.mossgrabers.pull.core.api.CoreControllerMappings;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.CoreExecutionRequirements;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DesiredInputRoutes;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredControllerState;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;
import de.mossgrabers.pull.core.api.DesiredNoteInputRoute;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.DesiredParameterBanks;
import de.mossgrabers.pull.core.api.DesiredParameterInteraction;
import de.mossgrabers.pull.core.api.InputRoute;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.NoteRepeatMode;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterTargetKind;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.TimerId;
import de.mossgrabers.pull.core.api.effect.ClipLaunchMode;
import de.mossgrabers.pull.core.api.effect.ClipLaunchPolicy;
import de.mossgrabers.pull.core.api.effect.ClipLaunchQuantization;
import de.mossgrabers.pull.core.api.effect.ClipReleaseTrigger;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
import de.mossgrabers.pull.core.api.effect.ScheduleTimerEffect;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.PadGridPosition;
import de.mossgrabers.pull.core.api.output.RgbColor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transaction, single-active fill ownership, and asynchronous read-back tests for the stable shell.
 */
class ControllerRuntimeEnvironmentTest
{
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor DIM_RED = new RgbColor (127, 0, 0);
    private static final RgbColor BRIGHT_RED = new RgbColor (255, 0, 0);
    private static final ControlId FIRST = CoreControls.DRUM_FILL_1;
    private static final ControlId SECOND = CoreControls.DRUM_FILL_2;
    private static final ControlId THIRD = CoreControls.DRUM_FILL_3;
    private static final ClipTargetId FIRST_TARGET = new ClipTargetId (1);
    private static final ClipTargetId SECOND_TARGET = new ClipTargetId (2);
    private static final ClipTargetId THIRD_TARGET = new ClipTargetId (3);
    private static final ClipLaunchPolicy LAUNCH_POLICY = new ClipLaunchPolicy (
        ClipLaunchQuantization.IMMEDIATE,
        ClipLaunchMode.LEGATO_FROM_CLIP_OR_PROJECT,
        ClipReleaseTrigger.ALTERNATE);


    @Test
    void capturesFreshSnapshotsAndCompleteIndependentOutputBuffers ()
    {
        final FakeClipHost host = host (7, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final AtomicLong clock = new AtomicLong (100);
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host, new RecordingLog (), clock::getAndIncrement);

        final ControllerSnapshot initial = environment.snapshot ();
        final ControllerSnapshot secondSnapshot = environment.snapshot ();
        assertNotSame (initial, secondSnapshot);
        assertEquals (initial.revision (), secondSnapshot.revision ());
        assertTrue (secondSnapshot.monotonicTimeNanos () > initial.monotonicTimeNanos ());
        assertEquals (7, initial.clipCatalog ().generation ());
        assertEquals (FIRST_TARGET, initial.armedClipTargets ().get (FIRST));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.INPUT_DRUM_FILL));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.SNAPSHOT_SELECTED_TRACK_CLIPS));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.BINDING_CLIP_TARGET));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.SNAPSHOT_CLIP_LAUNCH_SESSION));
        assertEquals (Integer.valueOf (4), initial.capabilities ().versions ().get (CoreCapabilities.EFFECT_CLIP_LAUNCH_HOLD));
        assertEquals (Integer.valueOf (4), initial.capabilities ().versions ().get (CoreCapabilities.OUTPUT_RGB_LIGHT));
        assertEquals (Integer.valueOf (2), initial.capabilities ().versions ().get (CoreCapabilities.OUTPUT_CONTROLLER_MAPPING));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.OUTPUT_CONTROLLER_STATE));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.EFFECT_NOTE_VIEW_PREFERENCE));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.OUTPUT_NOTE_REPEAT));
        assertEquals (Integer.valueOf (3), initial.capabilities ().versions ().get (CoreCapabilities.ROUTING_CONTROLLER_INPUT));
        assertEquals (Integer.valueOf (7), initial.capabilities ().versions ().get (CoreCapabilities.SNAPSHOT_CONTROLLER_BRIDGE));
        assertEquals (Integer.valueOf (2), initial.capabilities ().versions ().get (CoreCapabilities.SNAPSHOT_PARAMETER_TARGETS));
        assertEquals (Integer.valueOf (2), initial.capabilities ().versions ().get (CoreCapabilities.EFFECT_PARAMETER_TARGET));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.SNAPSHOT_CONTROLLER_MAPPING_FEEDBACK));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.SNAPSHOT_MASTER));
        assertEquals (Integer.valueOf (2), initial.capabilities ().versions ().get (CoreCapabilities.EFFECT_MASTER));
        assertEquals (Integer.valueOf (2), initial.capabilities ().versions ().get (CoreCapabilities.OUTPUT_CONTROLLER_DISPLAY));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.OUTPUT_PAD_GRID_OVERLAY));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.OUTPUT_DISPLAY_OVERLAY));
        assertEquals (Integer.valueOf (1), initial.capabilities ().versions ().get (CoreCapabilities.RENDER_MIXER_CONTROLS));
        assertTrue (initial.clipLaunchSessionTargets ().isEmpty ());
        assertEquals (Optional.empty (), initial.activeClipLaunchOwner ());

        final ButtonInputEvent firstDown = environment.setFillPressed (FIRST, true);
        final ButtonInputEvent secondDown = environment.setFillPressed (SECOND, true);
        assertEquals (1, firstDown.sequence ());
        assertEquals (2, secondDown.sequence ());
        assertEquals (2, environment.snapshot ().revision ());
        assertTrue (environment.snapshot ().pressedControls ().containsAll (List.of (FIRST, SECOND)));

        final ButtonInputEvent duplicateDown = environment.setFillPressed (FIRST, true);
        assertEquals (3, duplicateDown.sequence ());
        assertEquals (2, environment.snapshot ().revision ());
        final SnapshotChangedEvent changed = environment.snapshotChangedEvent ();
        assertEquals (4, changed.sequence ());

        final PreparedCoreResult prepared = environment.prepare (result (
            Map.of (FIRST, DIM_RED, SECOND, BRIGHT_RED),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of ()));
        assertEquals (OFF, environment.fillLightColor (FIRST));
        environment.commit (11, prepared);
        assertEquals (DIM_RED, environment.fillLightColor (FIRST));
        assertEquals (BRIGHT_RED, environment.fillLightColor (SECOND));
        assertEquals (OFF, environment.fillLightColor (THIRD));
        assertEquals (11, environment.outputGeneration ());
        assertEquals (0, host.bindingUpdateCount);

        environment.apply (11);
        assertEquals (1, host.bindingUpdateCount);
        assertEquals (Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET), host.desiredBindings);
    }


    @Test
    void parameterBanksRequireAnInstalledBridge ()
    {
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host (7, FIRST_TARGET), new RecordingLog ());
        final DesiredParameterBanks banks = new DesiredParameterBanks (Set.of (ParameterBankId.PROJECT_REMOTE));
        final CoreResult latentBank = parameterResult (DesiredBridgeSubscriptions.empty (), banks);
        final CoreResult missingBridge = parameterResult (
            new DesiredBridgeSubscriptions (Set.of (de.mossgrabers.pull.core.api.BridgeSubscription.PARAMETERS)),
            banks);
        final CoreResult unobservedEffect = parameterResult (
            DesiredBridgeSubscriptions.empty (),
            banks,
            List.of (new AdjustParameterValueEffect (new ParameterTargetRef (ParameterTargetKind.LIVE, "stale", 1), 1)));

        assertThrows (IllegalArgumentException.class, () -> environment.prepare (latentBank));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (missingBridge));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (unobservedEffect));
    }


    @Test
    void rejectsAWorkspaceBeforeCommitWhenNoPermanentControllerBridgeExists ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        final DesiredControllerWorkspace workspace = new DesiredControllerWorkspace (
            "test",
            Set.of (ControllerViewFacet.PROJECT_MACRO_CONTROLS),
            SessionBankShape.empty ());
        final CoreResult result = new CoreResult (
            DesiredHardwareOutput.empty (),
            DesiredInputRoutes.empty (),
            DesiredBridgeSubscriptions.empty (),
            Map.of (),
            new DesiredControllerState (workspace, DesiredNotePerformance.inactive ()),
            DesiredNoteRepeat.unowned (),
            de.mossgrabers.pull.core.api.DesiredControllerActions.empty (),
            de.mossgrabers.pull.core.api.DesiredParameterBanks.empty (),
            de.mossgrabers.pull.core.api.DesiredParameterInteraction.empty (),
            List.of ());

        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result));
        assertEquals (0, environment.outputGeneration ());
    }


    @Test
    void acceptsCoreTickCadenceWithoutAControllerBridge ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        final CoreResult result = new CoreResult (
            DesiredHardwareOutput.empty (),
            DesiredInputRoutes.empty (),
            DesiredBridgeSubscriptions.empty (),
            Map.of (),
            DesiredControllerState.empty (),
            DesiredNoteRepeat.unowned (),
            de.mossgrabers.pull.core.api.DesiredControllerActions.empty (),
            DesiredParameterBanks.empty (),
            DesiredParameterInteraction.empty (),
            new CoreExecutionRequirements (true),
            List.of ());

        assertTrue (environment.prepare (result) != null);
        assertEquals (0, environment.outputGeneration ());
    }


    @Test
    void commitsMasterRowLightsAndDisplayOnlyWithTheMasterFacet ()
    {
        final PassthroughControllerBridge bridge = new PassthroughControllerBridge ();
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host (1), bridge, new RecordingLog (), () -> 0);
        final ControlId previous = PushControlIds.button ("ROW2_7");
        final ControllerDisplayScene display = new ControllerDisplayScene (960, 160, List.of (new DisplayCommand.Rectangle (0, 0, 960, 160, OFF)));
        final DesiredControllerWorkspace workspace = new DesiredControllerWorkspace ("Master", Set.of (ControllerViewFacet.MASTER_CONTROLS), SessionBankShape.empty ());
        final CoreResult masterResult = new CoreResult (
            new DesiredHardwareOutput (Map.of (previous, BRIGHT_RED), display),
            DesiredInputRoutes.empty (),
            DesiredBridgeSubscriptions.empty (),
            Map.of (),
            new DesiredControllerState (workspace, DesiredNotePerformance.inactive ()),
            DesiredNoteRepeat.unowned (),
            de.mossgrabers.pull.core.api.DesiredControllerActions.empty (),
            DesiredParameterBanks.empty (),
            DesiredParameterInteraction.empty (),
            List.of ());

        commitAndApply (environment, 9, masterResult);

        assertEquals (BRIGHT_RED, environment.lightColor (previous));
        assertEquals (display, environment.controllerDisplay ());
        assertEquals (workspace, bridge.appliedWorkspace);
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (new CoreResult (
            new DesiredHardwareOutput (Map.of (previous, BRIGHT_RED), display),
            DesiredInputRoutes.empty (), DesiredBridgeSubscriptions.empty (), Map.of (),
            de.mossgrabers.pull.core.api.DesiredControllerActions.empty (),
            DesiredParameterBanks.empty (), DesiredParameterInteraction.empty (), List.of ())));

        environment.invalidate (10);
        assertFalse (environment.controllerDisplay ().isPresent ());
        assertEquals (OFF, environment.lightColor (previous));
    }


    @Test
    void debugLightRevisionAdvancesOnlyAfterTheCompleteResultApplies ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        final ControlId pad = CoreControls.DRUM_RATES.getFirst ();
        final PreparedCoreResult prepared = environment.prepare (result (Map.of (pad, BRIGHT_RED), Map.of (), List.of ()));

        environment.commit (9, prepared);
        final ControllerRuntimeEnvironment.DebugLightObservation pending = environment.debugLightObservation (pad);
        assertEquals (9, pending.coreGeneration ());
        assertEquals (0, pending.appliedRevision ());
        assertEquals (BRIGHT_RED, pending.color ());
        assertFalse (pending.mappingDesired ());

        environment.apply (8);
        assertEquals (0, environment.debugLightObservation (pad).appliedRevision ());
        environment.apply (9);

        final ControllerRuntimeEnvironment.DebugLightObservation applied = environment.debugLightObservation (pad);
        assertEquals (1, applied.appliedRevision ());
        assertTrue (applied.present ());
        assertEquals (BRIGHT_RED, applied.color ());
        assertFalse (applied.mappingDesired ());

        commitAndApply (environment, 9, result (Map.of (), Map.of (), List.of ()));
        final ControllerRuntimeEnvironment.DebugLightObservation absent = environment.debugLightObservation (pad);
        assertEquals (2, absent.appliedRevision ());
        assertFalse (absent.present ());
    }


    @Test
    void admitsSemanticMappingsOnlyWithTheirExclusivePhysicalRouteAndOwnedFeedback ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        final AtomicReference<DesiredControllerMappings> mappingsAtDeferredRelease = new AtomicReference<> ();
        environment.setDeferredInputRelease (() -> mappingsAtDeferredRelease.set (environment.activeControllerMappings ()));
        environment.setInputRouteValidator (ignored -> true);
        final ControlId pad = CoreControls.DRUM_CONTROL_PADS.getFirst ();
        final ControllerMappingBinding binding = new ControllerMappingBinding (pad, CoreControllerMappings.DRUM_CONTROL_PADS.getFirst ());
        final DesiredControllerMappings mappings = new DesiredControllerMappings (Set.of (binding));
        final DesiredInputRoutes routes = new DesiredInputRoutes (Set.of (new InputRoute (pad, InputKind.PAD, InputRouteMode.EXCLUSIVE)));
        final DesiredHardwareOutput output = new DesiredHardwareOutput (
            Map.of (pad, BRIGHT_RED),
            ControllerDisplayScene.empty (),
            ControllerPadGridOverlay.inactive (),
            ControllerDisplayOverlay.inactive (),
            mappings);
        final CoreResult result = routedResult (output, routes);

        commitAndApply (environment, 9, result);
        assertEquals (mappings, environment.activeControllerMappings ());
        assertTrue (environment.debugLightObservation (pad).mappingDesired ());

        environment.quarantine (8);
        assertEquals (mappings, environment.activeControllerMappings ());
        environment.quarantine (9);
        assertTrue (environment.activeControllerMappings ().bindings ().isEmpty ());
        assertEquals (DesiredControllerMappings.empty (), mappingsAtDeferredRelease.get ());
        assertEquals (OFF, environment.lightColor (pad));

        commitAndApply (environment, 10, result);
        assertEquals (mappings, environment.activeControllerMappings ());
        environment.invalidate (10);
        assertTrue (environment.activeControllerMappings ().bindings ().isEmpty ());
        assertEquals (DesiredControllerMappings.empty (), mappingsAtDeferredRelease.get ());
        assertTrue (environment.desiredInputRoutes ().routes ().isEmpty ());
        assertEquals (OFF, environment.lightColor (pad));

        assertThrows (IllegalArgumentException.class, () -> environment.prepare (routedResult (output, DesiredInputRoutes.empty ())));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (routedResult (
            new DesiredHardwareOutput (Map.of (), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), mappings),
            routes)));
        final DesiredControllerMappings unsupportedSemanticMapping = new DesiredControllerMappings (Set.of (
            new ControllerMappingBinding (pad, new ControllerMappingId ("not-installed"))));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (routedResult (
            new DesiredHardwareOutput (Map.of (pad, BRIGHT_RED), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), unsupportedSemanticMapping),
            routes)));
        final ControlId unsupportedPhysical = new ControlId ("not-installed");
        final DesiredControllerMappings unsupportedPhysicalMapping = new DesiredControllerMappings (Set.of (
            new ControllerMappingBinding (unsupportedPhysical, CoreControllerMappings.DRUM_CONTROL_PADS.getFirst ())));
        final DesiredInputRoutes unsupportedPhysicalRoute = new DesiredInputRoutes (Set.of (
            new InputRoute (unsupportedPhysical, InputKind.PAD, InputRouteMode.EXCLUSIVE)));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (routedResult (
            new DesiredHardwareOutput (Map.of (unsupportedPhysical, BRIGHT_RED), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), unsupportedPhysicalMapping),
            unsupportedPhysicalRoute)));
    }


    @Test
    void debugLightObservationUsesOnlyAvailableAuthoritativeMappedPadReadback ()
    {
        final PassthroughControllerBridge bridge = new PassthroughControllerBridge ();
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host (1), bridge, new RecordingLog (), () -> 0);
        environment.setInputRouteValidator (ignored -> true);
        final ControlId first = CoreControls.DRUM_CONTROL_PADS.get (0);
        final ControlId second = CoreControls.DRUM_CONTROL_PADS.get (1);
        final ControllerMappingId firstMapping = CoreControllerMappings.DRUM_CONTROL_PADS.get (0);
        final ControllerMappingId secondMapping = CoreControllerMappings.DRUM_CONTROL_PADS.get (1);
        final DesiredControllerMappings mappings = new DesiredControllerMappings (Set.of (
            new ControllerMappingBinding (first, firstMapping),
            new ControllerMappingBinding (second, secondMapping)));
        final DesiredInputRoutes routes = new DesiredInputRoutes (Set.of (
            new InputRoute (first, InputKind.PAD, InputRouteMode.EXCLUSIVE),
            new InputRoute (second, InputKind.PAD, InputRouteMode.EXCLUSIVE)));
        final DesiredHardwareOutput output = new DesiredHardwareOutput (
            Map.of (first, BRIGHT_RED, second, BRIGHT_RED),
            ControllerDisplayScene.empty (),
            ControllerPadGridOverlay.inactive (),
            ControllerDisplayOverlay.inactive (),
            mappings);
        commitAndApply (environment, 9, routedResult (output, routes));

        assertNull (environment.debugLightObservation (first).mappedOn ());
        bridge.setControllerMappingFeedback (new ControllerMappingFeedbackSnapshot (true, Map.of (firstMapping, Boolean.FALSE, secondMapping, Boolean.TRUE)));

        assertEquals (Boolean.FALSE, environment.debugLightObservation (first).mappedOn ());
        assertEquals (Boolean.TRUE, environment.debugLightObservation (second).mappedOn ());
        assertNull (environment.debugLightObservation (CoreControls.DRUM_RATES.getFirst ()).mappedOn ());
    }


    @Test
    void preparesAndAppliesTheCompleteNoteControllerMechanismsTransactionally ()
    {
        final PassthroughControllerBridge bridge = new PassthroughControllerBridge ();
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host (1), bridge, new RecordingLog (), () -> 0);
        final DesiredControllerLayout layout = DesiredControllerLayout.note (ControllerNoteView.DRUM_PAD);
        final DesiredNoteInputRoute route = DesiredNoteInputRoute.selectedTrack (4, "drums");
        final DesiredNotePerformance performance = new DesiredNotePerformance (layout, route);
        final DesiredNoteRepeat repeat = new DesiredNoteRepeat (true, true, NoteRepeatMode.UP, 0, 0.25, 0.5, false, false, true, true);
        final CoreResult result = new CoreResult (
            new DesiredHardwareOutput (Map.of (CoreControls.DRUM_RATES.get (0), BRIGHT_RED)),
            DesiredInputRoutes.empty (),
            DesiredBridgeSubscriptions.empty (),
            Map.of (),
            new DesiredControllerState (DesiredControllerWorkspace.empty (), performance),
            repeat,
            de.mossgrabers.pull.core.api.DesiredControllerActions.empty (),
            DesiredParameterBanks.empty (),
            DesiredParameterInteraction.empty (),
            List.of ());

        final PreparedCoreResult prepared = environment.prepare (result);

        assertEquals (performance, bridge.preparedNotePerformance);
        assertEquals (repeat, bridge.preparedNoteRepeat);
        assertEquals (DesiredNotePerformance.inactive (), bridge.appliedNotePerformance);
        assertFalse (bridge.appliedNoteRepeat.owned ());

        environment.commit (9, prepared);
        environment.apply (9);

        assertEquals (performance, bridge.appliedNotePerformance);
        assertEquals (repeat, bridge.appliedNoteRepeat);
        assertEquals (List.of ("controller-state", "note-repeat"), bridge.applicationOrder);
        assertEquals (BRIGHT_RED, environment.lightColor (CoreControls.DRUM_RATES.get (0)));
    }


    @Test
    void commitsAndInvalidatesTheCompleteSparsePadGridOverlay ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        final ControllerPadGridOverlay overlay = new ControllerPadGridOverlay (
            true,
            Map.of (new PadGridPosition (0, 0), new RgbColor (160, 48, 255)));
        final CoreResult result = new CoreResult (
            new DesiredHardwareOutput (Map.of (), ControllerDisplayScene.empty (), overlay),
            DesiredInputRoutes.empty (),
            DesiredBridgeSubscriptions.empty (),
            Map.of (),
            de.mossgrabers.pull.core.api.DesiredControllerActions.empty (),
            DesiredParameterBanks.empty (),
            DesiredParameterInteraction.empty (),
            List.of ());

        environment.commit (9, environment.prepare (result));
        assertEquals (overlay, environment.padGridOverlay ());

        environment.invalidate (10);
        assertFalse (environment.padGridOverlay ().active ());
    }


    @Test
    void commitsAndInvalidatesACompleteDisplayOverlayOutsideMaster ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        final ControllerDisplayOverlay overlay = new ControllerDisplayOverlay (
            true,
            new ControllerDisplayScene (960, 160, List.of (
                new DisplayCommand.Rectangle (0, 0, 960, 160, OFF),
                new DisplayCommand.Rectangle (200, 0, 20, 160, BRIGHT_RED))));
        final CoreResult result = new CoreResult (
            new DesiredHardwareOutput (Map.of (), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), overlay),
            DesiredInputRoutes.empty (),
            DesiredBridgeSubscriptions.empty (),
            Map.of (),
            de.mossgrabers.pull.core.api.DesiredControllerActions.empty (),
            DesiredParameterBanks.empty (),
            DesiredParameterInteraction.empty (),
            List.of ());

        environment.commit (9, environment.prepare (result));
        assertEquals (overlay, environment.displayOverlay ());

        environment.invalidate (10);
        assertFalse (environment.displayOverlay ().active ());
    }


    @Test
    void quarantineRetainsPassiveOutputButClearsTransientOverlaysAndReturnsTheActiveFill ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        final PassthroughControllerBridge bridge = new PassthroughControllerBridge ();
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host, bridge, new RecordingLog (), () -> 0);
        final AtomicBoolean failDeferredRelease = new AtomicBoolean ();
        environment.setDeferredInputRelease ( () -> {
            if (failDeferredRelease.get ())
                throw new IllegalStateException ("broken deferred release");
        });
        final ControlId previous = PushControlIds.button ("ROW2_7");
        final ControllerDisplayScene display = new ControllerDisplayScene (960, 160, List.of (new DisplayCommand.Rectangle (0, 0, 960, 160, OFF)));
        final ControllerPadGridOverlay padOverlay = new ControllerPadGridOverlay (true, Map.of (new PadGridPosition (0, 0), BRIGHT_RED));
        final ControllerDisplayOverlay displayOverlay = new ControllerDisplayOverlay (true, new ControllerDisplayScene (960, 160, List.of (new DisplayCommand.Rectangle (0, 0, 960, 160, BRIGHT_RED))));
        final DesiredControllerWorkspace workspace = new DesiredControllerWorkspace ("Master", Set.of (ControllerViewFacet.MASTER_CONTROLS), SessionBankShape.empty ());
        final ControlId ratePad = CoreControls.DRUM_RATES.get (0);
        final CoreResult result = new CoreResult (
            new DesiredHardwareOutput (Map.of (previous, BRIGHT_RED, ratePad, BRIGHT_RED), display, padOverlay, displayOverlay),
            DesiredInputRoutes.empty (), DesiredBridgeSubscriptions.empty (), Map.of (FIRST, FIRST_TARGET),
            new DesiredControllerState (workspace, DesiredNotePerformance.inactive ()), DesiredNoteRepeat.unowned (), de.mossgrabers.pull.core.api.DesiredControllerActions.empty (), DesiredParameterBanks.empty (),
            DesiredParameterInteraction.empty (), new CoreExecutionRequirements (true),
            List.of (new PressClipTargetEffect (FIRST, 1, FIRST_TARGET, LAUNCH_POLICY)));

        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 9, result);
        acknowledgeLaunch (host, environment, FIRST);
        assertEquals (Optional.of (FIRST), environment.snapshot ().activeClipLaunchOwner ());
        bridge.failAbandon = true;
        failDeferredRelease.set (true);

        environment.quarantine (9);

        assertEquals (BRIGHT_RED, environment.lightColor (previous));
        assertEquals (OFF, environment.lightColor (ratePad));
        assertEquals (display, environment.controllerDisplay ());
        assertFalse (environment.padGridOverlay ().active ());
        assertFalse (environment.displayOverlay ().active ());
        assertFalse (environment.ticksRequested ());
        assertEquals (Map.of (), host.desiredBindings);
        assertEquals (1, host.target (FIRST).releaseCount);

        acknowledgeReturn (host, environment, FIRST);
        assertTrue (environment.snapshot ().clipLaunchSessionTargets ().isEmpty ());
        assertEquals ("root", host.playing ());
    }


    @Test
    void admitsOnlyTheBoundedGlobalTransportLightsOutsideMaster ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        final ControlId play = PushControlIds.button ("PLAY");
        final ControlId record = PushControlIds.button ("RECORD");
        final ControlId stop = PushControlIds.button ("STOP");

        environment.commit (7, environment.prepare (result (
            Map.of (play, BRIGHT_RED, record, DIM_RED),
            Map.of (),
            List.of ())));

        assertEquals (BRIGHT_RED, environment.lightColor (play));
        assertEquals (DIM_RED, environment.lightColor (record));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (
            Map.of (stop, BRIGHT_RED),
            Map.of (),
            List.of ())));

        environment.invalidate (8);
        assertEquals (OFF, environment.lightColor (play));
        assertEquals (OFF, environment.lightColor (record));
    }


    @Test
    void requiresTheExactAlreadyArmedBindingBeforeAButtonCanLaunch ()
    {
        final FakeClipHost host = host (4, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        final ControllerRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);

        assertThrows (IllegalArgumentException.class, () -> environment.prepare (pressResult (3, FIRST, FIRST_TARGET)));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (pressResult (4, FIRST, SECOND_TARGET)));
        assertThrows (IllegalStateException.class, () -> environment (host (4, FIRST_TARGET)).prepare (pressResult (4, FIRST, FIRST_TARGET)));
        assertEquals (0, host.prepareCount);
        assertEquals (0, host.target (FIRST).pressCount);
    }


    @Test
    void replacementReturnsToBaseBeforeItResolvesAndLaunchesTheLatestFill ()
    {
        final FakeClipHost host = host (5, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final ControllerRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (5, FIRST, FIRST_TARGET));
        assertEquals (1, host.target (FIRST).prepareCount);
        assertEquals (1, host.target (FIRST).pressCount);
        assertEquals (Map.of (FIRST, FIRST_TARGET), environment.snapshot ().clipLaunchSessionTargets ());
        assertEquals (Optional.empty (), environment.snapshot ().activeClipLaunchOwner ());

        acknowledgeLaunch (host, environment, FIRST);
        assertEquals (Optional.of (FIRST), environment.snapshot ().activeClipLaunchOwner ());

        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (new PressClipTargetEffect (SECOND, 5, SECOND_TARGET, LAUNCH_POLICY))));

        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (0, host.target (SECOND).prepareCount);
        assertEquals (0, host.target (SECOND).pressCount);
        assertEquals (List.of ("press 1", "release 1"), host.launchEvents);
        assertEquals ("1", host.playing ());

        acknowledgeReturn (host, environment, FIRST);
        assertEquals (1, host.target (FIRST).retireCount);
        assertEquals (0, host.target (SECOND).prepareCount);
        assertEquals ("root", host.playing ());
        assertTrue (environment.snapshot ().clipLaunchSessionTargets ().isEmpty ());
        assertEquals (Optional.empty (), environment.snapshot ().activeClipLaunchOwner ());

        environment.refresh ();
        assertEquals (1, host.target (SECOND).prepareCount);
        assertEquals (1, host.target (SECOND).pressCount);
        assertEquals (List.of ("press 1", "release 1", "press 2"), host.launchEvents);
        assertEquals (Map.of (SECOND, SECOND_TARGET), environment.snapshot ().clipLaunchSessionTargets ());
        assertEquals (Optional.empty (), environment.snapshot ().activeClipLaunchOwner ());

        acknowledgeLaunch (host, environment, SECOND);
        assertEquals (Optional.of (SECOND), environment.snapshot ().activeClipLaunchOwner ());

        // FIRST remains physically held, but releasing SECOND returns to the opaque root rather
        // than ever making FIRST the replacement's native Return destination.
        environment.setFillPressed (SECOND, false);
        commitAndApply (environment, 1, releaseResult (SECOND, Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET)));
        acknowledgeReturn (host, environment, SECOND);
        environment.refresh ();
        assertEquals ("root", host.playing ());
        assertEquals (1, host.target (FIRST).pressCount);
        assertTrue (environment.snapshot ().clipLaunchSessionTargets ().isEmpty ());
        assertEquals (Optional.empty (), environment.snapshot ().activeClipLaunchOwner ());

        environment.setFillPressed (FIRST, false);
        commitAndApply (environment, 1, releaseResult (FIRST, Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET)));
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (1, host.target (SECOND).releaseCount);
    }


    @Test
    void newestPendingPressWinsWithoutPreparingSupersededTargets ()
    {
        final FakeClipHost host = host (5, FIRST_TARGET, SECOND_TARGET, THIRD_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        host.arm (THIRD, THIRD_TARGET);
        final ControllerRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (5, FIRST, FIRST_TARGET));
        acknowledgeLaunch (host, environment, FIRST);

        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET, THIRD, THIRD_TARGET),
            List.of (new PressClipTargetEffect (SECOND, 5, SECOND_TARGET, LAUNCH_POLICY))));
        environment.setFillPressed (THIRD, true);
        commitAndApply (environment, 1, result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET, THIRD, THIRD_TARGET),
            List.of (new PressClipTargetEffect (THIRD, 5, THIRD_TARGET, LAUNCH_POLICY))));

        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (0, host.target (SECOND).prepareCount);
        assertEquals (0, host.target (THIRD).prepareCount);

        acknowledgeReturn (host, environment, FIRST);
        assertEquals (0, host.target (SECOND).prepareCount);
        assertEquals (0, host.target (THIRD).prepareCount);
        environment.refresh ();

        assertEquals (0, host.target (SECOND).prepareCount);
        assertEquals (0, host.target (SECOND).pressCount);
        assertEquals (1, host.target (THIRD).prepareCount);
        assertEquals (1, host.target (THIRD).pressCount);
        assertEquals (List.of ("press 1", "release 1", "press 3"), host.launchEvents);
    }


    @Test
    void releasingAPendingReplacementCancelsItWithoutCancelingTheSubmittedReturn ()
    {
        final FakeClipHost host = host (5, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final ControllerRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (5, FIRST, FIRST_TARGET));
        acknowledgeLaunch (host, environment, FIRST);

        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (new PressClipTargetEffect (SECOND, 5, SECOND_TARGET, LAUNCH_POLICY))));
        environment.setFillPressed (SECOND, false);
        commitAndApply (environment, 1, releaseResult (SECOND, Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET)));

        assertEquals (1, host.target (FIRST).releaseCount);
        acknowledgeReturn (host, environment, FIRST);
        environment.refresh ();

        assertEquals ("root", host.playing ());
        assertEquals (0, host.target (SECOND).prepareCount);
        assertEquals (0, host.target (SECOND).pressCount);
        assertTrue (environment.snapshot ().clipLaunchSessionTargets ().isEmpty ());
    }


    @Test
    void earlyReplacementWaitsForObservedLaunchBeforeSubmittingReturn ()
    {
        final FakeClipHost host = host (5, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final ControllerRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (5, FIRST, FIRST_TARGET));

        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (new PressClipTargetEffect (SECOND, 5, SECOND_TARGET, LAUNCH_POLICY))));
        assertEquals (0, host.target (FIRST).releaseCount);

        // Bitwig has not published FIRST's launch yet. Its initial false read-back must neither
        // submit nor acknowledge Return in the same host turn as the launch request.
        environment.refresh ();
        assertEquals (0, host.target (FIRST).releaseCount);
        assertEquals (0, host.target (FIRST).retireCount);
        assertEquals (Map.of (FIRST, FIRST_TARGET), environment.snapshot ().clipLaunchSessionTargets ());
        assertEquals (0, host.target (SECOND).prepareCount);

        acknowledgeLaunch (host, environment, FIRST);
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (Optional.of (FIRST), environment.snapshot ().activeClipLaunchOwner ());
        assertEquals (0, host.target (FIRST).retireCount);

        acknowledgeReturn (host, environment, FIRST);
        assertEquals (1, host.target (FIRST).retireCount);
        assertEquals (0, host.target (SECOND).prepareCount);
        environment.refresh ();
        assertEquals (1, host.target (SECOND).pressCount);
    }


    @Test
    void failedReturnRetriesTheExactActiveTargetBeforeLaunchingTheReplacement ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final RecordingLog log = new RecordingLog ();
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host, log, () -> 0);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (1, FIRST, FIRST_TARGET));
        acknowledgeLaunch (host, environment, FIRST);

        host.target (FIRST).failRelease = true;
        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (new PressClipTargetEffect (SECOND, 1, SECOND_TARGET, LAUNCH_POLICY))));
        assertEquals (1, host.target (FIRST).releaseAttempts);
        assertEquals (0, host.target (FIRST).releaseCount);
        assertEquals (0, host.target (SECOND).prepareCount);

        environment.refresh ();
        assertEquals (2, host.target (FIRST).releaseAttempts);
        assertEquals (0, host.target (SECOND).prepareCount);

        host.target (FIRST).failRelease = false;
        environment.refresh ();
        assertEquals (3, host.target (FIRST).releaseAttempts);
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (0, host.target (SECOND).prepareCount);

        acknowledgeReturn (host, environment, FIRST);
        assertEquals (0, host.target (SECOND).prepareCount);
        environment.refresh ();
        assertEquals (1, host.target (SECOND).prepareCount);
        assertEquals (1, host.target (SECOND).pressCount);
        assertTrue (log.warnings.stream ().anyMatch (message -> message.contains ("Fill replacement failed")));
    }


    @Test
    void catalogGenerationFenceDiscardsADeferredReplacementWithoutRetargetingIt ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final RecordingLog log = new RecordingLog ();
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host, log, () -> 0);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (1, FIRST, FIRST_TARGET));
        acknowledgeLaunch (host, environment, FIRST);

        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 1, result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (new PressClipTargetEffect (SECOND, 1, SECOND_TARGET, LAUNCH_POLICY))));
        host.queueState (catalog (2, FIRST_TARGET, SECOND_TARGET), Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET));
        environment.refresh ();

        acknowledgeReturn (host, environment, FIRST);
        environment.refresh ();

        assertEquals (2, environment.snapshot ().clipCatalog ().generation ());
        assertEquals (0, host.target (SECOND).prepareCount);
        assertEquals (0, host.target (SECOND).pressCount);
        assertEquals ("root", host.playing ());
        assertTrue (environment.snapshot ().clipLaunchSessionTargets ().isEmpty ());
        assertTrue (log.warnings.stream ().anyMatch (message -> message.contains ("catalog binding changed")));
    }


    @Test
    void refreshPublishesPlaybackThatChangedBetweenHostSamples ()
    {
        final FakeClipHost host = host (5, FIRST_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        final ControllerRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (5, FIRST, FIRST_TARGET));
        assertEquals (Optional.empty (), environment.snapshot ().activeClipLaunchOwner ());
        environment.acknowledgeSnapshotChange (environment.snapshotRevision ());
        assertFalse (environment.refresh ());
        final long stoppedRevision = environment.snapshotRevision ();

        host.advanceLaunch (FIRST);

        assertTrue (environment.refresh ());
        assertEquals (stoppedRevision + 1, environment.snapshotRevision ());
        assertEquals (Optional.of (FIRST), environment.snapshot ().activeClipLaunchOwner ());
    }


    @Test
    void hardwareOutputChangesOnlyAfterTheCoreRendersAuthoritativeReadback ()
    {
        final FakeClipHost host = host (5, FIRST_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        final ControllerRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, result (
            Map.of (FIRST, DIM_RED),
            Map.of (FIRST, FIRST_TARGET),
            List.of (new PressClipTargetEffect (FIRST, 5, FIRST_TARGET, LAUNCH_POLICY))));

        assertEquals (DIM_RED, environment.fillLightColor (FIRST));
        assertEquals (Optional.empty (), environment.snapshot ().activeClipLaunchOwner ());
        acknowledgeLaunch (host, environment, FIRST);
        assertEquals (Optional.of (FIRST), environment.snapshot ().activeClipLaunchOwner ());
        assertEquals (DIM_RED, environment.fillLightColor (FIRST));

        commitAndApply (environment, 2, result (Map.of (FIRST, BRIGHT_RED), Map.of (FIRST, FIRST_TARGET), List.of ()));
        assertEquals (BRIGHT_RED, environment.fillLightColor (FIRST));

        environment.setFillPressed (FIRST, false);
        commitAndApply (environment, 2, result (
            Map.of (FIRST, BRIGHT_RED),
            Map.of (FIRST, FIRST_TARGET),
            List.of (new ReleaseClipTargetsEffect (FIRST))));
        acknowledgeReturn (host, environment, FIRST);
        assertEquals (Optional.empty (), environment.snapshot ().activeClipLaunchOwner ());
        assertEquals (BRIGHT_RED, environment.fillLightColor (FIRST));

        commitAndApply (environment, 3, result (Map.of (FIRST, DIM_RED), Map.of (FIRST, FIRST_TARGET), List.of ()));
        assertEquals (DIM_RED, environment.fillLightColor (FIRST));
    }


    @Test
    void generationFenceDiscardsAPreparedPressAfterTheCatalogMoves ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        final ControllerRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        final PreparedCoreResult prepared = environment.prepare (pressResult (1, FIRST, FIRST_TARGET));
        environment.commit (8, prepared);

        host.queueState (catalog (2, SECOND_TARGET), Map.of ());
        environment.refresh ();
        environment.apply (7);
        environment.apply (8);

        assertEquals (0, host.target (FIRST).prepareCount);
        assertEquals (0, host.target (FIRST).pressCount);
        assertEquals (1, host.bindingUpdateCount);
    }


    @Test
    void rejectsUnknownOutputsBindingsEffectsAndDuplicateOwnerEffects ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        final ControllerRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        final ControlId unknown = new ControlId ("unknown.control");

        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (unknown, DIM_RED), Map.of (), List.of ())));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (), Map.of (unknown, FIRST_TARGET), List.of ())));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (), Map.of (FIRST, new ClipTargetId (99)), List.of ())));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (), Map.of (FIRST, FIRST_TARGET, SECOND, FIRST_TARGET), List.of ())));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (), Map.of (), List.of (new PressClipTargetEffect (FIRST, 1, FIRST_TARGET, LAUNCH_POLICY)))));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (), Map.of (FIRST, SECOND_TARGET), List.of (new PressClipTargetEffect (FIRST, 1, FIRST_TARGET, LAUNCH_POLICY)))));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (), Map.of (), List.of (new ScheduleTimerEffect (new TimerId ("timer"), 1)))));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (
            Map.of (),
            Map.of (FIRST, FIRST_TARGET),
            List.of (new PressClipTargetEffect (FIRST, 1, FIRST_TARGET, LAUNCH_POLICY), new ReleaseClipTargetsEffect (FIRST)))));
        assertEquals (0, host.prepareCount);
    }


    @Test
    void activeTargetCannotBeReboundToAnotherOwner ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final ControllerRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (1, FIRST, FIRST_TARGET));
        environment.setFillPressed (SECOND, true);

        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (
            Map.of (),
            Map.of (SECOND, FIRST_TARGET),
            List.of (new PressClipTargetEffect (SECOND, 1, FIRST_TARGET, LAUNCH_POLICY)))));
        assertEquals (0, host.target (SECOND).prepareCount);
        assertEquals (0, host.target (SECOND).pressCount);
        assertEquals (Map.of (FIRST, FIRST_TARGET), environment.snapshot ().clipLaunchSessionTargets ());
    }


    @Test
    void pressThatAppliesThenThrowsRemainsOwnedUntilObservedCleanup ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.target (FIRST).failPressAfterApply = true;
        final RecordingLog log = new RecordingLog ();
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host, log, () -> 0);
        environment.setFillPressed (FIRST, true);

        commitAndApply (environment, 1, pressResult (1, FIRST, FIRST_TARGET));
        assertEquals (1, host.target (FIRST).pressCount);
        assertEquals (0, host.target (FIRST).releaseCount);
        assertEquals (Map.of (FIRST, FIRST_TARGET), environment.snapshot ().clipLaunchSessionTargets ());

        // The pre-launch false sample cannot safely clean up an indeterminate command.
        environment.refresh ();
        assertEquals (0, host.target (FIRST).releaseCount);
        assertEquals (0, host.target (FIRST).retireCount);
        host.advanceLaunch (FIRST);
        environment.refresh ();
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (Optional.of (FIRST), environment.snapshot ().activeClipLaunchOwner ());
        acknowledgeReturn (host, environment, FIRST);

        assertEquals (1, host.target (FIRST).retireCount);
        assertTrue (environment.snapshot ().clipLaunchSessionTargets ().isEmpty ());
        assertEquals ("root", host.playing ());
        assertTrue (log.warnings.stream ().anyMatch (message -> message.contains ("press failed")));
    }


    @Test
    void invalidationCancelsTheDeferredReplacementAndBestEffortReturnsOnlyTheActiveFill ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET, SECOND_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        host.arm (SECOND, SECOND_TARGET);
        final ControllerRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 4, pressResult (1, FIRST, FIRST_TARGET));
        acknowledgeLaunch (host, environment, FIRST);
        environment.setFillPressed (SECOND, true);
        commitAndApply (environment, 4, result (
            Map.of (FIRST, BRIGHT_RED, SECOND, BRIGHT_RED),
            Map.of (FIRST, FIRST_TARGET, SECOND, SECOND_TARGET),
            List.of (new PressClipTargetEffect (SECOND, 1, SECOND_TARGET, LAUNCH_POLICY))));

        environment.invalidate (5);

        assertEquals (Map.of (), host.desiredBindings);
        assertEquals (OFF, environment.fillLightColor (FIRST));
        assertEquals (OFF, environment.fillLightColor (SECOND));
        assertTrue (environment.snapshot ().pressedControls ().isEmpty ());
        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (0, host.target (SECOND).prepareCount);

        acknowledgeReturn (host, environment, FIRST);
        environment.refresh ();
        assertEquals ("root", host.playing ());
        assertEquals (0, host.target (SECOND).prepareCount);
        assertEquals (0, host.target (SECOND).pressCount);
        assertTrue (environment.snapshot ().clipLaunchSessionTargets ().isEmpty ());
        assertEquals (5, environment.outputGeneration ());
    }


    @Test
    void invalidationForceSubmitsOneBestEffortReturnBeforeBusyReadback ()
    {
        final FakeClipHost host = host (1, FIRST_TARGET);
        host.arm (FIRST, FIRST_TARGET);
        final ControllerRuntimeEnvironment environment = environment (host);
        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 1, pressResult (1, FIRST, FIRST_TARGET));
        assertEquals (0, host.target (FIRST).releaseCount);

        environment.invalidate (2);
        environment.invalidate (3);

        assertEquals (1, host.target (FIRST).releaseCount);
        assertEquals (1, host.target (FIRST).releaseAttempts);
        assertEquals (0, host.target (FIRST).retireCount);
    }


    @Test
    void routeValidationRejectsControlsOutsideTheInstalledPhysicalCanopy ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        final ControlId play = PushControlIds.button ("PLAY");
        final ControlId unknown = new ControlId ("push.button.not-installed");
        environment.setInputRouteValidator (route -> route.controlId ().equals (play) && route.kind () == InputKind.BUTTON);

        assertThrows (IllegalArgumentException.class, () -> environment.prepare (routedResult (new DesiredInputRoutes (Set.of (
            new InputRoute (unknown, InputKind.BUTTON, InputRouteMode.OBSERVE))))));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (routedResult (new DesiredInputRoutes (Set.of (
            new InputRoute (play, InputKind.TOUCH, InputRouteMode.EXCLUSIVE))))));
        assertEquals (DesiredInputRoutes.empty (), environment.desiredInputRoutes ());
    }


    @Test
    void committedInputRoutesAreACompleteReplayableReplacement ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        final ControlId play = PushControlIds.button ("PLAY");
        final ControlId stop = PushControlIds.button ("STOP");
        environment.setInputRouteValidator (route -> route.kind () == InputKind.BUTTON && (route.controlId ().equals (play) || route.controlId ().equals (stop)));
        final DesiredInputRoutes first = new DesiredInputRoutes (Set.of (
            new InputRoute (play, InputKind.BUTTON, InputRouteMode.OBSERVE),
            new InputRoute (stop, InputKind.BUTTON, InputRouteMode.EXCLUSIVE)));

        final PreparedCoreResult preparedFirst = environment.prepare (routedResult (first));
        assertEquals (DesiredInputRoutes.empty (), environment.desiredInputRoutes ());
        environment.commit (11, preparedFirst);
        assertEquals (first, environment.desiredInputRoutes ());
        assertEquals (Optional.of (InputRouteMode.OBSERVE), environment.desiredInputRoutes ().mode (play, InputKind.BUTTON));
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), environment.desiredInputRoutes ().mode (stop, InputKind.BUTTON));

        final DesiredInputRoutes second = new DesiredInputRoutes (Set.of (
            new InputRoute (play, InputKind.BUTTON, InputRouteMode.EXCLUSIVE)));
        environment.commit (12, environment.prepare (routedResult (second)));

        assertEquals (second, environment.desiredInputRoutes ());
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), environment.desiredInputRoutes ().mode (play, InputKind.BUTTON));
        assertEquals (Optional.empty (), environment.desiredInputRoutes ().mode (stop, InputKind.BUTTON));
    }


    @Test
    void genericGesturePhasesUpdatePressedAndTouchedSnapshotsUsingOneGlobalSequence ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        final ControlId play = PushControlIds.button ("PLAY");
        final ControlId knob = PushControlIds.continuous ("KNOB1");

        final ButtonInputEvent fillBegin = environment.setFillPressed (FIRST, true);
        final ControllerInputEvent buttonBegin = environment.controllerInput (play, InputKind.BUTTON, InputPhase.BEGIN, 127);
        assertEquals (1, fillBegin.sequence ());
        assertEquals (2, buttonBegin.sequence ());
        assertEquals (2, environment.snapshot ().revision ());
        assertEquals (Set.of (FIRST, play), environment.snapshot ().pressedControls ());
        assertTrue (environment.snapshot ().touchedControls ().isEmpty ());

        final ControllerInputEvent buttonLong = environment.controllerInput (play, InputKind.BUTTON, InputPhase.LONG, 127);
        final ControllerInputEvent touchBegin = environment.controllerInput (knob, InputKind.TOUCH, InputPhase.BEGIN, 127);
        final ControllerInputEvent touchLong = environment.controllerInput (knob, InputKind.TOUCH, InputPhase.LONG, 127);
        assertEquals (3, buttonLong.sequence ());
        assertEquals (4, touchBegin.sequence ());
        assertEquals (5, touchLong.sequence ());
        assertEquals (3, environment.snapshot ().revision ());
        assertEquals (Set.of (FIRST, play), environment.snapshot ().pressedControls ());
        assertEquals (Set.of (knob), environment.snapshot ().touchedControls ());

        final ControllerInputEvent buttonEnd = environment.controllerInput (play, InputKind.BUTTON, InputPhase.END, 0);
        final ControllerInputEvent touchEnd = environment.controllerInput (knob, InputKind.TOUCH, InputPhase.END, 0);
        final ButtonInputEvent fillEnd = environment.setFillPressed (FIRST, false);
        assertEquals (6, buttonEnd.sequence ());
        assertEquals (7, touchEnd.sequence ());
        assertEquals (8, fillEnd.sequence ());
        assertEquals (6, environment.snapshot ().revision ());
        assertTrue (environment.snapshot ().pressedControls ().isEmpty ());
        assertTrue (environment.snapshot ().touchedControls ().isEmpty ());
    }


    private static ControllerRuntimeEnvironment environment (final FakeClipHost host)
    {
        return new ControllerRuntimeEnvironment (host, new RecordingLog (), () -> 0);
    }


    private static FakeClipHost host (final long generation, final ClipTargetId... targets)
    {
        return new FakeClipHost (catalog (generation, targets));
    }


    private static ClipCatalogSnapshot catalog (final long generation, final ClipTargetId... targets)
    {
        final List<CatalogClip> clips = new ArrayList<> (targets.length);
        for (final ClipTargetId target: targets)
            clips.add (new CatalogClip (target, "fill " + target.value ()));
        return new ClipCatalogSnapshot (generation, clips);
    }


    private static CoreResult pressResult (final long catalogGeneration, final ControlId owner, final ClipTargetId target)
    {
        return result (Map.of (), Map.of (owner, target), List.of (new PressClipTargetEffect (owner, catalogGeneration, target, LAUNCH_POLICY)));
    }


    private static CoreResult releaseResult (final ControlId owner, final Map<ControlId, ClipTargetId> bindings)
    {
        return result (Map.of (), bindings, List.of (new ReleaseClipTargetsEffect (owner)));
    }


    private static CoreResult result (final Map<ControlId, RgbColor> lights, final Map<ControlId, ClipTargetId> bindings, final List<CoreEffect> effects)
    {
        return new CoreResult (
            new DesiredHardwareOutput (lights),
            DesiredInputRoutes.empty (),
            DesiredBridgeSubscriptions.empty (),
            bindings,
            de.mossgrabers.pull.core.api.DesiredControllerActions.empty (),
            de.mossgrabers.pull.core.api.DesiredParameterBanks.empty (),
            de.mossgrabers.pull.core.api.DesiredParameterInteraction.empty (),
            effects);
    }


    private static CoreResult parameterResult (final DesiredBridgeSubscriptions subscriptions, final DesiredParameterBanks banks)
    {
        return parameterResult (subscriptions, banks, List.of ());
    }


    private static CoreResult parameterResult (final DesiredBridgeSubscriptions subscriptions, final DesiredParameterBanks banks, final List<CoreEffect> effects)
    {
        return new CoreResult (
            DesiredHardwareOutput.empty (),
            DesiredInputRoutes.empty (),
            subscriptions,
            Map.of (),
            de.mossgrabers.pull.core.api.DesiredControllerActions.empty (),
            banks,
            DesiredParameterInteraction.empty (),
            effects);
    }


    private static CoreResult routedResult (final DesiredInputRoutes routes)
    {
        return routedResult (DesiredHardwareOutput.empty (), routes);
    }


    private static CoreResult routedResult (final DesiredHardwareOutput output, final DesiredInputRoutes routes)
    {
        return new CoreResult (
            output,
            routes,
            DesiredBridgeSubscriptions.empty (),
            Map.of (),
            de.mossgrabers.pull.core.api.DesiredControllerActions.empty (),
            de.mossgrabers.pull.core.api.DesiredParameterBanks.empty (),
            de.mossgrabers.pull.core.api.DesiredParameterInteraction.empty (),
            List.of ());
    }


    private static void commitAndApply (final ControllerRuntimeEnvironment environment, final long generation, final CoreResult result)
    {
        environment.commit (generation, environment.prepare (result));
        environment.apply (generation);
    }


    private static void acknowledgeLaunch (final FakeClipHost host, final ControllerRuntimeEnvironment environment, final ControlId owner)
    {
        host.advanceLaunch (owner);
        environment.refresh ();
    }


    private static void acknowledgeReturn (final FakeClipHost host, final ControllerRuntimeEnvironment environment, final ControlId owner)
    {
        host.advanceReturn (owner);
        environment.refresh ();
    }


    private static final class PassthroughControllerBridge implements ControllerBridge
    {
        private DesiredControllerWorkspace appliedWorkspace = DesiredControllerWorkspace.empty ();
        private DesiredNotePerformance preparedNotePerformance = DesiredNotePerformance.inactive ();
        private DesiredNotePerformance appliedNotePerformance = DesiredNotePerformance.inactive ();
        private DesiredNoteRepeat preparedNoteRepeat = DesiredNoteRepeat.unowned ();
        private DesiredNoteRepeat appliedNoteRepeat = DesiredNoteRepeat.unowned ();
        private ControllerBridgeSnapshot snapshot = ControllerBridgeSnapshot.empty ();
        private final List<String> applicationOrder = new ArrayList<> ();
        private boolean failAbandon;


        @Override
        public boolean refresh (final long monotonicTimeNanos, final DesiredBridgeSubscriptions subscriptions, final DesiredParameterBanks parameterBanks)
        {
            return false;
        }


        @Override
        public void activateCoreGeneration (final long generation)
        {
            // No live host in this output transaction test.
        }


        @Override
        public void invalidate ()
        {
            this.appliedWorkspace = DesiredControllerWorkspace.empty ();
        }


        @Override
        public void abandonActiveCore ()
        {
            if (this.failAbandon)
                throw new IllegalStateException ("broken bridge cleanup");
        }


        @Override
        public TargetedParameter resolveParameterMutation (final de.mossgrabers.framework.controller.hardware.IHwContinuousControl control)
        {
            return null;
        }


        @Override
        public Map<ParameterTargetRef, ParameterLease> prepareParameterLeases (final DesiredParameterInteraction desired, final DesiredParameterBanks parameterBanks)
        {
            return Map.of ();
        }


        @Override
        public boolean applyParameterLeases (final Map<ParameterTargetRef, ParameterLease> prepared, final DesiredParameterBanks parameterBanks)
        {
            return false;
        }


        @Override
        public boolean retainsParameterTarget (final ParameterTargetRef target)
        {
            return false;
        }


        @Override
        public DesiredControllerState prepareControllerState (final DesiredControllerState state)
        {
            this.preparedNotePerformance = state.notePerformance ();
            return state;
        }


        @Override
        public void applyControllerState (final DesiredControllerState state)
        {
            this.applicationOrder.add ("controller-state");
            this.appliedWorkspace = state.workspace ();
            this.appliedNotePerformance = state.notePerformance ();
        }


        @Override
        public DesiredNoteRepeat prepareNoteRepeat (final DesiredNoteRepeat noteRepeat)
        {
            this.preparedNoteRepeat = noteRepeat;
            return noteRepeat;
        }


        @Override
        public void applyNoteRepeat (final DesiredNoteRepeat noteRepeat)
        {
            this.applicationOrder.add ("note-repeat");
            this.appliedNoteRepeat = noteRepeat;
        }


        @Override
        public de.mossgrabers.pull.core.api.ControllerBridgeSnapshot snapshot ()
        {
            return this.snapshot;
        }


        private void setControllerMappingFeedback (final ControllerMappingFeedbackSnapshot feedback)
        {
            this.snapshot = new ControllerBridgeSnapshot (
                this.snapshot.transport (),
                this.snapshot.selectedTrack (),
                this.snapshot.layout (),
                this.snapshot.noteView (),
                this.snapshot.noteRepeat (),
                this.snapshot.drum (),
                this.snapshot.parameters (),
                feedback,
                this.snapshot.master (),
                this.snapshot.project ());
        }


        @Override
        public PreparedAction prepare (final CoreEffect effect, final Map<ParameterTargetRef, ParameterLease> parameterLeases)
        {
            return null;
        }


        @Override
        public void apply (final PreparedAction action)
        {
            // No effects in this output transaction test.
        }
    }


    private static final class FakeClipHost implements DrumFillClipHost
    {
        private final Map<ControlId, FakeTarget> targets = new LinkedHashMap<> ();
        private final List<String> launchEvents = new ArrayList<> ();
        private ClipCatalogSnapshot catalog;
        private Map<ControlId, ClipTargetId> armed = Map.of ();
        private ClipCatalogSnapshot queuedCatalog;
        private Map<ControlId, ClipTargetId> queuedArmed;
        private Map<ControlId, ClipTargetId> desiredBindings = Map.of ();
        private String playing = "root";
        private long desiredGeneration;
        private int bindingUpdateCount;
        private int prepareCount;


        private FakeClipHost (final ClipCatalogSnapshot catalog)
        {
            this.catalog = catalog;
            for (int index = 0; index < catalog.clips ().size () && index < CoreControls.DRUM_FILLS.size (); index++)
            {
                final ControlId owner = CoreControls.DRUM_FILLS.get (index);
                this.targets.put (owner, new FakeTarget (this, catalog.clips ().get (index).targetId ()));
            }
        }


        @Override
        public boolean refresh ()
        {
            if (this.queuedCatalog == null)
                return false;
            this.catalog = this.queuedCatalog;
            this.armed = this.queuedArmed;
            this.queuedCatalog = null;
            this.queuedArmed = null;
            return true;
        }


        @Override
        public ClipCatalogSnapshot clipCatalog ()
        {
            return this.catalog;
        }


        @Override
        public void setDesiredBindings (final long catalogGeneration, final Map<ControlId, ClipTargetId> bindings)
        {
            this.desiredGeneration = catalogGeneration;
            this.desiredBindings = Map.copyOf (bindings);
            this.bindingUpdateCount++;
        }


        @Override
        public Map<ControlId, ClipTargetId> armedClipTargets ()
        {
            return this.armed;
        }


        @Override
        public LaunchTarget prepare (final ControlId owner, final long catalogGeneration, final ClipTargetId targetId)
        {
            assertEquals ("root", this.playing, "A replacement target must not be resolved before the previous fill returned to base");
            if (catalogGeneration != this.catalog.generation ())
                throw new IllegalArgumentException ("stale generation");
            if (!targetId.equals (this.armed.get (owner)))
                throw new IllegalArgumentException ("target is not armed");
            final FakeTarget target = this.targets.get (owner);
            if (target == null || !targetId.equals (target.targetId ()))
                throw new IllegalArgumentException ("unknown target");
            this.prepareCount++;
            target.prepareCount++;
            return target;
        }


        private void arm (final ControlId owner, final ClipTargetId targetId)
        {
            final Map<ControlId, ClipTargetId> updated = new LinkedHashMap<> (this.armed);
            updated.put (owner, targetId);
            this.armed = Map.copyOf (updated);
            final FakeTarget existing = this.targets.get (owner);
            if (existing == null || !targetId.equals (existing.targetId ()))
                this.targets.put (owner, new FakeTarget (this, targetId));
        }


        private void queueState (final ClipCatalogSnapshot newCatalog, final Map<ControlId, ClipTargetId> newArmed)
        {
            this.queuedCatalog = newCatalog;
            this.queuedArmed = Map.copyOf (newArmed);
        }


        private FakeTarget target (final ControlId owner)
        {
            return this.targets.get (owner);
        }


        private void advanceLaunch (final ControlId owner)
        {
            final FakeTarget target = this.target (owner);
            assertTrue (target.pressRequested, "A host launch can advance only after command submission");
            assertFalse (target.launchAcknowledged, "A host launch can be acknowledged only once");
            assertEquals ("root", this.playing, "Only one fill may play above the opaque base");
            this.playing = Long.toString (target.targetId.value ());
            target.launchAcknowledged = true;
        }


        private void advanceReturn (final ControlId owner)
        {
            final FakeTarget target = this.target (owner);
            assertTrue (target.releaseRequested, "A host Return can advance only after command submission");
            assertFalse (target.returnAcknowledged, "A host Return can be acknowledged only once");
            assertEquals (Long.toString (target.targetId.value ()), this.playing, "Only the playing fill can Return to base");
            this.playing = "root";
            target.returnAcknowledged = true;
        }


        private String playing ()
        {
            return this.playing;
        }
    }


    private static final class FakeTarget implements DrumFillClipHost.LaunchTarget
    {
        private final FakeClipHost host;
        private final ClipTargetId targetId;
        private int prepareCount;
        private int pressCount;
        private int releaseAttempts;
        private int releaseCount;
        private int retireCount;
        private boolean failPressAfterApply;
        private boolean failRelease;
        private boolean pressRequested;
        private boolean launchAcknowledged;
        private boolean releaseRequested;
        private boolean returnAcknowledged;
        private boolean retired;


        private FakeTarget (final FakeClipHost host, final ClipTargetId targetId)
        {
            this.host = host;
            this.targetId = targetId;
        }


        @Override
        public ClipTargetId targetId ()
        {
            return this.targetId;
        }


        @Override
        public void press (final ClipLaunchPolicy launchPolicy)
        {
            assertEquals (LAUNCH_POLICY, launchPolicy);
            assertEquals ("root", this.host.playing, "A fill must launch from the opaque base");
            this.pressCount++;
            this.pressRequested = true;
            this.launchAcknowledged = false;
            this.releaseRequested = false;
            this.returnAcknowledged = false;
            this.retired = false;
            this.host.launchEvents.add ("press " + this.targetId.value ());
            if (this.failPressAfterApply)
                throw new IllegalStateException ("press applied then failed");
        }


        @Override
        public void release ()
        {
            this.releaseAttempts++;
            if (this.failRelease)
                throw new IllegalStateException ("release failed");
            assertTrue (this.pressRequested, "Only a submitted launch can be released");
            assertFalse (this.releaseRequested, "A successful host release must not be requested twice");
            this.host.launchEvents.add ("release " + this.targetId.value ());
            this.releaseRequested = true;
            this.releaseCount++;
        }


        @Override
        public DrumFillClipHost.PlaybackState playbackState ()
        {
            final boolean playing = Long.toString (this.targetId.value ()).equals (this.host.playing);
            return new DrumFillClipHost.PlaybackState (playing, false, false);
        }


        @Override
        public void retire ()
        {
            assertFalse (this.playbackState ().playing (), "A playing target cannot be retired");
            this.retired = true;
            this.retireCount++;
        }
    }


    private static final class RecordingLog implements RuntimeLog
    {
        private final List<String> warnings = new ArrayList<> ();


        @Override
        public void info (final String message)
        {
            // Not needed by these tests.
        }


        @Override
        public void warn (final String message)
        {
            this.warnings.add (message);
        }
    }
}
