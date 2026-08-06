// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.CatalogClip;
import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.GridPressureConfiguration;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.TrackMonitorMode;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.effect.ClipLaunchMode;
import de.mossgrabers.pull.core.api.effect.ClipLaunchPolicy;
import de.mossgrabers.pull.core.api.effect.ClipLaunchQuantization;
import de.mossgrabers.pull.core.api.effect.ClipReleaseTrigger;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackAction;
import de.mossgrabers.pull.core.api.effect.SelectedTrackActionEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackBoolean;
import de.mossgrabers.pull.core.api.effect.SendNoteInputMidiEffect;
import de.mossgrabers.pull.core.api.effect.SetSelectedTrackBooleanEffect;
import de.mossgrabers.pull.core.api.effect.SetTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.TransportState;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import de.mossgrabers.pull.core.runtime.view.VsLiveWorkspace;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic behavior tests for the selected-track drum-fill controls.
 */
class PullControllerCoreTest
{
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private static final RgbColor AVAILABLE = new RgbColor (96, 30, 0);
    private static final RgbColor HELD = new RgbColor (255, 80, 0);
    private static final ControlId RECORD_BUTTON = PushControlIds.button ("RECORD");
    private static final ControlId SESSION_BUTTON = PushControlIds.button ("SESSION");
    private static final ControlId NOTE_BUTTON = PushControlIds.button ("NOTE");
    private static final ControlId SHIFT_BUTTON = PushControlIds.button ("SHIFT");
    private static final ControlId SELECT_BUTTON = PushControlIds.button ("SELECT");
    private static final ClipLaunchPolicy FILL_POLICY = new ClipLaunchPolicy (
        ClipLaunchQuantization.IMMEDIATE,
        ClipLaunchMode.LEGATO_FROM_CLIP_OR_PROJECT,
        ClipReleaseTrigger.ALTERNATE);


    @Test
    void bindsTheFirstTwelveCaseInsensitiveFillsInCatalogOrder ()
    {
        final List<CatalogClip> clips = new ArrayList<> ();
        clips.add (clip (100, "verse"));
        for (int index = 0; index < 14; index++)
            clips.add (clip (index, index % 2 == 0 ? "Fill " + index : "prefilled " + index));
        final FakeCoreHost host = host (new ClipCatalogSnapshot (5, clips));

        host.start (Optional.empty ());

        final Map<ControlId, ClipTargetId> bindings = host.effects ().desiredClipBindings ();
        assertEquals (12, bindings.size ());
        for (int index = 0; index < 12; index++)
            assertEquals (new ClipTargetId (index), bindings.get (CoreControls.DRUM_FILLS.get (index)));
        assertFalse (bindings.containsValue (new ClipTargetId (12)));
        assertEquals (Set.copyOf (CoreControls.DRUM_FILLS), host.effects ().desiredOutput ().lights ().keySet ());
        assertTrue (host.effects ().desiredOutput ().lights ().values ().stream ().allMatch (OFF::equals));
    }


    @Test
    void aReadyPadPressesOneTargetAndReleasesByOwner ()
    {
        final ClipTargetId first = new ClipTargetId (1);
        final ClipTargetId second = new ClipTargetId (2);
        final FakeCoreHost host = host (new ClipCatalogSnapshot (41, List.of (
            new CatalogClip (first, "Drum Fill"),
            new CatalogClip (second, "FILLER"))));
        host.start (Optional.empty ());
        host.armedClipTargets (host.effects ().desiredClipBindings ());

        assertEquals (AVAILABLE, light (host, CoreControls.DRUM_FILL_1));
        assertEquals (AVAILABLE, light (host, CoreControls.DRUM_FILL_2));

        host.button (CoreControls.DRUM_FILL_2, true);

        final PressClipTargetEffect press = host.effects ().clipLease (CoreControls.DRUM_FILL_2).orElseThrow ();
        assertEquals (CoreControls.DRUM_FILL_2, press.owner ());
        assertEquals (41, press.catalogGeneration ());
        assertEquals (second, press.target ());
        assertEquals (AVAILABLE, light (host, CoreControls.DRUM_FILL_2));

        host.activeClipLaunchOwner (Optional.of (CoreControls.DRUM_FILL_2));
        assertEquals (HELD, light (host, CoreControls.DRUM_FILL_2));

        host.button (CoreControls.DRUM_FILL_2, false);

        assertTrue (host.effects ().clipLease (CoreControls.DRUM_FILL_2).isEmpty ());
        assertInstanceOf (ReleaseClipTargetsEffect.class, host.effects ().executionOrder ().getLast ());
        assertEquals (HELD, light (host, CoreControls.DRUM_FILL_2));

        host.activeClipLaunchOwner (Optional.empty ());
        assertEquals (AVAILABLE, light (host, CoreControls.DRUM_FILL_2));
    }


