// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.display.AbstractGraphicDisplay;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.graphics.DefaultGraphicsDimensions;
import de.mossgrabers.framework.graphics.IBitmap;
import de.mossgrabers.framework.graphics.IEncoder;
import de.mossgrabers.framework.graphics.IGraphicsConfiguration;
import de.mossgrabers.framework.graphics.IGraphicsContext;
import de.mossgrabers.framework.graphics.IImage;
import de.mossgrabers.framework.graphics.IRenderer;
import de.mossgrabers.framework.graphics.canvas.component.DisplaySceneComponent;
import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.CatalogClip;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerMappingFeedbackSnapshot;
import de.mossgrabers.pull.core.api.ControllerMappingContext;
import de.mossgrabers.pull.core.api.ControllerMappingValue;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.ControllerMappingBinding;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.ControllerMappingTarget;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
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
import de.mossgrabers.pull.core.api.DesiredParameterTouches;
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
import de.mossgrabers.pull.core.api.effect.ResetParameterEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
import de.mossgrabers.pull.core.api.effect.ScheduleTimerEffect;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;
import de.mossgrabers.pull.core.api.output.DesiredTouchStrip;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.PadGridPosition;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.api.output.LightBlink;

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
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private static final DesiredBridgeSubscriptions CONTROLLER_MAPPING_SUBSCRIPTIONS = new DesiredBridgeSubscriptions (Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK));
    private static final ControllerMappingContext MAPPING_CONTEXT = new ControllerMappingContext (1, "track-a", 1, "document-a");
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
            new DesiredControllerState (workspace, DesiredNotePerformance.inactive (), corePage ()),
            DesiredNoteRepeat.unowned (),
            de.mossgrabers.pull.core.api.DesiredControllerActions.empty (),
            de.mossgrabers.pull.core.api.DesiredParameterBanks.empty (),
            de.mossgrabers.pull.core.api.DesiredParameterInteraction.empty (),
            List.of ());

        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result));
        assertEquals (0, environment.outputGeneration ());
    }


    @Test
    void committedContinuationFencesReplacementAfterPhysicalReleaseUntilTheCoreClearsIt ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        final AtomicBoolean physicalInputIdle = new AtomicBoolean (true);
        environment.setInputLifecycleIdle (physicalInputIdle::get);
        final CoreResult pendingContinuation = executionResult (new CoreExecutionRequirements (true, true));
        final var prepared = environment.prepare (pendingContinuation);
        assertTrue (environment.canReplaceActiveCore ());

        environment.commit (7, prepared);
        assertFalse (environment.canReplaceActiveCore ());
        environment.apply (7);
        environment.refresh ();
        assertFalse (environment.canReplaceActiveCore ());

        // Continuing cadence alone must not retain the replacement fence.
        final var completed = environment.prepare (executionResult (new CoreExecutionRequirements (true)));
        assertFalse (environment.canReplaceActiveCore ());
        environment.commit (7, completed);
        environment.apply (7);
        assertTrue (environment.ticksRequested ());
        assertTrue (environment.canReplaceActiveCore ());
        physicalInputIdle.set (false);
        assertFalse (environment.canReplaceActiveCore ());
        physicalInputIdle.set (true);

        commitAndApply (environment, 7, pendingContinuation);
        assertFalse (environment.canReplaceActiveCore ());
        environment.invalidate (8);
        environment.apply (7);
        assertTrue (environment.canReplaceActiveCore ());
        assertFalse (environment.ticksRequested ());
    }


    @Test
    void automationWriteRequiresItsAuthoritativeSubscription ()
    {
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host (1), new PassthroughControllerBridge (), new RecordingLog (), () -> 0);
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (), Map.of (), List.of (new de.mossgrabers.pull.core.api.effect.SetAutomationWriteEffect ("project-a", false)))));
    }


    @Test
    void trackSettingsAndApplicationEffectsRequireTheirOwnAuthoritativeSubscriptions ()
    {
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host (1), new PassthroughControllerBridge (), new RecordingLog (), () -> 0);
        final var target = new de.mossgrabers.pull.core.api.CurrentTrackTarget (1, "main", 0, "track-a");
        final List<CoreEffect> effects = List.of (
            new de.mossgrabers.pull.core.api.effect.CurrentTrackActionEffect (target, de.mossgrabers.pull.core.api.effect.CurrentTrackActionEffect.Action.SELECT),
            new de.mossgrabers.pull.core.api.effect.SetCurrentTrackBooleanEffect (target, de.mossgrabers.pull.core.api.effect.SetCurrentTrackBooleanEffect.Property.RECORD_ARMED, true),
            new de.mossgrabers.pull.core.api.effect.NavigateTrackParentEffect (1, "track-a"),
            new de.mossgrabers.pull.core.api.effect.CurrentTrackNavigationEffect (1, "main", de.mossgrabers.pull.core.api.effect.CurrentTrackNavigationEffect.Operation.TRACK_PAGE_NEXT),
            new de.mossgrabers.pull.core.api.effect.SetControllerBooleanSettingEffect (de.mossgrabers.pull.core.api.effect.SetControllerBooleanSettingEffect.Setting.VU_METERS, true),
            new de.mossgrabers.pull.core.api.effect.SetControllerIntegerSettingEffect (de.mossgrabers.pull.core.api.effect.SetControllerIntegerSettingEffect.Setting.MIX_SEND_OFFSET, 4),
            new de.mossgrabers.pull.core.api.effect.SetControllerModeSettingEffect (de.mossgrabers.pull.core.api.effect.SetControllerModeSettingEffect.Setting.GLOBAL_MIX_MODE, "VOLUME"),
            new de.mossgrabers.pull.core.api.effect.SetApplicationLayoutEffect (new de.mossgrabers.pull.core.api.ApplicationUiContext (1, "project", "ARRANGE"), de.mossgrabers.pull.core.api.effect.SetApplicationLayoutEffect.Layout.MIX),
            new de.mossgrabers.pull.core.api.effect.ToggleApplicationPanelEffect (new de.mossgrabers.pull.core.api.ApplicationUiContext (1, "project", "ARRANGE"), de.mossgrabers.pull.core.api.effect.ToggleApplicationPanelEffect.Panel.DEVICES),
            new de.mossgrabers.pull.core.api.effect.SetArrangerBooleanEffect (new de.mossgrabers.pull.core.api.ApplicationUiContext (1, "project", "ARRANGE"), de.mossgrabers.pull.core.api.effect.SetArrangerBooleanEffect.Property.TIMELINE_VISIBLE, true),
            new de.mossgrabers.pull.core.api.effect.SetMixerBooleanEffect (new de.mossgrabers.pull.core.api.ApplicationUiContext (1, "project", "MIX"), de.mossgrabers.pull.core.api.effect.SetMixerBooleanEffect.Property.CROSS_FADE_VISIBLE, true));
        for (final CoreEffect effect: effects)
        {
            final IllegalArgumentException failure = assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (), Map.of (), List.of (effect))));
            assertTrue (failure.getMessage ().contains ("snapshot subscription"));
        }
    }


    @Test
    void arrowClaimsRequireCorePageOwnership ()
    {
        final PassthroughControllerBridge bridge = new PassthroughControllerBridge ();
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host (1), bridge, new RecordingLog (), () -> 0);
        environment.setInputRouteValidator (ignored -> true);
        for (final String arrow: List.of ("ARROW_LEFT", "ARROW_RIGHT", "ARROW_UP", "ARROW_DOWN"))
        {
            final var routes = new DesiredInputRoutes (Set.of (new InputRoute (PushControlIds.button (arrow), InputKind.BUTTON, InputRouteMode.EXCLUSIVE)));
            final CoreResult claimed = new CoreResult (
                DesiredHardwareOutput.empty (), routes, DesiredBridgeSubscriptions.empty (), Map.of (),
                new DesiredControllerState (DesiredControllerWorkspace.empty (), DesiredNotePerformance.inactive (), corePage ()), DesiredNoteRepeat.unowned (),
                de.mossgrabers.pull.core.api.DesiredControllerActions.empty (), DesiredParameterBanks.empty (), DesiredParameterInteraction.empty (), List.of ());
            assertNotNull (environment.prepare (claimed));
            final CoreResult unclassified = new CoreResult (
                DesiredHardwareOutput.empty (), routes, DesiredBridgeSubscriptions.empty (), Map.of (),
                de.mossgrabers.pull.core.api.DesiredControllerActions.empty (), DesiredParameterBanks.empty (), DesiredParameterInteraction.empty (), List.of ());
            assertThrows (IllegalArgumentException.class, () -> environment.prepare (unclassified));
        }
    }


    @Test
    void realCoreSessionDevicePagePassesTheInstalledNavigationBoundary () throws Exception
    {
        final var classes = java.nio.file.Path.of ("../pull-core/target/classes").toAbsolutePath ().normalize ();
        try (final var loader = new java.net.URLClassLoader (new java.net.URL[] {classes.toUri ().toURL ()}, getClass ().getClassLoader ()))
        {
            final var provider = (de.mossgrabers.pull.core.api.CoreProvider) loader.loadClass ("de.mossgrabers.pull.core.runtime.PullCoreProvider").getConstructor ().newInstance ();
            final var core = provider.create ();
            final var capabilities = provider.descriptor ().requiredCapabilities ();
            final var empty = de.mossgrabers.pull.core.api.LegacyControllerPageRequests.empty ();
            core.start (pageCaptureSnapshot (capabilities, 1, Set.of (), empty), Optional.empty ());
            final var session = PushControlIds.button ("SESSION");
            final var selected = core.handle (new ControllerInputEvent (2, 2, session, InputKind.BUTTON, InputPhase.BEGIN, 127), pageCaptureSnapshot (capabilities, 2, Set.of (session), empty));
            final var request = new de.mossgrabers.pull.core.api.LegacyControllerPageRequest (1, selected.desiredControllerState ().page ().revision (), 0, de.mossgrabers.pull.core.api.LegacyControllerPageRequest.Operation.SELECT, "DEVICE_PARAMS");
            final var result = core.handle (new SnapshotChangedEvent (3, 3), pageCaptureSnapshot (capabilities, 3, Set.of (), new de.mossgrabers.pull.core.api.LegacyControllerPageRequests (List.of (request))));
            assertEquals ("DEVICE_PARAMS", result.desiredControllerState ().page ().effectivePage ().legacyAlias ());
            assertTrue (result.desiredControllerState ().workspace ().facets ().contains (ControllerViewFacet.SESSION_GRID_FULL));
            assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), result.desiredInputRoutes ().mode (PushControlIds.button ("ARROW_UP"), InputKind.BUTTON));
            final var environment = new ControllerRuntimeEnvironment (host (1), new PassthroughControllerBridge (), new RecordingLog (), () -> 0);
            environment.setInputRouteValidator (ignored -> true);
            environment.setControllerActionValidator (ignored -> true);
            environment.setPhysicalLightOwnerValidator (ignored -> true);
            assertNotNull (environment.prepare (result));
            for (final String horizontal: List.of ("ARROW_LEFT", "ARROW_RIGHT"))
            {
                final var route = new DesiredInputRoutes (Set.of (new InputRoute (PushControlIds.button (horizontal), InputKind.BUTTON, InputRouteMode.EXCLUSIVE)));
                final var stolen = new CoreResult (DesiredHardwareOutput.empty (), route, DesiredBridgeSubscriptions.empty (), Map.of (), result.desiredControllerState (), DesiredNoteRepeat.unowned (),
                    de.mossgrabers.pull.core.api.DesiredControllerActions.empty (), DesiredParameterBanks.empty (), DesiredParameterInteraction.empty (), List.of ());
                assertThrows (IllegalArgumentException.class, () -> environment.prepare (stolen));
            }
        }
    }


    @Test
    void realCoreCancelledFrameReleaseIsInertThroughParentPreparation () throws Exception
    {
        final var classes = java.nio.file.Path.of ("../pull-core/target/classes").toAbsolutePath ().normalize ();
        try (final var loader = new java.net.URLClassLoader (new java.net.URL[] {classes.toUri ().toURL ()}, getClass ().getClassLoader ()))
        {
            final var provider = (de.mossgrabers.pull.core.api.CoreProvider) loader.loadClass ("de.mossgrabers.pull.core.runtime.PullCoreProvider").getConstructor ().newInstance ();
            final var core = provider.create ();
            final var capabilities = provider.descriptor ().requiredCapabilities ();
            final var empty = de.mossgrabers.pull.core.api.LegacyControllerPageRequests.empty ();
            final var started = core.start (pageCaptureSnapshot (capabilities, 1, Set.of (), empty), Optional.empty ());
            final var row = PushControlIds.button ("ROW1_4");
            core.handle (new ControllerInputEvent (2, 2, row, InputKind.BUTTON, InputPhase.BEGIN, 127), pageCaptureSnapshot (capabilities, 2, Set.of (row), empty));
            final var request = new de.mossgrabers.pull.core.api.LegacyControllerPageRequest (1, started.desiredControllerState ().page ().revision (), 0, de.mossgrabers.pull.core.api.LegacyControllerPageRequest.Operation.SELECT, "TRACK");
            core.handle (new de.mossgrabers.pull.core.api.event.SnapshotChangedEvent (3, 3), pageCaptureSnapshot (capabilities, 3, Set.of (row), new de.mossgrabers.pull.core.api.LegacyControllerPageRequests (List.of (request))));
            final var released = core.handle (new ControllerInputEvent (4, 4, row, InputKind.BUTTON, InputPhase.END, 0), pageCaptureSnapshot (capabilities, 4, Set.of (), new de.mossgrabers.pull.core.api.LegacyControllerPageRequests (1, List.of ())));
            assertEquals ("TRACK", released.desiredControllerState ().page ().effectivePage ().legacyAlias ());
            assertEquals (0, released.effects ().stream ().filter (de.mossgrabers.pull.core.api.effect.ToggleApplicationPanelEffect.class::isInstance).count ());
            assertFalse (released.desiredBridgeSubscriptions ().includes (BridgeSubscription.APPLICATION_UI));
            final var environment = new ControllerRuntimeEnvironment (host (1), new PassthroughControllerBridge (), new RecordingLog (), () -> 0);
            environment.setInputRouteValidator (ignored -> true);
            environment.setControllerActionValidator (ignored -> true);
            environment.setPhysicalLightOwnerValidator (ignored -> true);
            assertTrue (environment.prepare (released) != null, "the actual core release result must survive the parent contract");
        }
    }

    private static ControllerSnapshot pageCaptureSnapshot (final de.mossgrabers.pull.core.api.ShellCapabilities capabilities, final long sequence, final Set<ControlId> pressed, final de.mossgrabers.pull.core.api.LegacyControllerPageRequests requests)
    {
        final var e = de.mossgrabers.pull.core.api.ControllerBridgeSnapshot.empty ();
        final var layout = new de.mossgrabers.pull.core.api.ControllerLayoutSnapshot (1, "", "FRAME", false, false, 0, de.mossgrabers.pull.core.api.GridPressureConfiguration.OFF);
        final var ui = new de.mossgrabers.pull.core.api.ApplicationUiSnapshot (1, "project", "ARRANGE", de.mossgrabers.pull.core.api.ArrangerUiSnapshot.empty (), de.mossgrabers.pull.core.api.MixerUiSnapshot.empty ());
        final var bridge = new de.mossgrabers.pull.core.api.ControllerBridgeSnapshot (e.transport (), e.selectedTrack (), e.sessionBank (), layout, e.noteView (), e.noteRepeat (), e.drum (), e.parameters (), e.controllerMappingFeedback (), e.master (), e.project (), e.automation (), e.encoderConfiguration (), e.currentTrackBank (), e.transportSettings (), e.controllerSettings (), ui, requests, e.browser ());
        return new ControllerSnapshot (sequence, sequence, capabilities, bridge, ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.empty (), pressed, Set.of ());
    }


    @Test
    void departingPageCannotRetainATouchWithoutCurrentExclusiveOwnership ()
    {
        final PassthroughControllerBridge bridge = new PassthroughControllerBridge ();
        bridge.recordTouches = true;
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host (1), bridge, new RecordingLog (), () -> 0);
        environment.setInputRouteValidator (ignored -> true);
        final ControlId knob = PushControlIds.continuous ("KNOB1");
        final ParameterTargetRef target = new ParameterTargetRef (ParameterTargetKind.LIVE, "original-parameter", 1);
        environment.controllerInput (knob, InputKind.TOUCH, InputPhase.BEGIN, 127);
        final var touch = new DesiredParameterTouches (Map.of (knob, target));
        final CoreResult begin = touchResult (touch, new DesiredInputRoutes (Set.of (new InputRoute (knob, InputKind.TOUCH, InputRouteMode.EXCLUSIVE))), new DesiredControllerState (DesiredControllerWorkspace.empty (), DesiredNotePerformance.inactive (), corePage ()));
        environment.commit (1, environment.prepare (begin));
        environment.apply (1);
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (touchResult (touch, DesiredInputRoutes.empty (), DesiredControllerState.empty ())), "a previously acquired lease grants no offscreen continuation permission");
    }

    private static CoreResult touchResult (final DesiredParameterTouches touches, final DesiredInputRoutes routes, final DesiredControllerState state)
    {
        return new CoreResult (DesiredHardwareOutput.empty (), routes, new DesiredBridgeSubscriptions (Set.of (BridgeSubscription.PARAMETERS)), Map.of (), state,
            DesiredNoteRepeat.unowned (), de.mossgrabers.pull.core.api.DesiredControllerActions.empty (), new DesiredParameterBanks (Set.of (ParameterBankId.PROJECT_REMOTE)),
            DesiredParameterInteraction.empty (), touches, CoreExecutionRequirements.empty (), List.of ());
    }


    @Test
    void orderedTouchEffectsPreserveResetTouchEnabledOrderAndRequireDesiredLease ()
    {
        final PassthroughControllerBridge bridge = new PassthroughControllerBridge ();
        bridge.recordTouches = true;
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host (1), bridge, new RecordingLog (), () -> 0);
        environment.setInputRouteValidator (ignored -> true);
        final ControlId knob = PushControlIds.continuous ("KNOB3");
        final ParameterTargetRef target = new ParameterTargetRef (ParameterTargetKind.LIVE, "selected-send", 1);
        final DesiredInputRoutes routes = new DesiredInputRoutes (Set.of (new InputRoute (knob, InputKind.TOUCH, InputRouteMode.EXCLUSIVE)));
        final CoreResult result = new CoreResult (
            DesiredHardwareOutput.empty (), routes, new DesiredBridgeSubscriptions (Set.of (BridgeSubscription.PARAMETERS)), Map.of (),
            new DesiredControllerState (new DesiredControllerWorkspace ("Track", Set.of (ControllerViewFacet.TRACK_MIXER_PAGE), SessionBankShape.empty (), "TRACK"), DesiredNotePerformance.inactive (), corePage ()),
            DesiredNoteRepeat.unowned (), de.mossgrabers.pull.core.api.DesiredControllerActions.empty (),
            new DesiredParameterBanks (Set.of (ParameterBankId.SELECTED_TRACK, ParameterBankId.SELECTED_TRACK_SENDS)), DesiredParameterInteraction.empty (),
            new DesiredParameterTouches (Map.of (knob, target)), CoreExecutionRequirements.empty (), List.of (
                new ResetParameterEffect (target),
                new de.mossgrabers.pull.core.api.effect.AcquireParameterTouchEffect (knob, target),
                new de.mossgrabers.pull.core.api.effect.SetParameterEnabledEffect (target, false)));
        final PreparedCoreResult prepared = environment.prepare (result);
        environment.commit (1, prepared);
        assertTrue (bridge.applicationOrder.isEmpty ());
        environment.apply (1);
        assertEquals (List.of ("touch-release", "controller-state", "note-repeat", "reset", "touch-acquire-ordered", "enabled", "touch-acquire"), bridge.applicationOrder);
        environment.apply (1);
        assertEquals (7, bridge.applicationOrder.size (), "unchanged replay cannot repeat reset or touch acquisition");
        final CoreResult withoutLease = new CoreResult (
            result.desiredOutput (), result.desiredInputRoutes (), result.desiredBridgeSubscriptions (), result.desiredClipBindings (), result.desiredControllerState (),
            result.desiredNoteRepeat (), result.desiredControllerActions (), result.desiredParameterBanks (), result.desiredParameterInteraction (), DesiredParameterTouches.empty (), result.executionRequirements (), result.effects ());
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (withoutLease));
    }


    @Test
    void commitsPageRowLightsAndDisplayOnlyWithAnInstalledInertAdapter ()
    {
        final PassthroughControllerBridge bridge = new PassthroughControllerBridge ();
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host (1), bridge, new RecordingLog (), () -> 0);
        final ControlId previous = PushControlIds.button ("ROW2_7");
        final ControllerDisplayScene display = new ControllerDisplayScene (960, 160, List.of (new DisplayCommand.Rectangle (0, 0, 960, 160, OFF)));
        final DesiredControllerWorkspace workspace = new DesiredControllerWorkspace ("Master", Set.of (ControllerViewFacet.MASTER_CONTROLS), SessionBankShape.empty (), "MASTER");
        final CoreResult masterResult = new CoreResult (
            new DesiredHardwareOutput (Map.of (previous, BRIGHT_RED), display),
            DesiredInputRoutes.empty (),
            DesiredBridgeSubscriptions.empty (),
            Map.of (),
            new DesiredControllerState (workspace, DesiredNotePerformance.inactive (), corePage ()),
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
    void blinkingOutputCommitsWithItsBaseAndClearsOnOmissionOrQuarantine ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        final ControlId pad = PushControlIds.pad (1);
        final LightBlink blink = new LightBlink (BRIGHT_RED, true);
        final DesiredHardwareOutput output = new DesiredHardwareOutput (Map.of (pad, OFF), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), DesiredControllerMappings.empty (), DesiredTouchStrip.unowned (), Map.of (pad, blink));
        environment.setPhysicalLightOwnerValidator (ignored -> true);
        final CoreResult result = routedResult (output, DesiredInputRoutes.empty ());
        final PreparedCoreResult prepared = environment.prepare (result);
        assertNull (environment.lightBlink (pad), "preparation does not publish output");
        environment.commit (9, prepared);
        environment.apply (9);
        assertEquals (blink, environment.lightBlink (pad));
        assertEquals (OFF, environment.lightColor (pad));

        commitAndApply (environment, 9, result (Map.of (pad, BRIGHT_RED), Map.of (), List.of ()));
        assertNull (environment.lightBlink (pad));
        assertEquals (BRIGHT_RED, environment.lightColor (pad));
        commitAndApply (environment, 9, result);
        environment.quarantine (9);
        assertNull (environment.lightBlink (pad));
        assertEquals (OFF, environment.lightColor (pad));

        final ControlId button = PushControlIds.button ("PLAY");
        final DesiredHardwareOutput unsupported = new DesiredHardwareOutput (Map.of (button, OFF), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), DesiredControllerMappings.empty (), DesiredTouchStrip.unowned (), Map.of (button, blink));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (routedResult (unsupported, DesiredInputRoutes.empty ())));
    }


    @Test
    void admitsSemanticMappingsOnlyWithTheirExclusivePhysicalRouteAndAuthoritativeFeedback ()
    {
        final PassthroughControllerBridge bridge = new PassthroughControllerBridge ();
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host (1), bridge, new RecordingLog (), () -> 0);
        final AtomicReference<DesiredControllerMappings> mappingsAtDeferredRelease = new AtomicReference<> ();
        environment.setDeferredInputRelease (() -> mappingsAtDeferredRelease.set (environment.activeControllerMappings ()));
        environment.setInputRouteValidator (ignored -> true);
        final ControlId pad = CoreControls.DRUM_CONTROL_PADS.getFirst ();
        final ControllerMappingBinding binding = new ControllerMappingBinding (pad, CoreControllerMappings.trackBank (0).getFirst (), ControllerMappingValue.MAXIMUM, MAPPING_CONTEXT);
        final DesiredControllerMappings mappings = new DesiredControllerMappings (Set.of (binding));
        final DesiredInputRoutes routes = new DesiredInputRoutes (Set.of (new InputRoute (pad, InputKind.PAD, InputRouteMode.EXCLUSIVE)));
        final DesiredHardwareOutput output = new DesiredHardwareOutput (
            Map.of (pad, BRIGHT_RED),
            ControllerDisplayScene.empty (),
            ControllerPadGridOverlay.inactive (),
            ControllerDisplayOverlay.inactive (),
            mappings);
        final CoreResult result = routedResult (output, routes, CONTROLLER_MAPPING_SUBSCRIPTIONS);

        commitAndApply (environment, 9, result);
        assertEquals (mappings, environment.activeControllerMappings ());
        assertTrue (environment.debugLightObservation (pad).mappingDesired ());

        bridge.mappingContext = ControllerMappingContext.empty ();
        assertTrue (environment.activeControllerMappings ().bindings ().isEmpty (), "a stale owner is revoked before another core result");
        bridge.mappingContext = MAPPING_CONTEXT;
        assertEquals (mappings, environment.activeControllerMappings ());

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

        assertThrows (IllegalArgumentException.class, () -> environment.prepare (routedResult (output, routes)));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (routedResult (output, DesiredInputRoutes.empty (), CONTROLLER_MAPPING_SUBSCRIPTIONS)));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (routedResult (
            new DesiredHardwareOutput (Map.of (), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), mappings),
            routes,
            CONTROLLER_MAPPING_SUBSCRIPTIONS)));
        final DesiredControllerMappings unsupportedSemanticMapping = new DesiredControllerMappings (Set.of (
            new ControllerMappingBinding (pad, new ControllerMappingId ("not-installed"))));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (routedResult (
            new DesiredHardwareOutput (Map.of (pad, BRIGHT_RED), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), unsupportedSemanticMapping),
            routes,
            CONTROLLER_MAPPING_SUBSCRIPTIONS)));
        final DesiredControllerMappings legacyMapping = new DesiredControllerMappings (Set.of (
            new ControllerMappingBinding (pad, CoreControllerMappings.DRUM_CONTROL_PADS.getFirst (), ControllerMappingValue.MAXIMUM, MAPPING_CONTEXT)));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (routedResult (
            new DesiredHardwareOutput (Map.of (pad, BRIGHT_RED), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), legacyMapping),
            routes, CONTROLLER_MAPPING_SUBSCRIPTIONS)));
        final ControlId unsupportedPhysical = new ControlId ("not-installed");
        final DesiredControllerMappings unsupportedPhysicalMapping = new DesiredControllerMappings (Set.of (
            new ControllerMappingBinding (unsupportedPhysical, CoreControllerMappings.trackBank (0).getFirst ())));
        final DesiredInputRoutes unsupportedPhysicalRoute = new DesiredInputRoutes (Set.of (
            new InputRoute (unsupportedPhysical, InputKind.PAD, InputRouteMode.EXCLUSIVE)));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (routedResult (
            new DesiredHardwareOutput (Map.of (unsupportedPhysical, BRIGHT_RED), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), unsupportedPhysicalMapping),
            unsupportedPhysicalRoute,
            CONTROLLER_MAPPING_SUBSCRIPTIONS)));
    }


    @Test
    void debugLightObservationUsesOnlyAvailableAuthoritativeMappedPadReadback ()
    {
        final PassthroughControllerBridge bridge = new PassthroughControllerBridge ();
        final ControllerRuntimeEnvironment environment = new ControllerRuntimeEnvironment (host (1), bridge, new RecordingLog (), () -> 0);
        environment.setInputRouteValidator (ignored -> true);
        final ControlId first = CoreControls.DRUM_CONTROL_PADS.get (0);
        final ControlId second = CoreControls.DRUM_CONTROL_PADS.get (1);
        final ControllerMappingId firstMapping = CoreControllerMappings.trackBank (0).get (0);
        final ControllerMappingId secondMapping = CoreControllerMappings.trackBank (0).get (1);
        final DesiredControllerMappings mappings = new DesiredControllerMappings (Set.of (
            new ControllerMappingBinding (first, firstMapping, ControllerMappingValue.MAXIMUM, MAPPING_CONTEXT),
            new ControllerMappingBinding (second, secondMapping, ControllerMappingValue.MAXIMUM, MAPPING_CONTEXT)));
        final DesiredInputRoutes routes = new DesiredInputRoutes (Set.of (
            new InputRoute (first, InputKind.PAD, InputRouteMode.EXCLUSIVE),
            new InputRoute (second, InputKind.PAD, InputRouteMode.EXCLUSIVE)));
        final DesiredHardwareOutput output = new DesiredHardwareOutput (
            Map.of (first, BRIGHT_RED, second, BRIGHT_RED),
            ControllerDisplayScene.empty (),
            ControllerPadGridOverlay.inactive (),
            ControllerDisplayOverlay.inactive (),
            mappings);
        commitAndApply (environment, 9, routedResult (output, routes, CONTROLLER_MAPPING_SUBSCRIPTIONS));

        assertNull (environment.debugLightObservation (first).mappedTarget ());
        final ControllerMappingTarget belowMidpoint = new ControllerMappingTarget (true, 0.25);
        final ControllerMappingTarget aboveMidpoint = new ControllerMappingTarget (true, 0.75);
        bridge.setControllerMappingFeedback (new ControllerMappingFeedbackSnapshot (true, Map.of (firstMapping, belowMidpoint, secondMapping, aboveMidpoint)));

        assertEquals (belowMidpoint, environment.debugLightObservation (first).mappedTarget ());
        assertEquals (aboveMidpoint, environment.debugLightObservation (second).mappedTarget ());
        assertNull (environment.debugLightObservation (CoreControls.DRUM_RATES.getFirst ()).mappedTarget ());

        final ControllerMappingTarget absentTarget = new ControllerMappingTarget (false, 0.75);
        bridge.setControllerMappingFeedback (new ControllerMappingFeedbackSnapshot (true, Map.of (firstMapping, absentTarget)));
        assertEquals (absentTarget, environment.debugLightObservation (first).mappedTarget ());
        assertNull (environment.debugLightObservation (second).mappedTarget (), "unsupported inventory has no target observation");
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
    void touchStripCommitsTransactionallyAndFailsClosedWithoutRevivingLegacyOutput ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        assertEquals (DesiredTouchStrip.off (), environment.touchStrip ());
        final DesiredTouchStrip observedPosition = DesiredTouchStrip.pitchBend (12345);
        final CoreResult result = new CoreResult (
            new DesiredHardwareOutput (Map.of (), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), DesiredControllerMappings.empty (), observedPosition),
            DesiredInputRoutes.empty (), DesiredBridgeSubscriptions.empty (), Map.of (),
            de.mossgrabers.pull.core.api.DesiredControllerActions.empty (), DesiredParameterBanks.empty (),
            DesiredParameterInteraction.empty (), List.of ());
        final var prepared = environment.prepare (result);
        assertEquals (DesiredTouchStrip.off (), environment.touchStrip ());

        environment.commit (9, prepared);
        assertEquals (observedPosition, environment.touchStrip ());
        environment.quarantine (8);
        assertEquals (observedPosition, environment.touchStrip ());
        environment.quarantine (9);
        assertEquals (DesiredTouchStrip.off (), environment.touchStrip ());

        environment.commit (10, environment.prepare (result));
        assertEquals (observedPosition, environment.touchStrip ());
        environment.invalidate (11);
        assertEquals (DesiredTouchStrip.off (), environment.touchStrip ());
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
    void quarantineBlanksOwnedOutputAndClearsTransientOverlaysAndReturnsTheActiveFill ()
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
        final DesiredControllerWorkspace workspace = new DesiredControllerWorkspace ("Master", Set.of (ControllerViewFacet.MASTER_CONTROLS), SessionBankShape.empty (), "MASTER");
        final ControlId ratePad = CoreControls.DRUM_RATES.get (0);
        final ControlId tap = PushControlIds.button ("TAP_TEMPO");
        final ControlId undo = PushControlIds.button ("UNDO");
        final PushColorManager colorManager = new PushColorManager ();
        // These are the permanent core-only light suppliers installed for the inert commands.
        final IntSupplier tapSupplier = () -> PushColorManager.resolveCoreButtonColor (colorManager, ButtonID.TAP_TEMPO, environment.lightColor (tap));
        final IntSupplier undoSupplier = () -> PushColorManager.resolveCoreButtonColor (colorManager, ButtonID.UNDO, environment.lightColor (undo));
        final CoreResult result = new CoreResult (
            new DesiredHardwareOutput (Map.of (previous, BRIGHT_RED, ratePad, BRIGHT_RED, tap, new RgbColor (255, 255, 255), undo, new RgbColor (60, 60, 60)), display, padOverlay, displayOverlay),
            DesiredInputRoutes.empty (), DesiredBridgeSubscriptions.empty (), Map.of (FIRST, FIRST_TARGET),
            new DesiredControllerState (workspace, DesiredNotePerformance.inactive (), corePage ()), DesiredNoteRepeat.unowned (), de.mossgrabers.pull.core.api.DesiredControllerActions.empty (), DesiredParameterBanks.empty (),
            DesiredParameterInteraction.empty (), new CoreExecutionRequirements (true),
            List.of (new PressClipTargetEffect (FIRST, 1, FIRST_TARGET, LAUNCH_POLICY)));

        environment.setFillPressed (FIRST, true);
        commitAndApply (environment, 9, result);
        assertTrue (environment.ownsLight (tap));
        assertEquals (127, tapSupplier.getAsInt ());
        assertEquals (30, undoSupplier.getAsInt ());
        acknowledgeLaunch (host, environment, FIRST);
        assertEquals (Optional.of (FIRST), environment.snapshot ().activeClipLaunchOwner ());
        bridge.failAbandon = true;
        failDeferredRelease.set (true);

        environment.quarantine (9);

        assertEquals (OFF, environment.lightColor (previous));
        assertEquals (OFF, environment.lightColor (ratePad));
        assertFalse (environment.ownsLight (tap));
        assertFalse (environment.debugLightObservation (tap).present ());
        assertEquals (0, tapSupplier.getAsInt ());
        assertEquals (0, undoSupplier.getAsInt ());
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
    void quarantineKeepsTheOwnedDisplayPlaneBlackWithoutRevivingLegacyColumns ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        final List<String> draws = new ArrayList<> ();
        final TestSceneDisplay display = new TestSceneDisplay (displayHost (draws), environment::controllerDisplay);
        final ControllerDisplayScene scene = new ControllerDisplayScene (960, 160, List.of (new DisplayCommand.TextAt ("old parameter", 10, 20, BRIGHT_RED, 12)));
        final CoreResult result = new CoreResult (new DesiredHardwareOutput (Map.of (), scene), DesiredInputRoutes.empty (), DesiredBridgeSubscriptions.empty (), Map.of (), de.mossgrabers.pull.core.api.DesiredControllerActions.empty (), DesiredParameterBanks.empty (), DesiredParameterInteraction.empty (), List.of ());
        try
        {
            // An unowned legacy page remains available before any core scene is committed.
            commitAndApply (environment, 1, CoreResult.empty ());
            environment.quarantine (1);
            display.addElement (ignored -> draws.add ("legacy"));
            display.send ();
            assertTrue (draws.contains ("legacy"));

            draws.clear ();
            commitAndApply (environment, 2, result);
            display.addElement (ignored -> draws.add ("legacy"));
            display.send ();
            assertTrue (draws.contains ("old parameter"));
            assertFalse (draws.contains ("legacy"));

            draws.clear ();
            environment.quarantine (2);
            display.addElement (ignored -> draws.add ("legacy"));
            display.send ();
            assertEquals (new ControllerDisplayScene (960, 160, List.of (new DisplayCommand.Rectangle (0, 0, 960, 160, OFF))), environment.controllerDisplay ());
            assertTrue (draws.contains ("black viewport"));
            assertFalse (draws.contains ("old parameter"));
            assertFalse (draws.contains ("legacy"));

            draws.clear ();
            commitAndApply (environment, 3, result);
            display.send ();
            assertTrue (draws.contains ("old parameter"));
        }
        finally
        {
            display.shutdown ();
        }
    }


    @Test
    void admitsOnlyTheBoundedGlobalAndSessionButtonLightsOutsideMaster ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        final ControlId play = PushControlIds.button ("PLAY");
        final ControlId record = PushControlIds.button ("RECORD");
        final ControlId mute = PushControlIds.button ("MUTE");
        final ControlId solo = PushControlIds.button ("SOLO");
        final ControlId stopClip = PushControlIds.button ("STOP_CLIP");
        final ControlId unsupported = PushControlIds.button ("STOP");

        environment.commit (7, environment.prepare (result (
            Map.of (play, BRIGHT_RED, record, DIM_RED, mute, DIM_RED, solo, BRIGHT_RED, stopClip, DIM_RED),
            Map.of (),
            List.of ())));

        assertEquals (BRIGHT_RED, environment.lightColor (play));
        assertEquals (DIM_RED, environment.lightColor (record));
        assertEquals (DIM_RED, environment.lightColor (mute));
        assertEquals (BRIGHT_RED, environment.lightColor (solo));
        assertEquals (DIM_RED, environment.lightColor (stopClip));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (
            Map.of (unsupported, BRIGHT_RED),
            Map.of (),
            List.of ())));

        environment.invalidate (8);
        assertEquals (OFF, environment.lightColor (play));
        assertEquals (OFF, environment.lightColor (record));
        assertEquals (OFF, environment.lightColor (mute));
        assertEquals (OFF, environment.lightColor (solo));
        assertEquals (OFF, environment.lightColor (stopClip));
    }


    @Test
    void genericPhysicalLightValidationOwnsOnlyTheExplicitCompleteResult ()
    {
        final ControllerRuntimeEnvironment environment = environment (host (1));
        final ControlId browse = PushControlIds.button ("BROWSE");
        final ControlId pad = PushControlIds.pad (1);
        final ControlId unknown = new ControlId ("not-installed");
        environment.setPhysicalLightOwnerValidator (Set.of (browse, pad)::contains);

        commitAndApply (environment, 7, result (Map.of (browse, BRIGHT_RED, pad, DIM_RED), Map.of (), List.of ()));
        assertTrue (environment.ownsLight (browse));
        assertTrue (environment.ownsLight (pad));
        assertEquals (BRIGHT_RED, environment.lightColor (browse));
        assertEquals (DIM_RED, environment.lightColor (pad));
        assertThrows (IllegalArgumentException.class, () -> environment.prepare (result (Map.of (unknown, BRIGHT_RED), Map.of (), List.of ())));

        commitAndApply (environment, 8, result (Map.of (), Map.of (), List.of ()));
        assertFalse (environment.ownsLight (browse));
        assertFalse (environment.ownsLight (pad));
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


    private static IHost displayHost (final List<String> draws)
    {
        final IGraphicsContext context = (IGraphicsContext) Proxy.newProxyInstance (IGraphicsContext.class.getClassLoader (), new Class<?> [] { IGraphicsContext.class }, (ignored, method, arguments) -> {
            if ("drawTextAt".equals (method.getName ()))
                draws.add ((String) arguments[0]);
            if ("fillRectangle".equals (method.getName ()) && ((Number) arguments[2]).doubleValue () == 960 && ((Number) arguments[3]).doubleValue () == 160 && ColorEx.BLACK.equals (arguments[4]))
                draws.add ("black viewport");
            return displayDefault (method.getReturnType ());
        });
        final IBitmap bitmap = new IBitmap ()
        {
            @Override public void render (final boolean antialias, final IRenderer renderer) { renderer.render (context); }
            @Override public void encode (final IEncoder encoder) { }
        };
        final IImage image = (IImage) Proxy.newProxyInstance (IImage.class.getClassLoader (), new Class<?> [] { IImage.class }, (ignored, method, arguments) -> displayDefault (method.getReturnType ()));
        return (IHost) Proxy.newProxyInstance (IHost.class.getClassLoader (), new Class<?> [] { IHost.class }, (ignored, method, arguments) -> switch (method.getName ())
        {
            case "createBitmap" -> bitmap;
            case "loadSVG" -> image;
            default -> displayDefault (method.getReturnType ());
        });
    }


    private static Object displayDefault (final Class<?> type)
    {
        if (type == boolean.class) return Boolean.FALSE;
        if (type == int.class) return Integer.valueOf (0);
        if (type == long.class) return Long.valueOf (0);
        if (type == double.class) return Double.valueOf (0);
        if (type == ColorEx.class) return ColorEx.BLACK;
        return null;
    }


    private static final class TestSceneDisplay extends AbstractGraphicDisplay
    {
        private TestSceneDisplay (final IHost host, final Supplier<ControllerDisplayScene> scene)
        {
            super (host, (IGraphicsConfiguration) Proxy.newProxyInstance (IGraphicsConfiguration.class.getClassLoader (), new Class<?> [] { IGraphicsConfiguration.class }, (ignored, method, arguments) -> displayDefault (method.getReturnType ())), new DefaultGraphicsDimensions (960, 160, 1024));
            this.setFullScreenBaseSupplier ( () -> scene.get ().isPresent () ? new DisplaySceneComponent (scene.get ()) : null);
        }

        @Override public void notify (final String message) { }
        @Override protected void send (final IBitmap image) { }
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


    @Test
    void pageParameterIndicationsRequireTheirDeclaredSnapshotAndBank ()
    {
        final var environment = new ControllerRuntimeEnvironment (host (1), new PassthroughControllerBridge (), new RecordingLog (), () -> 0);
        final var page = new de.mossgrabers.pull.core.api.DesiredControllerPageState (1, de.mossgrabers.pull.core.api.ControllerPageRef.core ("arbitrary"), de.mossgrabers.pull.core.api.ControllerPageRef.none (), java.util.Optional.empty (), 0, Set.of (de.mossgrabers.pull.core.api.ParameterSlot.selectedDeviceRemote (0)));
        for (final var subscriptions: List.of (DesiredBridgeSubscriptions.empty (), new DesiredBridgeSubscriptions (Set.of (BridgeSubscription.PARAMETERS))))
        {
            final var result = new CoreResult (DesiredHardwareOutput.empty (), DesiredInputRoutes.empty (), subscriptions, Map.of (),
                new DesiredControllerState (DesiredControllerWorkspace.empty (), DesiredNotePerformance.inactive (), page), DesiredNoteRepeat.unowned (),
                de.mossgrabers.pull.core.api.DesiredControllerActions.empty (), DesiredParameterBanks.empty (), DesiredParameterInteraction.empty (), List.of ());
            assertThrows (IllegalArgumentException.class, () -> environment.prepare (result));
        }
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
        return routedResult (output, routes, DesiredBridgeSubscriptions.empty ());
    }


    private static CoreResult routedResult (final DesiredHardwareOutput output, final DesiredInputRoutes routes, final DesiredBridgeSubscriptions subscriptions)
    {
        return new CoreResult (
            output,
            routes,
            subscriptions,
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


    private static CoreResult executionResult (final CoreExecutionRequirements requirements)
    {
        return new CoreResult (
            DesiredHardwareOutput.empty (), DesiredInputRoutes.empty (), DesiredBridgeSubscriptions.empty (), Map.of (),
            DesiredControllerState.empty (), DesiredNoteRepeat.unowned (), de.mossgrabers.pull.core.api.DesiredControllerActions.empty (),
            DesiredParameterBanks.empty (), DesiredParameterInteraction.empty (), requirements, List.of ());
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


    private static de.mossgrabers.pull.core.api.DesiredControllerPageState corePage ()
    {
        return new de.mossgrabers.pull.core.api.DesiredControllerPageState (1, de.mossgrabers.pull.core.api.ControllerPageRef.core ("unregistered-test-page"), de.mossgrabers.pull.core.api.ControllerPageRef.none (), java.util.Optional.empty (), 0);
    }


    private static final class PassthroughControllerBridge implements ControllerBridge
    {
        private ControllerMappingContext mappingContext = MAPPING_CONTEXT;

        @Override
        public boolean supportsPageInput (final de.mossgrabers.pull.core.api.DesiredControllerPageState page, final ControlId control, final InputKind kind)
        {
            return page.effectivePage ().kind () == de.mossgrabers.pull.core.api.ControllerPageRef.Kind.CORE && de.mossgrabers.controller.ableton.push.mode.CorePageMode.containsInput (control, kind);
        }

        @Override
        public boolean supportsPageLight (final de.mossgrabers.pull.core.api.DesiredControllerPageState page, final ControlId control)
        {
            return page.effectivePage ().kind () == de.mossgrabers.pull.core.api.ControllerPageRef.Kind.CORE && de.mossgrabers.controller.ableton.push.mode.CorePageMode.containsLight (control);
        }



        @Override
        public boolean controllerMappingContextMatches (final ControllerMappingContext context)
        {
            return this.mappingContext.equals (context);
        }


        private DesiredControllerWorkspace appliedWorkspace = DesiredControllerWorkspace.empty ();
        private DesiredNotePerformance preparedNotePerformance = DesiredNotePerformance.inactive ();
        private DesiredNotePerformance appliedNotePerformance = DesiredNotePerformance.inactive ();
        private DesiredNoteRepeat preparedNoteRepeat = DesiredNoteRepeat.unowned ();
        private DesiredNoteRepeat appliedNoteRepeat = DesiredNoteRepeat.unowned ();
        private ControllerBridgeSnapshot snapshot = ControllerBridgeSnapshot.empty ();
        private final List<String> applicationOrder = new ArrayList<> ();
        private boolean failAbandon;
        private boolean recordTouches;


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
        public Map<ControlId, ParameterTouchLease> prepareParameterTouches (final DesiredParameterTouches touches, final DesiredParameterBanks banks)
        {
            final Map<ControlId, ParameterTouchLease> leases = new LinkedHashMap<> ();
            touches.targets ().forEach ((control, target) -> leases.put (control, new ParameterTouchLease () {}));
            return leases;
        }


        @Override
        public void releaseParameterTouches (final Map<ControlId, ParameterTouchLease> touches)
        {
            if (this.recordTouches)
                this.applicationOrder.add ("touch-release");
        }


        @Override
        public void acquireParameterTouches (final Map<ControlId, ParameterTouchLease> touches)
        {
            if (this.recordTouches)
                this.applicationOrder.add ("touch-acquire");
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
            if (effect instanceof de.mossgrabers.pull.core.api.effect.ToggleApplicationPanelEffect)
                return new RecordedParameterAction ("application-panel");
            if (!this.recordTouches)
                return null;
            if (effect instanceof ResetParameterEffect)
                return new RecordedParameterAction ("reset");
            if (effect instanceof de.mossgrabers.pull.core.api.effect.AcquireParameterTouchEffect)
                return new RecordedParameterAction ("touch-acquire-ordered");
            if (effect instanceof de.mossgrabers.pull.core.api.effect.SetParameterEnabledEffect)
                return new RecordedParameterAction ("enabled");
            return null;
        }


        @Override
        public void apply (final PreparedAction action)
        {
            if (action instanceof final RecordedParameterAction recorded)
                this.applicationOrder.add (recorded.event ());
        }

        private record RecordedParameterAction (String event) implements PreparedAction
        {
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