    @Test
    void latestReadyFillPressRequestsOneReplacementWhileReadbackKeepsTheCurrentOwnerLit ()
    {
        final ClipTargetId first = new ClipTargetId (1);
        final ClipTargetId second = new ClipTargetId (2);
        final FakeCoreHost host = host (new ClipCatalogSnapshot (42, List.of (
            new CatalogClip (first, "fill one"),
            new CatalogClip (second, "fill two"))));
        host.start (Optional.empty ());
        host.armedClipTargets (host.effects ().desiredClipBindings ());

        host.button (CoreControls.DRUM_FILL_1, true);
        host.activeClipLaunchOwner (Optional.of (CoreControls.DRUM_FILL_1));
        host.button (CoreControls.DRUM_FILL_2, true);

        final List<CoreEffect> effects = host.effects ().executionOrder ();
        assertEquals (2, effects.size ());
        assertEquals (new PressClipTargetEffect (CoreControls.DRUM_FILL_2, 42, second, FILL_POLICY), effects.get (1));
        assertTrue (host.effects ().clipLease (CoreControls.DRUM_FILL_1).isEmpty ());
        assertEquals (second, host.effects ().clipLease (CoreControls.DRUM_FILL_2).orElseThrow ().target ());
        assertEquals (HELD, light (host, CoreControls.DRUM_FILL_1));
        assertEquals (AVAILABLE, light (host, CoreControls.DRUM_FILL_2));

        host.activeClipLaunchOwner (Optional.of (CoreControls.DRUM_FILL_2));
        assertEquals (AVAILABLE, light (host, CoreControls.DRUM_FILL_1));
        assertEquals (HELD, light (host, CoreControls.DRUM_FILL_2));

        host.button (CoreControls.DRUM_FILL_1, false);
        assertEquals (second, host.effects ().clipLease (CoreControls.DRUM_FILL_2).orElseThrow ().target ());
        assertEquals (HELD, light (host, CoreControls.DRUM_FILL_2));

        host.button (CoreControls.DRUM_FILL_2, false);
        assertTrue (host.effects ().clipLease (CoreControls.DRUM_FILL_2).isEmpty ());
        assertEquals (HELD, light (host, CoreControls.DRUM_FILL_2));

        host.activeClipLaunchOwner (Optional.empty ());
        assertEquals (AVAILABLE, light (host, CoreControls.DRUM_FILL_2));
    }


    @Test
    void aNewPressAfterReloadLeavesTheSingleActiveHandoffToTheShell ()
    {
        final ClipTargetId first = new ClipTargetId (1);
        final ClipTargetId second = new ClipTargetId (2);
        final ClipTargetId third = new ClipTargetId (3);
        final ClipCatalogSnapshot catalog = new ClipCatalogSnapshot (43, List.of (
            new CatalogClip (first, "fill one"),
            new CatalogClip (second, "fill two"),
            new CatalogClip (third, "fill three")));
        final Map<ControlId, ClipTargetId> armed = Map.of (
            CoreControls.DRUM_FILL_1, first,
            CoreControls.DRUM_FILL_2, second,
            CoreControls.DRUM_FILL_3, third);
        final PullCoreProvider provider = new PullCoreProvider ();
        final FakeCoreHost host = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities (), catalog, armed, Set.of (CoreControls.DRUM_FILL_1, CoreControls.DRUM_FILL_2));
        host.start (Optional.empty ());

        host.button (CoreControls.DRUM_FILL_3, true);

        assertEquals (List.of (new PressClipTargetEffect (CoreControls.DRUM_FILL_3, 43, third, FILL_POLICY)), host.effects ().executionOrder ());
    }


    @Test
    void startNeverSynthesizesALatePressForHeldReadyBindings ()
    {
        final ClipTargetId first = new ClipTargetId (7);
        final ClipTargetId second = new ClipTargetId (8);
        final ClipCatalogSnapshot catalog = new ClipCatalogSnapshot (9, List.of (
            new CatalogClip (first, "transition fill"),
            new CatalogClip (second, "another fill")));
        final Map<ControlId, ClipTargetId> armed = Map.of (
            CoreControls.DRUM_FILL_1, first,
            CoreControls.DRUM_FILL_2, second);
        final PullCoreProvider provider = new PullCoreProvider ();
        final FakeCoreHost host = new FakeCoreHost (
            provider.create (),
            provider.descriptor ().requiredCapabilities (),
            catalog,
            armed,
            Optional.of (CoreControls.DRUM_FILL_2),
            armed.keySet ());

        host.start (Optional.empty ());

        assertTrue (host.effects ().executionOrder ().isEmpty ());
        assertTrue (host.effects ().clipLease (CoreControls.DRUM_FILL_1).isEmpty ());
        assertTrue (host.effects ().clipLease (CoreControls.DRUM_FILL_2).isEmpty ());
        assertEquals (AVAILABLE, light (host, CoreControls.DRUM_FILL_1));
        assertEquals (HELD, light (host, CoreControls.DRUM_FILL_2));
    }


    @Test
    void activeOwnerStaysHeldWhenItsArmedBindingDisappears ()
    {
        final ClipTargetId target = new ClipTargetId (7);
        final ClipCatalogSnapshot catalog = new ClipCatalogSnapshot (9, List.of (new CatalogClip (target, "fill")));
        final PullCoreProvider provider = new PullCoreProvider ();
        final FakeCoreHost host = new FakeCoreHost (
            provider.create (),
            provider.descriptor ().requiredCapabilities (),
            catalog,
            Map.of (CoreControls.DRUM_FILL_1, target),
            Optional.of (CoreControls.DRUM_FILL_1),
            Set.of (CoreControls.DRUM_FILL_1));
        host.start (Optional.empty ());

        host.armedClipTargets (Map.of ());

        assertEquals (HELD, light (host, CoreControls.DRUM_FILL_1));
    }


    @Test
    void activeOwnerStaysHeldWhenTheSelectedTrackCatalogDisappears ()
    {
        final PullCoreProvider provider = new PullCoreProvider ();
        final ClipTargetId retained = new ClipTargetId (7);
        final FakeCoreHost host = new FakeCoreHost (
            provider.create (),
            provider.descriptor ().requiredCapabilities (),
            ClipCatalogSnapshot.empty (),
            Map.of (),
            Map.of (CoreControls.DRUM_FILL_1, retained),
            Optional.of (CoreControls.DRUM_FILL_1),
            Set.of ());

        host.start (Optional.empty ());

        assertTrue (host.effects ().desiredClipBindings ().isEmpty ());
        assertEquals (HELD, light (host, CoreControls.DRUM_FILL_1));
        assertEquals (OFF, light (host, CoreControls.DRUM_FILL_2));
    }


    @Test
    void onlyTheSingleShellRetainedTargetStaysReservedAfterCatalogReordering ()
    {
        final ClipTargetId first = new ClipTargetId (1);
        final ClipTargetId second = new ClipTargetId (2);
        final FakeCoreHost host = host (new ClipCatalogSnapshot (1, List.of (
            new CatalogClip (first, "fill one"),
            new CatalogClip (second, "fill two"))));
        host.start (Optional.empty ());
        host.armedClipTargets (host.effects ().desiredClipBindings ());

        host.button (CoreControls.DRUM_FILL_1, true);
        host.activeClipLaunchOwner (Optional.of (CoreControls.DRUM_FILL_1));
        host.button (CoreControls.DRUM_FILL_2, true);
        host.activeClipLaunchOwner (Optional.of (CoreControls.DRUM_FILL_2));
        host.button (CoreControls.DRUM_FILL_1, false);

        final ClipTargetId replacement = new ClipTargetId (3);
        host.clipCatalog (new ClipCatalogSnapshot (2, List.of (
            new CatalogClip (replacement, "replacement fill"),
            new CatalogClip (first, "fill one"),
            new CatalogClip (second, "fill two"))));
        assertEquals (Map.of (
            CoreControls.DRUM_FILL_1, replacement,
            CoreControls.DRUM_FILL_2, second), host.effects ().desiredClipBindings ());
        host.armedClipTargets (Map.of (CoreControls.DRUM_FILL_1, replacement));
        assertEquals (AVAILABLE, light (host, CoreControls.DRUM_FILL_1));
        assertEquals (HELD, light (host, CoreControls.DRUM_FILL_2));
    }


    @Test
    void aNewDownPressesAgainWhenTheOwnerIsAlreadyInTheShellSession ()
    {
        final ClipTargetId target = new ClipTargetId (1);
        final ClipCatalogSnapshot catalog = new ClipCatalogSnapshot (2, List.of (new CatalogClip (target, "fill one")));
        final Map<ControlId, ClipTargetId> armed = Map.of (CoreControls.DRUM_FILL_1, target);
        final PullCoreProvider provider = new PullCoreProvider ();
        final FakeCoreHost host = new FakeCoreHost (
            provider.create (),
            provider.descriptor ().requiredCapabilities (),
            catalog,
            armed,
            armed,
            Optional.of (CoreControls.DRUM_FILL_1),
            Set.of ());
        host.start (Optional.empty ());

        host.button (CoreControls.DRUM_FILL_1, true);

        assertEquals (List.of (new PressClipTargetEffect (CoreControls.DRUM_FILL_1, 2, target, FILL_POLICY)), host.effects ().executionOrder ());
    }


    @Test
    void catalogChangesCannotRedirectAHeldLease ()
    {
        final ClipTargetId original = new ClipTargetId (1);
        final ClipTargetId second = new ClipTargetId (2);
        final FakeCoreHost host = host (new ClipCatalogSnapshot (1, List.of (
            new CatalogClip (original, "fill one"),
            new CatalogClip (second, "fill two"))));
        host.start (Optional.empty ());
        host.armedClipTargets (host.effects ().desiredClipBindings ());
        host.button (CoreControls.DRUM_FILL_1, true);
        host.activeClipLaunchOwner (Optional.of (CoreControls.DRUM_FILL_1));

        final ClipTargetId inserted = new ClipTargetId (3);
        host.clipCatalog (new ClipCatalogSnapshot (2, List.of (
            new CatalogClip (inserted, "new fill"),
            new CatalogClip (original, "fill one"),
            new CatalogClip (second, "fill two"))));

        final Map<ControlId, ClipTargetId> heldBindings = host.effects ().desiredClipBindings ();
        assertEquals (original, heldBindings.get (CoreControls.DRUM_FILL_1));
        assertEquals (1, heldBindings.values ().stream ().filter (original::equals).count ());
        assertEquals (original, host.effects ().clipLease (CoreControls.DRUM_FILL_1).orElseThrow ().target ());
        assertEquals (1, host.effects ().executionOrder ().size ());
        assertEquals (HELD, light (host, CoreControls.DRUM_FILL_1));

        host.button (CoreControls.DRUM_FILL_1, false);

        assertEquals (original, host.effects ().desiredClipBindings ().get (CoreControls.DRUM_FILL_1));
        assertInstanceOf (ReleaseClipTargetsEffect.class, host.effects ().executionOrder ().getLast ());

        host.activeClipLaunchOwner (Optional.empty ());
        assertEquals (inserted, host.effects ().desiredClipBindings ().get (CoreControls.DRUM_FILL_1));
        assertEquals (original, host.effects ().desiredClipBindings ().get (CoreControls.DRUM_FILL_2));
    }


    @Test
    void padPressedBeforeArmingNeverLaunchesLater ()
    {
        final ClipTargetId desired = new ClipTargetId (1);
        final ClipTargetId stale = new ClipTargetId (2);
        final ClipCatalogSnapshot catalog = new ClipCatalogSnapshot (3, List.of (new CatalogClip (desired, "fill")));
        final PullCoreProvider provider = new PullCoreProvider ();
        final FakeCoreHost host = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities (), catalog, Map.of (CoreControls.DRUM_FILL_1, stale), Set.of ());
        host.start (Optional.empty ());

        assertEquals (OFF, light (host, CoreControls.DRUM_FILL_1));
        host.button (CoreControls.DRUM_FILL_1, true);

        assertEquals (desired, host.effects ().desiredClipBindings ().get (CoreControls.DRUM_FILL_1));
        assertEquals (OFF, light (host, CoreControls.DRUM_FILL_1));
        assertTrue (host.effects ().executionOrder ().isEmpty ());
        assertTrue (host.effects ().clipLease (CoreControls.DRUM_FILL_1).isEmpty ());

        host.armedClipTargets (Map.of (CoreControls.DRUM_FILL_1, desired));
        assertEquals (AVAILABLE, light (host, CoreControls.DRUM_FILL_1));
        assertTrue (host.effects ().executionOrder ().isEmpty ());
        assertTrue (host.effects ().clipLease (CoreControls.DRUM_FILL_1).isEmpty ());

        host.button (CoreControls.DRUM_FILL_1, false);
        assertInstanceOf (ReleaseClipTargetsEffect.class, host.effects ().executionOrder ().getLast ());
    }


    @Test
    void unrelatedInputsDoNotAcquireOrReleaseFillLeases ()
    {
        final FakeCoreHost host = host (new ClipCatalogSnapshot (1, List.of (clip (1, "fill"))));
        host.start (Optional.empty ());
        host.armedClipTargets (host.effects ().desiredClipBindings ());

        host.button (new ControlId ("other.button"), true);
        host.touch (new ControlId ("other.touch"), true);

        assertTrue (host.effects ().executionOrder ().isEmpty ());
        assertEquals (AVAILABLE, light (host, CoreControls.DRUM_FILL_1));
    }


    @Test
    void ownsPlainRecordAndRequestsSelectedTrackState ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());

        host.start (Optional.empty ());

        assertEquals (Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.TRANSPORT), host.effects ().desiredBridgeSubscriptions ().domains ());
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (RECORD_BUTTON, InputKind.BUTTON));
        host.selectedTrack (selectedTrack (false));
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (RECORD_BUTTON, InputKind.BUTTON));
        assertEquals (Optional.of (InputRouteMode.OBSERVE), host.effects ().desiredInputRoutes ().mode (SHIFT_BUTTON, InputKind.BUTTON));
        assertEquals (Optional.of (InputRouteMode.OBSERVE), host.effects ().desiredInputRoutes ().mode (SELECT_BUTTON, InputKind.BUTTON));
    }


    @Test
    void recordReleaseRequestsInverseOfAuthoritativeSelectedTrackArm ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.selectedTrack (selectedTrack (false));

        host.controllerButton (RECORD_BUTTON, true);
        assertTrue (host.effects ().executionOrder ().isEmpty ());
        host.controllerButton (RECORD_BUTTON, false);

        assertEquals (new SetSelectedTrackBooleanEffect (7, "track-7", SelectedTrackBoolean.RECORD_ARMED, true), host.effects ().executionOrder ().getLast ());

        host.selectedTrack (selectedTrack (true));
        host.controllerButton (RECORD_BUTTON, true);
        host.controllerButton (RECORD_BUTTON, false);

        assertEquals (new SetSelectedTrackBooleanEffect (7, "track-7", SelectedTrackBoolean.RECORD_ARMED, false), host.effects ().executionOrder ().getLast ());
    }


    @Test
    void shiftRecordTogglesLauncherOverdubWithoutChangingOwnership ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.selectedTrack (selectedTrack (false));
        host.transport (transport (false));

        host.controllerButton (SHIFT_BUTTON, true);
        host.controllerButton (RECORD_BUTTON, true);
        host.controllerButton (RECORD_BUTTON, false);

        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (RECORD_BUTTON, InputKind.BUTTON));
        assertEquals (new SetTransportStateEffect (TransportState.LAUNCHER_OVERDUB, true), host.effects ().executionOrder ().getLast ());
    }


    @Test
    void selectRecordCreatesANewClipWithoutChangingOwnership ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.selectedTrack (selectedTrack (false));

        host.controllerButton (SELECT_BUTTON, true);
        host.controllerButton (RECORD_BUTTON, true);
        host.controllerButton (RECORD_BUTTON, false);

        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (RECORD_BUTTON, InputKind.BUTTON));
        assertEquals (new SelectedTrackActionEffect (7, "track-7", SelectedTrackAction.CREATE_NEW_CLIP), host.effects ().executionOrder ().getLast ());
    }


    @Test
    void shiftSessionSelectsVsLiveAndPlainSessionReturnsToTheStableWorkspace ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());

        assertEquals (DesiredControllerWorkspace.empty (), host.effects ().desiredControllerWorkspace ());

        host.controllerButton (SHIFT_BUTTON, true);
        host.controllerButton (SESSION_BUTTON, true);
        host.controllerButton (SESSION_BUTTON, false);
        host.controllerButton (SHIFT_BUTTON, false);

        assertVsLive (host.effects ().desiredControllerWorkspace ());

        host.controllerButton (SESSION_BUTTON, true);

        assertEquals (DesiredControllerWorkspace.empty (), host.effects ().desiredControllerWorkspace ());
    }


    @Test
    void vsLiveCanExitAndReenterWithItsCompleteOwnershipRestored ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());

        enterVsLive (host);
        host.controllerButton (SESSION_BUTTON, true);
        assertEquals (DesiredControllerWorkspace.empty (), host.effects ().desiredControllerWorkspace ());

        host.controllerButton (SESSION_BUTTON, false);
        enterVsLive (host);

        assertVsLive (host.effects ().desiredControllerWorkspace ());
        assertEquals (Optional.of (InputRouteMode.OBSERVE), host.effects ().desiredInputRoutes ().mode (PushControlIds.pad (10), InputKind.POLY_PRESSURE));
        assertEquals (Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.TRANSPORT, BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.PARAMETERS), host.effects ().desiredBridgeSubscriptions ().domains ());
        assertEquals (Set.of (ParameterBankId.PROJECT_REMOTE, ParameterBankId.GLOBAL), host.effects ().desiredParameterBanks ().banks ());
    }


    @Test
    void checkpointRestoresVsLiveWithoutReplayingTheShortcut ()
    {
        final FakeCoreHost first = host (ClipCatalogSnapshot.empty ());
        first.start (Optional.empty ());
        first.controllerButton (SHIFT_BUTTON, true);
        first.controllerButton (SESSION_BUTTON, true);

        final PullCoreProvider provider = new PullCoreProvider ();
        final FakeCoreHost restored = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities ());
        restored.start (Optional.of (first.checkpoint ()));

        assertVsLive (restored.effects ().desiredControllerWorkspace ());
    }


    @Test
    void noteExitsVsLive ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.controllerButton (SHIFT_BUTTON, true);
        host.controllerButton (SESSION_BUTTON, true);

        host.controllerButton (NOTE_BUTTON, true);

        assertEquals (DesiredControllerWorkspace.empty (), host.effects ().desiredControllerWorkspace ());
    }


    @Test
    void vsLiveMapsPlayablePadPressureInTheReloadableCore ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.bridge (bridgeWithPressure (GridPressureConfiguration.POLY, 48));

        host.controllerMotion (PushControlIds.pad (10), InputKind.POLY_PRESSURE, 91);
        assertTrue (host.effects ().executionOrder ().isEmpty ());

        enterVsLive (host);
        assertEquals (Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.TRANSPORT, BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.PARAMETERS), host.effects ().desiredBridgeSubscriptions ().domains ());
        assertEquals (Set.of (ParameterBankId.PROJECT_REMOTE, ParameterBankId.GLOBAL), host.effects ().desiredParameterBanks ().banks ());
        host.controllerMotion (PushControlIds.pad (10), InputKind.POLY_PRESSURE, 91);

        assertEquals (new SendNoteInputMidiEffect (0xA0, 53, 91), host.effects ().executionOrder ().getLast ());
        assertEquals (Optional.of (InputRouteMode.OBSERVE), host.effects ().desiredInputRoutes ().mode (PushControlIds.pad (10), InputKind.POLY_PRESSURE));
        assertEquals (Optional.of (InputRouteMode.OBSERVE), host.effects ().desiredInputRoutes ().mode (PushControlIds.pad (10), InputKind.PAD));

        host.controllerButton (SESSION_BUTTON, true);
        assertEquals (Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.TRANSPORT), host.effects ().desiredBridgeSubscriptions ().domains ());
        assertEquals (Optional.empty (), host.effects ().desiredInputRoutes ().mode (PushControlIds.pad (10), InputKind.POLY_PRESSURE));
    }


    @Test
    void vsLiveHonorsSharedPressureModesAndIgnoresNonPlayablePads ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        enterVsLive (host);

        host.bridge (bridgeWithPressure (GridPressureConfiguration.CHANNEL, 36));
        host.controllerMotion (PushControlIds.pad (1), InputKind.POLY_PRESSURE, 80);
        assertEquals (new SendNoteInputMidiEffect (0xD0, 80, 0), host.effects ().executionOrder ().getLast ());

        host.bridge (bridgeWithPressure (GridPressureConfiguration.controlChange (74), 36));
        host.controllerMotion (PushControlIds.pad (1), InputKind.POLY_PRESSURE, 64);
        assertEquals (new SendNoteInputMidiEffect (0xB0, 74, 64), host.effects ().executionOrder ().getLast ());

        final int effectCount = host.effects ().executionOrder ().size ();
        host.controllerMotion (PushControlIds.pad (5), InputKind.POLY_PRESSURE, 64);
        assertEquals (effectCount, host.effects ().executionOrder ().size ());
    }


    private static CatalogClip clip (final long target, final String name)
    {
        return new CatalogClip (new ClipTargetId (target), name);
    }


    private static SelectedTrackSnapshot selectedTrack (final boolean recordArmed)
    {
        return new SelectedTrackSnapshot (
            7,
            "track-7",
            "Drums",
            3,
            "INSTRUMENT",
            true,
            false,
            false,
            true,
            false,
            true,
            recordArmed,
            TrackMonitorMode.AUTO,
            false,
            false,
            false,
            true,
            0.75,
            0.5,
            new RgbColor (100, 50, 25));
    }


    private static TransportSnapshot transport (final boolean launcherOverdub)
    {
        return new TransportSnapshot (true, true, false, false, launcherOverdub, false, false, false, 120, 0, 4, 4);
    }


    private static ControllerBridgeSnapshot bridgeWithPressure (final GridPressureConfiguration pressure, final int drumBaseMidiNote)
    {
        return new ControllerBridgeSnapshot (
            TransportSnapshot.empty (),
            SelectedTrackSnapshot.empty (),
            new ControllerLayoutSnapshot ("WORKSPACE", "PROJECT", true, true, drumBaseMidiNote, pressure),
            de.mossgrabers.pull.core.api.DrumContextSnapshot.empty (),
            de.mossgrabers.pull.core.api.ParameterBridgeSnapshot.empty ());
    }


    private static void enterVsLive (final FakeCoreHost host)
    {
        host.controllerButton (SHIFT_BUTTON, true);
        host.controllerButton (SESSION_BUTTON, true);
        host.controllerButton (SESSION_BUTTON, false);
        host.controllerButton (SHIFT_BUTTON, false);
    }


    private static void assertVsLive (final DesiredControllerWorkspace workspace)
    {
        assertEquals (VsLiveWorkspace.NAME, workspace.name ());
        assertEquals (VsLiveWorkspace.SESSION_BANK, workspace.sessionBankShape ());
        assertEquals (Set.of (
            ControllerViewFacet.PROJECT_MACRO_CONTROLS,
            ControllerViewFacet.TRACK_SELECTION_STRIP,
            ControllerViewFacet.SESSION_NAVIGATION,
            ControllerViewFacet.SESSION_CLIP_GRID_UPPER,
            ControllerViewFacet.SESSION_SCENE_KEYS_UPPER,
            ControllerViewFacet.DRUM_CONTROLLER_LOWER,
            ControllerViewFacet.DRUM_PITCH_BEND), workspace.facets ());
    }


    private static FakeCoreHost host (final ClipCatalogSnapshot clips)
    {
        final PullCoreProvider provider = new PullCoreProvider ();
        final ShellCapabilities capabilities = provider.descriptor ().requiredCapabilities ();
        return new FakeCoreHost (provider.create (), capabilities, clips, Map.of (), Set.of ());
    }


    private static RgbColor light (final FakeCoreHost host, final ControlId control)
    {
        return host.effects ().desiredOutput ().lights ().get (control);
    }
}
