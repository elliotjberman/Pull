// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.CatalogClip;
import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerActionIntent;
import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerMappingBinding;
import de.mossgrabers.pull.core.api.ControllerMappingFeedbackSnapshot;
import de.mossgrabers.pull.core.api.ControllerNoteView;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.ControllerStateScope;
import de.mossgrabers.pull.core.api.CoreControllerMappings;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.DesiredNoteInputRoute;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.DrumContextSnapshot;
import de.mossgrabers.pull.core.api.DrumPadSnapshot;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.GridPressureConfiguration;
import de.mossgrabers.pull.core.api.MasterSnapshot;
import de.mossgrabers.pull.core.api.NoteRepeatMode;
import de.mossgrabers.pull.core.api.NoteRepeatSnapshot;
import de.mossgrabers.pull.core.api.NoteViewSnapshot;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetKind;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.ProjectSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.api.SessionBankSnapshot;
import de.mossgrabers.pull.core.api.SessionTrackSnapshot;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.TrackMonitorMode;
import de.mossgrabers.pull.core.api.TransportSnapshot;
import de.mossgrabers.pull.core.api.effect.ClipLaunchMode;
import de.mossgrabers.pull.core.api.effect.ClipLaunchPolicy;
import de.mossgrabers.pull.core.api.effect.ClipLaunchQuantization;
import de.mossgrabers.pull.core.api.effect.ClipReleaseTrigger;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.ConsumeControllerButtonEffect;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.NavigateProjectEffect;
import de.mossgrabers.pull.core.api.effect.ProjectFileAction;
import de.mossgrabers.pull.core.api.effect.ProjectFileActionEffect;
import de.mossgrabers.pull.core.api.effect.ProjectNavigationDirection;
import de.mossgrabers.pull.core.api.effect.ScheduleTimerEffect;
import de.mossgrabers.pull.core.api.effect.SetProjectEngineEffect;
import de.mossgrabers.pull.core.api.effect.SetProjectTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.SetNoteViewPreferenceEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackAction;
import de.mossgrabers.pull.core.api.effect.SelectedTrackActionEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackBoolean;
import de.mossgrabers.pull.core.api.effect.SelectSessionTrackEffect;
import de.mossgrabers.pull.core.api.effect.StopSessionBankEffect;
import de.mossgrabers.pull.core.api.effect.SendNoteInputMidiEffect;
import de.mossgrabers.pull.core.api.effect.SetSelectedTrackBooleanEffect;
import de.mossgrabers.pull.core.api.effect.SetTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.TransportState;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.api.output.PadGridPosition;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;
import de.mossgrabers.pull.core.runtime.view.StableDestinationWorkspace;
import de.mossgrabers.pull.core.runtime.view.VsLiveWorkspace;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);
    private static final RgbColor GREEN = new RgbColor (0, 255, 0);
    private static final RgbColor RED = new RgbColor (255, 0, 0);
    private static final RgbColor AMBER = new RgbColor (255, 191, 0);
    private static final RgbColor PURPLE = new RgbColor (128, 0, 255);
    private static final RgbColor WAVE_PURPLE = new RgbColor (160, 48, 255);
    private static final RgbColor AVAILABLE = new RgbColor (167, 107, 34);
    private static final RgbColor HELD = new RgbColor (242, 126, 0);
    private static final ControlId PLAY_BUTTON = PushControlIds.button ("PLAY");
    private static final ControlId RECORD_BUTTON = PushControlIds.button ("RECORD");
    private static final ControlId SESSION_BUTTON = PushControlIds.button ("SESSION");
    private static final ControlId NOTE_BUTTON = PushControlIds.button ("NOTE");
    private static final ControlId LAYOUT_BUTTON = PushControlIds.button ("LAYOUT");
    private static final ControlId SHIFT_BUTTON = PushControlIds.button ("SHIFT");
    private static final ControlId SELECT_BUTTON = PushControlIds.button ("SELECT");
    private static final ControlId STOP_CLIP_BUTTON = PushControlIds.button ("STOP_CLIP");
    private static final ControlId MUTE_BUTTON = PushControlIds.button ("MUTE");
    private static final ControlId SOLO_BUTTON = PushControlIds.button ("SOLO");
    private static final List<ControlId> FILL_LIGHTS = List.of (
        PushControlIds.pad (13),
        PushControlIds.pad (14),
        PushControlIds.pad (15),
        PushControlIds.pad (16),
        PushControlIds.pad (21),
        PushControlIds.pad (22),
        PushControlIds.pad (23),
        PushControlIds.pad (24));
    private static final ClipLaunchPolicy FILL_POLICY = new ClipLaunchPolicy (
        ClipLaunchQuantization.IMMEDIATE,
        ClipLaunchMode.LEGATO_FROM_CLIP_OR_PROJECT,
        ClipReleaseTrigger.ALTERNATE);


    @Test
    void bindsTheFirstEightCaseInsensitiveFillsInCatalogOrder ()
    {
        final List<CatalogClip> clips = new ArrayList<> ();
        clips.add (clip (100, "verse"));
        for (int index = 0; index < 14; index++)
            clips.add (clip (index, index % 2 == 0 ? "Fill " + index : "prefilled " + index));
        final FakeCoreHost host = host (new ClipCatalogSnapshot (5, clips));

        startFillCore (host);

        final Map<ControlId, ClipTargetId> bindings = host.effects ().desiredClipBindings ();
        assertEquals (8, bindings.size ());
        for (int index = 0; index < 8; index++)
            assertEquals (new ClipTargetId (index), bindings.get (CoreControls.DRUM_FILLS.get (index)));
        assertFalse (bindings.containsValue (new ClipTargetId (8)));
        assertTrue (host.effects ().desiredOutput ().lights ().keySet ().containsAll (FILL_LIGHTS));
        assertTrue (host.effects ().desiredOutput ().lights ().keySet ().stream ().noneMatch (CoreControls.DRUM_FILLS::contains));
        assertTrue (host.effects ().desiredOutput ().lights ().keySet ().containsAll (Set.of (PLAY_BUTTON, RECORD_BUTTON, MUTE_BUTTON, SOLO_BUTTON)));
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
        startFillCore (host);
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
        startFillCore (host);
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
        startFillCore (host);

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

        startFillCore (host);

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
        startFillCore (host);

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

        startFillCore (host);

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
        startFillCore (host);
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
        startFillCore (host);

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
        startFillCore (host);
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
        startFillCore (host);

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
        startFillCore (host);
        host.armedClipTargets (host.effects ().desiredClipBindings ());

        host.button (new ControlId ("other.button"), true);
        host.touch (new ControlId ("other.touch"), true);

        assertTrue (host.effects ().executionOrder ().isEmpty ());
        assertEquals (AVAILABLE, light (host, CoreControls.DRUM_FILL_1));
    }


    @Test
    void melodicNoteRouteRetainsAllPadsWithoutClaimingThePhysicalFillLights ()
    {
        final FakeCoreHost host = host (new ClipCatalogSnapshot (1, List.of (clip (1, "fill"))));
        final SelectedTrackSnapshot selected = selectedTrack (8, "drums", 5, true, false);
        final NoteViewSnapshot preference = new NoteViewSnapshot (8, "drums", 5, ControllerNoteView.PLAY, false);
        host.initialBridge (noteBridge (1, "PLAY", selected, preference, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));
        host.start (Optional.empty ());

        assertEquals (DesiredNoteInputRoute.selectedTrack (8, "drums"), host.effects ().desiredNoteInputRoute ());
        assertTrue (Collections.disjoint (FILL_LIGHTS, host.effects ().desiredOutput ().lights ().keySet ()));
        assertTrue (Collections.disjoint (CoreControls.DRUM_FILLS, host.effects ().desiredOutput ().lights ().keySet ()));
        assertTrue (host.effects ().desiredClipBindings ().isEmpty ());
        host.button (CoreControls.DRUM_FILL_1, true);
        host.button (CoreControls.DRUM_FILL_1, false);
        assertTrue (host.effects ().executionOrder ().isEmpty ());

        host.bridge (fillLayoutBridge (2, true));
        assertTrue (host.effects ().desiredOutput ().lights ().keySet ().containsAll (FILL_LIGHTS));
        assertTrue (Collections.disjoint (CoreControls.DRUM_FILLS, host.effects ().desiredOutput ().lights ().keySet ()));
        assertEquals (new ClipTargetId (1), host.effects ().desiredClipBindings ().get (CoreControls.DRUM_FILL_1));

        host.bridge (noteBridge (3, "PLAY", selected, preference, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));
        assertEquals (DesiredNoteInputRoute.selectedTrack (8, "drums"), host.effects ().desiredNoteInputRoute ());
        assertTrue (Collections.disjoint (FILL_LIGHTS, host.effects ().desiredOutput ().lights ().keySet ()));
        assertTrue (host.effects ().desiredClipBindings ().isEmpty ());
    }


    @Test
    void ownsPlainRecordAndRequestsSelectedTrackState ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());

        host.start (Optional.empty ());

        assertEquals (Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.TRANSPORT, BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.NOTE_VIEW, BridgeSubscription.PROJECT), host.effects ().desiredBridgeSubscriptions ().domains ());
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (PLAY_BUTTON, InputKind.BUTTON));
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (RECORD_BUTTON, InputKind.BUTTON));
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (NOTE_BUTTON, InputKind.BUTTON));
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (SESSION_BUTTON, InputKind.BUTTON));
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (LAYOUT_BUTTON, InputKind.BUTTON));
        host.selectedTrack (selectedTrack (false));
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (RECORD_BUTTON, InputKind.BUTTON));
        assertEquals (Optional.of (InputRouteMode.OBSERVE), host.effects ().desiredInputRoutes ().mode (SHIFT_BUTTON, InputKind.BUTTON));
        assertEquals (Optional.of (InputRouteMode.OBSERVE), host.effects ().desiredInputRoutes ().mode (SELECT_BUTTON, InputKind.BUTTON));
    }


    @Test
    void transportLightsRenderOnlyAuthoritativeEngineTransportAndArmReadback ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());

        assertEquals (OFF, light (host, PLAY_BUTTON));
        assertEquals (OFF, light (host, RECORD_BUTTON));

        host.bridge (projectBridge ("project-a", true, false, true, true, false, selectedTrack (false)));
        assertEquals (WHITE, light (host, PLAY_BUTTON));
        assertEquals (WHITE, light (host, RECORD_BUTTON));

        host.bridge (projectBridge ("project-a", true, true, true, true, false, selectedTrack (true)));
        assertEquals (GREEN, light (host, PLAY_BUTTON));
        assertEquals (RED, light (host, RECORD_BUTTON));

        host.controllerButton (SHIFT_BUTTON, true);
        assertEquals (WHITE, light (host, RECORD_BUTTON));
        host.bridge (projectBridge ("project-a", true, true, true, true, false, selectedTrack (true), true));
        assertEquals (AMBER, light (host, RECORD_BUTTON));

        host.bridge (projectBridge ("project-a", false, true, true, true, false, selectedTrack (true), true));
        assertEquals (OFF, light (host, PLAY_BUTTON));
        assertEquals (OFF, light (host, RECORD_BUTTON));
    }


    @Test
    void remotePlaySubmitsOneStableOwnedProjectOperationImmediately ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.bridge (projectBridge ("project-a", true, true, true, true, false, selectedTrack (false)));
        assertEquals (GREEN, light (host, PLAY_BUTTON));

        host.bridge (projectBridge ("project-b", false, false, true, true, false, selectedTrack (false)));
        assertEquals (PURPLE, light (host, PLAY_BUTTON));

        host.controllerButton (PLAY_BUTTON, true);
        assertEquals (new SetProjectTransportStateEffect ("project-b", "project-a", TransportState.PLAYING, false), host.effects ().executionOrder ().getLast ());
        final int playPressEffectCount = host.effects ().executionOrder ().size ();
        host.controllerButton (PLAY_BUTTON, false);
        assertEquals (playPressEffectCount, host.effects ().executionOrder ().size ());
        assertEquals (PURPLE, light (host, PLAY_BUTTON));

        host.bridge (projectBridge ("project-a", true, true, true, true, true, selectedTrack (false)));
        assertEquals (playPressEffectCount, host.effects ().executionOrder ().size ());
        assertEquals (GREEN, light (host, PLAY_BUTTON));

        host.bridge (projectBridge ("project-a", true, false, true, true, false, selectedTrack (false)));
        host.bridge (projectBridge ("project-b", false, false, true, true, true, selectedTrack (false)));
        host.bridge (projectBridge ("project-b", false, false, true, true, false, selectedTrack (false)));
        assertEquals (playPressEffectCount, host.effects ().executionOrder ().size ());
        assertEquals (WHITE, light (host, PLAY_BUTTON));
    }


    @Test
    void remoteStopMasksTheGridWithAFastRoundedWhiteWavefront ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.bridge (projectBridge ("project-a", true, true, true, true, false, selectedTrack (false)));
        host.bridge (projectBridge ("project-b", false, false, true, true, false, selectedTrack (false)));

        host.controllerButton (PLAY_BUTTON, true);
        assertTrue (host.effects ().executionRequirements ().ticksRequested ());
        assertFalse (host.effects ().executionOrder ().stream ().anyMatch (ScheduleTimerEffect.class::isInstance));
        ControllerPadGridOverlay overlay = host.effects ().desiredOutput ().padGridOverlay ();
        assertTrue (overlay.active ());
        assertEquals (64, overlay.colors ().size ());
        assertEquals (Map.of (new PadGridPosition (0, 0), WHITE), nonBlackPads (overlay));
        assertTrue (host.effects ().desiredOutput ().displayOverlay ().active ());
        assertTrue (host.effects ().desiredOutput ().displayOverlay ().scene ().commands ().contains (new DisplayCommand.Rectangle (0, 0, 960, 160, OFF)));
        assertTrue (host.effects ().desiredOutput ().displayOverlay ().scene ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.Rectangle rectangle && rectangle.x () > 0 && rectangle.color ().red () == rectangle.color ().green () && rectangle.color ().green () == rectangle.color ().blue () && rectangle.color ().blue () > 0));

        for (int frame = 1; frame < 10; frame++)
        {
            host.advance (Duration.ofNanos (25_000_000L));
            host.controllerTick ();
            overlay = host.effects ().desiredOutput ().padGridOverlay ();
            assertTrue (overlay.active ());
            assertEquals (64, overlay.colors ().size ());
            final Map<PadGridPosition, RgbColor> wave = nonBlackPads (overlay);
            assertFalse (wave.isEmpty ());
            if (frame == 3)
            {
                assertTrue (wave.containsKey (new PadGridPosition (0, 3)));
                assertTrue (wave.containsKey (new PadGridPosition (3, 0)));
                assertFalse (wave.containsKey (new PadGridPosition (3, 3)));
                assertTrue (wave.get (new PadGridPosition (0, 3)).blue () > wave.get (new PadGridPosition (0, 2)).blue ());
            }
        }

        host.advance (Duration.ofNanos (25_000_000L));
        host.controllerTick ();
        assertFalse (host.effects ().desiredOutput ().padGridOverlay ().active ());
        assertFalse (host.effects ().desiredOutput ().displayOverlay ().active ());

        assertFalse (host.effects ().desiredOutput ().padGridOverlay ().active ());
        assertFalse (host.effects ().executionRequirements ().ticksRequested ());
    }


    @Test
    void remoteAnimationStartsWithTheRequestAndDoesNotDelayTheHostOperation ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.bridge (projectBridge ("project-a", true, false, true, true, false, selectedTrack (false)));
        host.bridge (projectBridge ("project-b", false, false, true, true, false, selectedTrack (false)));

        host.controllerButton (PLAY_BUTTON, true);
        assertEquals (new SetProjectTransportStateEffect ("project-b", "project-a", TransportState.PLAYING, true), host.effects ().executionOrder ().getLast ());
        assertEquals (Map.of (new PadGridPosition (0, 0), WAVE_PURPLE), nonBlackPads (host.effects ().desiredOutput ().padGridOverlay ()));
        final int requestEffectCount = host.effects ().executionOrder ().size ();
        host.bridge (projectBridge ("project-b", false, false, true, true, true, selectedTrack (false)));
        host.advance (Duration.ofNanos (300_000_000L));
        host.controllerTick ();
        assertEquals (requestEffectCount, host.effects ().executionOrder ().size ());
        assertFalse (host.effects ().desiredOutput ().padGridOverlay ().active ());
        assertFalse (host.effects ().desiredOutput ().displayOverlay ().active ());

        host.bridge (projectBridge ("project-a", true, false, true, true, true, selectedTrack (false)));
        host.bridge (projectBridge ("project-a", true, false, true, true, false, selectedTrack (false)));
        assertEquals (requestEffectCount, host.effects ().executionOrder ().size ());
    }


    @Test
    void localPlayNeverStartsTheProjectTransitionOverlay ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.bridge (projectBridge ("project-a", true, false, true, true, false, selectedTrack (false)));

        host.controllerButton (PLAY_BUTTON, true);

        assertEquals (new SetProjectTransportStateEffect ("project-a", "project-a", TransportState.PLAYING, true), host.effects ().executionOrder ().getLast ());
        assertFalse (host.effects ().desiredOutput ().padGridOverlay ().active ());
    }


    @Test
    void remotePausedEngineOwnerUsesWhitePlayFeedback ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.bridge (projectBridge ("project-a", true, false, true, true, false, selectedTrack (false)));
        host.bridge (projectBridge ("project-b", false, false, true, true, false, selectedTrack (false)));

        assertEquals (WHITE, light (host, PLAY_BUTTON));
        assertEquals (OFF, light (host, RECORD_BUTTON));

        host.controllerButton (PLAY_BUTTON, true);
        assertEquals (new SetProjectTransportStateEffect ("project-b", "project-a", TransportState.PLAYING, true), host.effects ().executionOrder ().getLast ());
        assertEquals (new RgbColor (160, 48, 255), nonBlackPads (host.effects ().desiredOutput ().padGridOverlay ()).get (new PadGridPosition (0, 0)));
        assertTrue (host.effects ().desiredOutput ().displayOverlay ().scene ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.Rectangle rectangle && rectangle.x () > 0 && rectangle.color ().blue () > rectangle.color ().red () && rectangle.color ().red () > rectangle.color ().green ()));
    }


    @Test
    void aLongStableOwnedCommandCannotCaptureCoreAnimationOrInput ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.bridge (projectBridge ("project-a", true, true, true, true, false, selectedTrack (false)));
        host.bridge (projectBridge ("project-b", false, false, true, true, false, selectedTrack (false)));

        host.controllerButton (PLAY_BUTTON, true);
        final int effectCount = host.effects ().executionOrder ().size ();
        host.bridge (projectBridge ("project-b", false, false, true, true, true, selectedTrack (false)));

        host.advance (Duration.ofMillis (250));
        host.controllerTick ();
        assertFalse (host.effects ().executionRequirements ().ticksRequested ());
        assertFalse (host.effects ().desiredOutput ().padGridOverlay ().active ());
        assertFalse (host.effects ().desiredOutput ().displayOverlay ().active ());
        assertEquals (PURPLE, light (host, PLAY_BUTTON));

        assertEquals (effectCount, host.effects ().executionOrder ().size ());
    }


    @Test
    void masterModeIsACompleteReloadableWorkspaceWithAuthoritativeNavigation ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.bridge (masterBridge (true, true, false));

        assertEquals (Set.of (ControllerViewFacet.MASTER_CONTROLS), host.effects ().desiredControllerWorkspace ().facets ());
        assertEquals (Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.TRANSPORT, BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.NOTE_VIEW, BridgeSubscription.MASTER, BridgeSubscription.PARAMETERS, BridgeSubscription.PROJECT), host.effects ().desiredBridgeSubscriptions ().domains ());
        assertEquals (Set.of (ParameterBankId.MASTER, ParameterBankId.GLOBAL), host.effects ().desiredParameterBanks ().banks ());
        assertTrue (host.effects ().desiredOutput ().display ().isPresent ());
        assertEquals (960, host.effects ().desiredOutput ().display ().width ());
        for (final String label: List.of ("Volume", "Pan", "Cue Volume", "Cue Mix"))
            assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && label.equals (text.text ())));
        for (final String label: List.of ("Audio Engine", "Project"))
            assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextAt text && label.equals (text.text ())));
        assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && "8".equals (text.text ())));
        assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextAt text && "L".equals (text.text ())));
        assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && "Previous".equals (text.text ())));
        assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && "Next".equals (text.text ())));
        assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && "second_test".equals (text.text ())));
        assertFalse (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextAt text && "st".equals (text.text ())));
        assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.RoundedRectangle rectangle && new RgbColor (55, 185, 64).equals (rectangle.color ())));

        final ControlId previous = PushControlIds.button ("ROW2_7");
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (previous, InputKind.BUTTON));
        host.controllerButton (previous, true);
        assertEquals (new NavigateProjectEffect ("project-a", ProjectNavigationDirection.PREVIOUS), host.effects ().executionOrder ().getLast ());

        final ControlId engine = PushControlIds.button ("ROW2_5");
        host.controllerButton (engine, true);
        assertEquals (new SetProjectEngineEffect ("project-a", false), host.effects ().executionOrder ().getLast ());

        host.bridge (masterBridge (true, true, false, false));
        assertEquals (new RgbColor (255, 255, 255), host.effects ().desiredOutput ().lights ().get (engine));

        final ControlId open = PushControlIds.button ("ROW1_7");
        host.controllerButton (open, true);
        assertEquals (new ProjectFileActionEffect ("project-a", ProjectFileAction.OPEN), host.effects ().executionOrder ().getLast ());

        final ControlId save = PushControlIds.button ("ROW1_8");
        host.controllerButton (save, true);
        assertEquals (new ProjectFileActionEffect ("project-a", ProjectFileAction.SAVE), host.effects ().executionOrder ().getLast ());

        final ParameterTargetRef cueMixTarget = parameterTarget (ParameterSlot.CUE_MIX);
        host.controllerMotion (PushControlIds.continuous ("KNOB4"), InputKind.RELATIVE, 2);
        assertEquals (new AdjustParameterValueEffect (cueMixTarget, 20), host.effects ().executionOrder ().getLast ());

        host.bridge (masterBridge (false, true, false));
        final int effectCount = host.effects ().executionOrder ().size ();
        assertEquals (new RgbColor (30, 30, 30), host.effects ().desiredOutput ().lights ().get (previous));
        assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && "Previous".equals (text.text ()) && new RgbColor (102, 102, 102).equals (text.color ())));
        host.controllerButton (previous, false);
        host.controllerButton (previous, true);
        assertEquals (effectCount, host.effects ().executionOrder ().size ());
    }


    @Test
    void shiftedMasterEncoderTurnRemainsRoutedToTheCore ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.bridge (masterBridge (true, true, false));

        assertEquals (Optional.of (InputRouteMode.OBSERVE), host.effects ().desiredInputRoutes ().mode (SHIFT_BUTTON, InputKind.BUTTON));

        host.controllerButton (SHIFT_BUTTON, true);
        final ParameterTargetRef cueMixTarget = parameterTarget (ParameterSlot.CUE_MIX);
        host.controllerMotion (PushControlIds.continuous ("KNOB4"), InputKind.RELATIVE, 2);

        assertEquals (new AdjustParameterValueEffect (cueMixTarget, 20), host.effects ().executionOrder ().getLast ());
    }


    @Test
    void shiftTempoInMasterRestoresOnlyAfterAuthoritativeReadback ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.bridge (masterBridge (true, true, false, true, 120));
        final ControlId tempo = PushControlIds.continuous ("TEMPO");
        final ParameterTargetSnapshot baseline = parameter (ParameterSlot.TEMPO, "Tempo", 120, "120.00 BPM");

        host.controllerButton (SHIFT_BUTTON, true);
        host.parameterMutation (tempo, baseline);
        assertEquals (Map.of (baseline.target (), 120.0), host.effects ().desiredParameterInteraction ().baselines ());

        host.bridge (masterBridge (true, true, false, true, 126));
        host.controllerButton (SHIFT_BUTTON, false);
        host.controllerTick ();
        host.controllerTick ();

        final SetParameterValueEffect restore = new SetParameterValueEffect (baseline.target (), 120);
        assertEquals (restore, host.effects ().executionOrder ().getLast ());
        final int submittedEffects = host.effects ().executionOrder ().size ();

        host.controllerTick ();
        host.controllerTick ();

        assertEquals (submittedEffects + 1, host.effects ().executionOrder ().size ());
        assertEquals (restore, host.effects ().executionOrder ().getLast ());
        assertEquals (Map.of (baseline.target (), 120.0), host.effects ().desiredParameterInteraction ().baselines ());

        host.bridge (masterBridge (true, true, false, true, 120));
        host.controllerTick ();
        host.controllerTick ();

        assertTrue (host.effects ().desiredParameterInteraction ().baselines ().isEmpty ());
    }


    @Test
    void shiftSessionLeavesMasterForVsLiveBeforeTheLayoutReadbackChanges ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.bridge (masterBridge (true, true, false));

        host.controllerButton (SHIFT_BUTTON, true);
        host.controllerButton (SESSION_BUTTON, true);

        assertVsLive (host.effects ().desiredControllerWorkspace ());

        host.bridge (bridgeWithPressure (GridPressureConfiguration.OFF, 0));
        host.bridge (masterBridge (true, true, false));

        assertMasterOverVsLive (host.effects ().desiredControllerWorkspace ());
    }


    @Test
    void plainSessionLeavesMasterForTheStableDefaultBeforeLayoutReadbackChanges ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.bridge (masterBridge (true, true, false));

        host.controllerButton (SESSION_BUTTON, true);

        assertStableSessionDestination (host.effects ().desiredControllerWorkspace ());

        host.bridge (layoutBridge (2, "SESSION", "MASTER"));
        assertStableSessionDestination (host.effects ().desiredControllerWorkspace ());

        host.bridge (layoutBridge (3, "SESSION", "TRACK"));
        assertStableSessionDestination (host.effects ().desiredControllerWorkspace ());
        host.controllerButton (SESSION_BUTTON, false);
        assertSelectedSession (host.effects ().desiredControllerWorkspace ());
    }


    @Test
    void startupSessionWithoutAPageReadbackRequestsItsDefaultTrackPage ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.initialBridge (sessionBridge (4, "SESSION", "", StableDestinationWorkspace.SESSION_BANK));

        host.start (Optional.empty ());

        assertStableSessionDestination (host.effects ().desiredControllerWorkspace ());
        assertEquals (Optional.of (InputRouteMode.OBSERVE), host.effects ().desiredInputRoutes ().mode (STOP_CLIP_BUTTON, InputKind.BUTTON));
    }


    @Test
    void startupSessionPreservesAnAlreadySelectedIndependentPage ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.initialBridge (sessionBridge (4, "SESSION", "DEVICE_PARAMS", StableDestinationWorkspace.SESSION_BANK));

        host.start (Optional.empty ());

        assertSelectedSession (host.effects ().desiredControllerWorkspace ());
        assertEquals (Optional.of (InputRouteMode.OBSERVE), host.effects ().desiredInputRoutes ().mode (STOP_CLIP_BUTTON, InputKind.BUTTON));
    }


    @Test
    void sessionOwnsStopClipActionAndAuthoritativeButtonFeedback ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.initialBridge (sessionBridge (4, "SESSION", "TRACK", StableDestinationWorkspace.SESSION_BANK));
        host.start (Optional.empty ());

        assertSelectedSession (host.effects ().desiredControllerWorkspace ());
        assertEquals (new RgbColor (25, 0, 0), light (host, STOP_CLIP_BUTTON));
        assertEquals (Optional.of (InputRouteMode.OBSERVE), host.effects ().desiredInputRoutes ().mode (STOP_CLIP_BUTTON, InputKind.BUTTON));

        host.controllerButton (STOP_CLIP_BUTTON, true);
        assertEquals (RED, light (host, STOP_CLIP_BUTTON));
        host.controllerButton (STOP_CLIP_BUTTON, false);

        assertEquals (new SelectedTrackActionEffect (7, "track-7", SelectedTrackAction.STOP_IMMEDIATELY), host.effects ().executionOrder ().getLast ());
        assertEquals (new RgbColor (25, 0, 0), light (host, STOP_CLIP_BUTTON));
    }


    @Test
    void shiftedSessionStopTargetsTheFencedVisibleBank ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.initialBridge (sessionBridge (4, "SESSION", "TRACK", StableDestinationWorkspace.SESSION_BANK));
        host.start (Optional.empty ());

        host.controllerButton (SHIFT_BUTTON, true);
        host.controllerButton (STOP_CLIP_BUTTON, true);
        host.controllerButton (STOP_CLIP_BUTTON, false);

        assertEquals (new StopSessionBankEffect (12, StableDestinationWorkspace.SESSION_BANK, true), host.effects ().executionOrder ().getLast ());
    }


    @Test
    void selectSessionStopConsumesTheDeferredSelectRelease ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.initialBridge (sessionBridge (4, "SESSION", "TRACK", StableDestinationWorkspace.SESSION_BANK));
        host.start (Optional.empty ());

        host.controllerButton (SELECT_BUTTON, true);
        host.controllerButton (STOP_CLIP_BUTTON, true);
        host.controllerButton (STOP_CLIP_BUTTON, false);

        assertEquals (List.of (
            new ConsumeControllerButtonEffect (SELECT_BUTTON),
            new StopSessionBankEffect (12, StableDestinationWorkspace.SESSION_BANK, true)), host.effects ().executionOrder ());
    }


    @Test
    void longSessionStopHasNoLegacyPageModifierAndStopsTheSelectionOnRelease ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.initialBridge (sessionBridge (4, "SESSION", "TRACK", StableDestinationWorkspace.SESSION_BANK));
        host.start (Optional.empty ());
        host.controllerButton (STOP_CLIP_BUTTON, true);
        host.controllerButtonLong (STOP_CLIP_BUTTON);
        host.controllerButton (STOP_CLIP_BUTTON, false);

        assertEquals (new SelectedTrackActionEffect (7, "track-7", SelectedTrackAction.STOP_IMMEDIATELY), host.effects ().executionOrder ().getLast ());
    }


    @Test
    void sessionPadConsumesTheHeldStopGesture ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.initialBridge (sessionBridge (4, "SESSION", "TRACK", StableDestinationWorkspace.SESSION_BANK));
        host.start (Optional.empty ());
        final int effectsBeforeGesture = host.effects ().executionOrder ().size ();

        host.controllerButton (STOP_CLIP_BUTTON, true);
        host.controllerPad (PushControlIds.pad (33), true);
        host.controllerPad (PushControlIds.pad (33), false);
        host.controllerButton (STOP_CLIP_BUTTON, false);

        assertEquals (effectsBeforeGesture, host.effects ().executionOrder ().size ());
    }


    @Test
    void vsLiveKeepsItsGridAndStopControlWhenTheStablePageChanges ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        enterVsLive (host);
        host.bridge (sessionBridge (2, "DRUM_PAD", "WORKSPACE", VsLiveWorkspace.SESSION_BANK));
        assertVsLive (host.effects ().desiredControllerWorkspace ());

        host.controllerAction (switchParameterContext (), sessionBridge (3, "DRUM_PAD", "TRACK", VsLiveWorkspace.SESSION_BANK));

        assertEquals (VsLiveWorkspace.NAME + " / Track Mix", host.effects ().desiredControllerWorkspace ().name ());
        assertEquals (Set.of (
            ControllerViewFacet.TRACK_MIXER_PAGE,
            ControllerViewFacet.SESSION_NAVIGATION,
            ControllerViewFacet.SESSION_CLIP_GRID_UPPER,
            ControllerViewFacet.SESSION_SCENE_KEYS_UPPER,
            ControllerViewFacet.DRUM_CONTROLLER_LOWER,
            ControllerViewFacet.DRUM_PITCH_BEND), host.effects ().desiredControllerWorkspace ().facets ());
        assertEquals (960, host.effects ().desiredOutput ().display ().width ());
        assertEquals (160, host.effects ().desiredOutput ().display ().height ());
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (PushControlIds.button ("ROW1_1"), InputKind.BUTTON));
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (PushControlIds.continuous ("KNOB1"), InputKind.RELATIVE));
        assertEquals (Optional.of (InputRouteMode.OBSERVE), host.effects ().desiredInputRoutes ().mode (STOP_CLIP_BUTTON, InputKind.BUTTON));
    }


    @Test
    void vsLiveSelectedTrackRouteNeutralizationDoesNotSelectTheMixPage ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        enterVsLive (host);
        host.bridge (sessionBridge (2, "DRUM_PAD", "WORKSPACE", VsLiveWorkspace.SESSION_BANK));

        // Selected-track Note routing uses TRACK as a temporary stable safety layout. Without a
        // physical page action, that implementation detail cannot replace the composite page.
        host.bridge (sessionBridge (3, "DRUM_PAD", "TRACK", VsLiveWorkspace.SESSION_BANK));

        assertEquals (VsLiveWorkspace.NAME, host.effects ().desiredControllerWorkspace ().name ());
        assertTrue (host.effects ().desiredControllerWorkspace ().facets ().contains (ControllerViewFacet.PROJECT_MACRO_CONTROLS));
        assertFalse (host.effects ().desiredControllerWorkspace ().facets ().contains (ControllerViewFacet.TRACK_MIXER_PAGE));
    }


    @Test
    void vsLiveTrackMixOwnsItsParameterReadbackRenderingAndEncoderEffects ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        enterVsLive (host);
        host.bridge (sessionBridge (2, "DRUM_PAD", "WORKSPACE", VsLiveWorkspace.SESSION_BANK));
        final ParameterTargetSnapshot send = parameter (ParameterSlot.active (2), "Reverb", 256, "25 %");
        host.controllerAction (switchParameterContext (), sessionMixBridge (Map.of (
            ParameterSlot.active (0), parameter (ParameterSlot.active (0), "Volume", 768, "-6.0 dB"),
            ParameterSlot.active (1), parameter (ParameterSlot.active (1), "Pan", 512, "C"),
            ParameterSlot.active (2), send)));

        assertTrue (host.effects ().desiredParameterBanks ().banks ().contains (ParameterBankId.ACTIVE));
        assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextAt text && "Volume".equals (text.text ())));
        assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextAt text && "Reverb".equals (text.text ())));

        host.controllerMotion (PushControlIds.continuous ("KNOB3"), InputKind.RELATIVE, 2);

        assertEquals (new AdjustParameterValueEffect (send.target (), 20), host.effects ().executionOrder ().getLast ());
    }


    @Test
    void vsLiveComposesProjectAndTrackViewsWithAuthoritativeTrackSelectionFeedback ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        final ControlId secondTrackButton = PushControlIds.button ("ROW1_2");
        final RgbColor secondTrackColor = new RgbColor (25, 50, 100);
        host.start (Optional.empty ());
        enterVsLive (host);
        host.bridge (trackSelectionBridge (2, false));

        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (secondTrackButton, InputKind.BUTTON));
        assertEquals (secondTrackColor, light (host, secondTrackButton));
        assertEquals (960, host.effects ().desiredOutput ().display ().width ());
        assertEquals (160, host.effects ().desiredOutput ().display ().height ());
        assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && "Drums".equals (text.text ())));
        assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && "Bass".equals (text.text ())));
        assertFalse (hasFooterSelection (host, 1, secondTrackColor));

        final int submittedEffects = host.effects ().executionOrder ().size ();
        host.controllerButton (secondTrackButton, true);

        assertEquals (submittedEffects + 1, host.effects ().executionOrder ().size ());
        assertEquals (new SelectSessionTrackEffect (12, VsLiveWorkspace.SESSION_BANK, 1, "track-8"), host.effects ().executionOrder ().getLast ());
        assertFalse (hasFooterSelection (host, 1, secondTrackColor));

        host.bridge (trackSelectionBridge (3, true));
        assertTrue (hasFooterSelection (host, 1, secondTrackColor));

        final int afterReadback = host.effects ().executionOrder ().size ();
        host.controllerButton (secondTrackButton, false);
        assertEquals (afterReadback, host.effects ().executionOrder ().size ());
    }


    @Test
    void vsLiveTrackSelectionKeepsMixSelectedWhileNewParametersAreUnavailable ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        final ControlId secondTrackButton = PushControlIds.button ("ROW1_2");
        host.start (Optional.empty ());
        enterVsLive (host);
        host.bridge (trackSelectionBridge (2, false, "WORKSPACE"));
        host.controllerAction (switchParameterContext (), trackSelectionBridge (3, false, "TRACK"));

        assertEquals (VsLiveWorkspace.NAME + " / Track Mix", host.effects ().desiredControllerWorkspace ().name ());
        assertMixSelectedWithoutInputOutputFallback (host);

        host.controllerButton (secondTrackButton, true);
        assertEquals (new SelectSessionTrackEffect (12, VsLiveWorkspace.SESSION_BANK, 1, "track-8"), host.effects ().executionOrder ().getLast ());
        host.bridge (trackSelectionBridge (4, true, "TRACK"));

        assertTrue (hasFooterSelection (host, 1, new RgbColor (25, 50, 100)));
        assertMixSelectedWithoutInputOutputFallback (host);
    }


    @Test
    void sessionStopGestureConsumptionSurvivesACompositePageTransition ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        enterVsLive (host);
        host.bridge (sessionBridge (2, "DRUM_PAD", "WORKSPACE", VsLiveWorkspace.SESSION_BANK));
        final int effectsBeforeGesture = host.effects ().executionOrder ().size ();

        host.controllerButton (STOP_CLIP_BUTTON, true);
        host.controllerPad (PushControlIds.pad (33), true);
        host.controllerAction (switchParameterContext (), sessionBridge (3, "DRUM_PAD", "TRACK", VsLiveWorkspace.SESSION_BANK));
        host.controllerPad (PushControlIds.pad (33), false);
        host.controllerButton (STOP_CLIP_BUTTON, false);

        assertEquals (effectsBeforeGesture, host.effects ().executionOrder ().size ());
    }


    @Test
    void enteringMasterFromVsLivePreservesOnlyTheVsLiveGridOwnership ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        enterVsLive (host);

        host.bridge (masterBridge (true, true, false));

        assertMasterOverVsLive (host.effects ().desiredControllerWorkspace ());
    }


    private static void assertMasterOverVsLive (final DesiredControllerWorkspace workspace)
    {
        assertEquals (VsLiveWorkspace.SESSION_BANK, workspace.sessionBankShape ());
        assertEquals (Set.of (
            ControllerViewFacet.MASTER_CONTROLS,
            ControllerViewFacet.SESSION_NAVIGATION,
            ControllerViewFacet.SESSION_CLIP_GRID_UPPER,
            ControllerViewFacet.SESSION_SCENE_KEYS_UPPER,
            ControllerViewFacet.DRUM_CONTROLLER_LOWER,
            ControllerViewFacet.DRUM_PITCH_BEND), workspace.facets ());
    }


    @Test
    void masterKnobKeepsItsGlobalSnapbackBinding ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.bridge (masterBridge (true, true, false));
        final ParameterTargetSnapshot baseline = parameter (ParameterSlot.MASTER_VOLUME, "Master Volume", 84, "-6.0 dB");

        host.controllerButton (SHIFT_BUTTON, true);
        host.parameterMutation (PushControlIds.continuous ("MASTER_KNOB"), baseline);

        assertEquals (Map.of (baseline.target (), 84.0), host.effects ().desiredParameterInteraction ().baselines ());
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
    void muteAndSoloAlwaysTargetTheAuthoritativeSelectionAndWaitForReadback ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.selectedTrack (selectedTrack (false, false, false));

        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (MUTE_BUTTON, InputKind.BUTTON));
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (SOLO_BUTTON, InputKind.BUTTON));
        assertEquals (new RgbColor (30, 30, 30), light (host, MUTE_BUTTON));
        assertEquals (new RgbColor (30, 30, 30), light (host, SOLO_BUTTON));

        host.controllerButton (MUTE_BUTTON, true);
        host.controllerButton (MUTE_BUTTON, false);

        assertEquals (new SetSelectedTrackBooleanEffect (7, "track-7", SelectedTrackBoolean.MUTED, true), host.effects ().executionOrder ().getLast ());
        assertEquals (new RgbColor (30, 30, 30), light (host, MUTE_BUTTON));

        host.selectedTrack (selectedTrack (false, true, false));
        assertEquals (new RgbColor (39, 27, 0), light (host, MUTE_BUTTON));

        host.controllerButton (SHIFT_BUTTON, true);
        host.controllerButton (SOLO_BUTTON, true);
        host.controllerButtonLong (SOLO_BUTTON);
        host.controllerButton (SOLO_BUTTON, false);

        assertEquals (new SetSelectedTrackBooleanEffect (7, "track-7", SelectedTrackBoolean.SOLOED, true), host.effects ().executionOrder ().getLast ());
        assertEquals (new RgbColor (30, 30, 30), light (host, SOLO_BUTTON));

        host.selectedTrack (selectedTrack (false, true, true));
        assertEquals (new RgbColor (89, 89, 0), light (host, SOLO_BUTTON));
    }


    @Test
    void muteAndSoloFailClosedWithoutASelectedTrack ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());

        host.controllerButton (MUTE_BUTTON, true);
        host.controllerButton (MUTE_BUTTON, false);
        host.controllerButton (SOLO_BUTTON, true);
        host.controllerButton (SOLO_BUTTON, false);

        assertTrue (host.effects ().executionOrder ().isEmpty ());
        assertEquals (OFF, light (host, MUTE_BUTTON));
        assertEquals (OFF, light (host, SOLO_BUTTON));
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
        assertEquals (Optional.empty (), host.effects ().desiredInputRoutes ().mode (LAYOUT_BUTTON, InputKind.BUTTON));

        host.controllerButton (SESSION_BUTTON, true);

        assertStableSessionDestination (host.effects ().desiredControllerWorkspace ());

        host.bridge (layoutBridge ("SESSION", "TRACK"));
        assertStableSessionDestination (host.effects ().desiredControllerWorkspace ());
        host.controllerButton (SESSION_BUTTON, false);
        assertSelectedSession (host.effects ().desiredControllerWorkspace ());
    }


    @Test
    void vsLiveCanExitAndReenterWithItsCompleteOwnershipRestored ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());

        enterVsLive (host);
        host.controllerButton (SESSION_BUTTON, true);
        assertStableSessionDestination (host.effects ().desiredControllerWorkspace ());
        host.bridge (layoutBridge ("SESSION", "TRACK"));
        assertStableSessionDestination (host.effects ().desiredControllerWorkspace ());
        host.controllerButton (SESSION_BUTTON, false);
        assertSelectedSession (host.effects ().desiredControllerWorkspace ());

        enterVsLive (host);

        assertVsLive (host.effects ().desiredControllerWorkspace ());
        assertEquals (Optional.of (InputRouteMode.OBSERVE), host.effects ().desiredInputRoutes ().mode (PushControlIds.pad (10), InputKind.POLY_PRESSURE));
        assertEquals (Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.SESSION_BANK, BridgeSubscription.TRANSPORT, BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.NOTE_VIEW, BridgeSubscription.NOTE_REPEAT, BridgeSubscription.DRUM_PADS, BridgeSubscription.PARAMETERS, BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK, BridgeSubscription.PROJECT), host.effects ().desiredBridgeSubscriptions ().domains ());
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
    void checkpointReplaysAnUnacknowledgedSessionDestinationView ()
    {
        final FakeCoreHost first = host (ClipCatalogSnapshot.empty ());
        first.start (Optional.empty ());
        enterVsLive (first);
        first.controllerButton (SESSION_BUTTON, true);

        final PullCoreProvider provider = new PullCoreProvider ();
        final FakeCoreHost restored = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities ());
        restored.start (Optional.of (first.checkpoint ()));

        assertStableSessionDestination (restored.effects ().desiredControllerWorkspace ());
        restored.bridge (layoutBridge ("SESSION", "TRACK"));
        assertSelectedSession (restored.effects ().desiredControllerWorkspace ());
    }


    @Test
    void checkpointRetainsAnAcknowledgedNoteDestinationAcrossNeutralReadback ()
    {
        final SelectedTrackSnapshot juno = selectedTrack (8, "juno", 5, true, false);
        final NoteViewSnapshot preference = new NoteViewSnapshot (8, "juno", 5, ControllerNoteView.PLAY, false);
        final FakeCoreHost first = host (ClipCatalogSnapshot.empty ());
        first.start (Optional.empty ());
        first.bridge (noteBridge ("PLAY", juno, preference, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));

        final PullCoreProvider provider = new PullCoreProvider ();
        final FakeCoreHost restored = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities ());
        restored.start (Optional.of (first.checkpoint ()));
        restored.bridge (noteBridge (2, "SESSION", juno, preference, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));

        assertEquals (ControllerNoteView.PLAY, restored.effects ().desiredControllerLayout ().noteView ());
        assertEquals (DesiredNoteInputRoute.selectedTrack (8, "juno"), restored.effects ().desiredNoteInputRoute ());
    }


    @Test
    void checkpointRetainsTheLastAuthoritativeEngineOwnerAcrossCoreReload ()
    {
        final FakeCoreHost first = host (ClipCatalogSnapshot.empty ());
        first.start (Optional.empty ());
        first.bridge (projectBridge ("project-a", true, true, true, true, false, selectedTrack (false)));
        first.bridge (projectBridge ("project-b", false, false, true, true, false, selectedTrack (false)));

        final PullCoreProvider provider = new PullCoreProvider ();
        final FakeCoreHost restored = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities ());
        restored.start (Optional.of (first.checkpoint ()));
        restored.bridge (projectBridge ("project-b", false, false, true, true, false, selectedTrack (false)));

        assertEquals (PURPLE, light (restored, PLAY_BUTTON));
    }


    @Test
    void noteExitsVsLive ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.controllerButton (SHIFT_BUTTON, true);
        host.controllerButton (SESSION_BUTTON, true);

        host.controllerButton (NOTE_BUTTON, true);

        assertEquals (Set.of (ControllerViewFacet.TRACK_MIXER_PAGE), host.effects ().desiredControllerWorkspace ().facets ());
        host.bridge (layoutBridge ("DRUM_PAD", "WORKSPACE"));
        assertEquals (Set.of (ControllerViewFacet.TRACK_MIXER_PAGE), host.effects ().desiredControllerWorkspace ().facets ());
        host.bridge (layoutBridge ("DRUM_PAD", "TRACK"));
        assertEquals (DesiredControllerWorkspace.empty (), host.effects ().desiredControllerWorkspace ());
    }


    @Test
    void defaultDrumAndVsLiveMapPlayablePadPressureInTheReloadableCore ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.bridge (bridgeWithPressure (GridPressureConfiguration.POLY, 48));

        host.controllerMotion (PushControlIds.pad (10), InputKind.POLY_PRESSURE, 91);
        assertEquals (new SendNoteInputMidiEffect (0xA0, 53, 91), host.effects ().executionOrder ().getLast ());

        enterVsLive (host);
        assertEquals (Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.SESSION_BANK, BridgeSubscription.TRANSPORT, BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.NOTE_VIEW, BridgeSubscription.NOTE_REPEAT, BridgeSubscription.DRUM_PADS, BridgeSubscription.PARAMETERS, BridgeSubscription.CONTROLLER_MAPPING_FEEDBACK, BridgeSubscription.PROJECT), host.effects ().desiredBridgeSubscriptions ().domains ());
        assertEquals (Set.of (ParameterBankId.PROJECT_REMOTE, ParameterBankId.GLOBAL), host.effects ().desiredParameterBanks ().banks ());
        final int defaultEffectCount = host.effects ().executionOrder ().size ();
        host.controllerMotion (PushControlIds.pad (10), InputKind.POLY_PRESSURE, 91);

        assertEquals (defaultEffectCount + 1, host.effects ().executionOrder ().size ());
        assertEquals (new SendNoteInputMidiEffect (0xA0, 53, 91), host.effects ().executionOrder ().getLast ());
        assertEquals (Optional.of (InputRouteMode.OBSERVE), host.effects ().desiredInputRoutes ().mode (PushControlIds.pad (10), InputKind.POLY_PRESSURE));
        assertEquals (Optional.of (InputRouteMode.OBSERVE), host.effects ().desiredInputRoutes ().mode (PushControlIds.pad (10), InputKind.PAD));

        host.controllerButton (SESSION_BUTTON, true);
        assertEquals (Set.of (BridgeSubscription.SELECTED_TRACK, BridgeSubscription.SESSION_BANK, BridgeSubscription.TRANSPORT, BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.NOTE_VIEW, BridgeSubscription.PROJECT), host.effects ().desiredBridgeSubscriptions ().domains ());
        assertEquals (Optional.empty (), host.effects ().desiredInputRoutes ().mode (PushControlIds.pad (10), InputKind.POLY_PRESSURE));
    }


    @Test
    void drumPlayPadsRenderOnlyAuthoritativeAlignedReadbackInBothCompositions ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot drums = selectedTrack (9, "drums", 0, true, false);
        final NoteViewSnapshot preference = new NoteViewSnapshot (9, "drums", 0, ControllerNoteView.DRUM_PAD, true);
        final RgbColor trackColor = drums.color ();

        host.bridge (noteBridge ("DRUM_PAD", drums, preference, drum (drums, 0), NoteRepeatSnapshot.empty ()));
        assertEquals (trackColor, light (host, PushControlIds.pad (1)));
        assertEquals (OFF, light (host, PushControlIds.pad (2)));

        host.controllerPad (PushControlIds.pad (1), true);
        assertEquals (trackColor, light (host, PushControlIds.pad (1)), "a press is not authoritative playback state");
        host.controllerPad (PushControlIds.pad (1), false);
        host.bridge (noteBridge ("DRUM_PAD", drums, preference, drum (drums, 71), NoteRepeatSnapshot.empty ()));
        assertEquals (GREEN, light (host, PushControlIds.pad (1)));

        enterVsLive (host);
        host.bridge (workspaceDrumBridge (drums, preference, drum (drums, 8), NoteRepeatSnapshot.empty ()));
        assertEquals (new RgbColor (0, 89, 0), light (host, PushControlIds.pad (1)));

        host.bridge (workspaceDrumBridge (drums, preference, new DrumContextSnapshot (5, 9, "other-track", "drum-device", true, true, 36, drumPads (8)), NoteRepeatSnapshot.empty ()));
        assertEquals (OFF, light (host, PushControlIds.pad (1)));
    }


    @Test
    void drumControlPadsScopeMappingsAndRenderOnlyLaterAuthoritativeFeedback ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final ControlId first = CoreControls.DRUM_CONTROL_PADS.getFirst ();
        final ControlId second = CoreControls.DRUM_CONTROL_PADS.get (1);

        host.bridge (controllerMappingUnavailableBridge ());
        assertEquals (OFF, light (host, first));
        assertEquals (OFF, light (host, second));
        host.bridge (controllerMappingFeedbackBridge (false));

        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), host.effects ().desiredInputRoutes ().mode (first, InputKind.PAD));
        assertEquals (Set.of (
            new ControllerMappingBinding (CoreControls.DRUM_CONTROL_PADS.get (0), CoreControllerMappings.DRUM_CONTROL_PADS.get (0)),
            new ControllerMappingBinding (CoreControls.DRUM_CONTROL_PADS.get (1), CoreControllerMappings.DRUM_CONTROL_PADS.get (1)),
            new ControllerMappingBinding (CoreControls.DRUM_CONTROL_PADS.get (2), CoreControllerMappings.DRUM_CONTROL_PADS.get (2)),
            new ControllerMappingBinding (CoreControls.DRUM_CONTROL_PADS.get (3), CoreControllerMappings.DRUM_CONTROL_PADS.get (3))), host.effects ().desiredOutput ().controllerMappings ().bindings ());
        assertEquals (OFF, light (host, first));

        final int beforePress = host.effects ().executionOrder ().size ();
        host.controllerPad (first, true);
        assertEquals (beforePress, host.effects ().executionOrder ().size ());
        assertEquals (OFF, light (host, first));
        host.controllerPad (first, false);
        assertEquals (beforePress, host.effects ().executionOrder ().size ());

        host.bridge (controllerMappingFeedbackBridge (false));
        assertEquals (OFF, light (host, first));
        host.bridge (controllerMappingFeedbackBridge (true));
        assertEquals (RED, light (host, first));
        assertEquals (OFF, light (host, second));
        host.controllerPad (first, true);
        assertEquals (beforePress, host.effects ().executionOrder ().size ());
        assertEquals (RED, light (host, first));
        host.controllerPad (first, false);

        host.bridge (controllerMappingFeedbackBridge (false));
        assertEquals (OFF, light (host, first));

        host.bridge (controllerMappingFeedbackBridge (1));
        assertEquals (OFF, light (host, first));
        assertEquals (RED, light (host, second));

        host.controllerButton (SESSION_BUTTON, true);
        host.bridge (layoutBridge ("SESSION", "TRACK"));
        host.controllerButton (SESSION_BUTTON, false);
        assertTrue (host.effects ().desiredOutput ().controllerMappings ().bindings ().isEmpty ());
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


    @Test
    void selectedTrackNoteViewerFailsClosedUntilPrivatePreferenceIdentityAligns ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot juno = selectedTrack (8, "juno", 5, true, false);

        host.bridge (noteBridge ("DRUM_PAD", juno, new NoteViewSnapshot (7, "drums", 0, ControllerNoteView.DRUM_PAD, true), DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));
        assertFalse (host.effects ().desiredControllerLayout ().isPresent ());
        assertEquals (DesiredNoteInputRoute.disabled (), host.effects ().desiredNoteInputRoute ());

        host.bridge (noteBridge ("DRUM_PAD", juno, new NoteViewSnapshot (8, "juno", 5, ControllerNoteView.PLAY, false), DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));
        assertEquals (ControllerNoteView.PLAY, host.effects ().desiredControllerLayout ().noteView ());
        assertEquals (DesiredNoteInputRoute.selectedTrack (8, "juno"), host.effects ().desiredNoteInputRoute ());

        host.bridge (noteBridge ("SESSION", juno, new NoteViewSnapshot (8, "juno", 5, ControllerNoteView.PLAY, false), DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));
        assertEquals (ControllerNoteView.PLAY, host.effects ().desiredControllerLayout ().noteView ());
        assertEquals (DesiredNoteInputRoute.selectedTrack (8, "juno"), host.effects ().desiredNoteInputRoute ());
    }


    @Test
    void selectedTrackHandoffRetainsNoteDestinationThroughFailClosedNeutralization ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot juno = selectedTrack (8, "juno", 5, true, false);
        final NoteViewSnapshot junoPreference = new NoteViewSnapshot (8, "juno", 5, ControllerNoteView.PLAY, false);
        host.bridge (noteBridge ("PLAY", juno, junoPreference, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));

        final SelectedTrackSnapshot drums = selectedTrack (9, "drums", 0, true, false);
        host.bridge (noteBridge (2, "PLAY", drums, junoPreference, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));
        assertEquals (DesiredNotePerformance.inactive (), host.effects ().desiredNotePerformance ());

        final NoteViewSnapshot drumPreference = new NoteViewSnapshot (9, "drums", 0, ControllerNoteView.DRUM_PAD, true);
        host.bridge (noteBridge (3, "SESSION", drums, drumPreference, drum (drums), NoteRepeatSnapshot.empty ()));
        assertEquals (ControllerNoteView.DRUM_PAD, host.effects ().desiredControllerLayout ().noteView ());
        assertEquals (DesiredNoteInputRoute.selectedTrack (9, "drums"), host.effects ().desiredNoteInputRoute ());

        final NoteRepeatSnapshot rollEnabled = new NoteRepeatSnapshot (true, true, false, NoteRepeatMode.RANDOM, 2, 1.0 / 3.0, 0.25, true, true, false, false);
        host.bridge (noteBridge (4, "DRUM_PAD", drums, drumPreference, drum (drums), rollEnabled));
        assertTrue (host.effects ().desiredNoteRepeat ().owned ());
    }


    @Test
    void explicitSessionDuringNeutralizedHandoffSupersedesRetainedNoteDestination ()
    {
        final FakeCoreHost host = neutralizedJunoToDrumsHandoff ();

        host.controllerButton (SESSION_BUTTON, true);
        host.controllerButton (SESSION_BUTTON, false);

        final SelectedTrackSnapshot drums = selectedTrack (9, "drums", 0, true, false);
        final NoteViewSnapshot drumPreference = new NoteViewSnapshot (9, "drums", 0, ControllerNoteView.DRUM_PAD, true);
        host.bridge (noteBridge (3, "SESSION", drums, drumPreference, drum (drums), NoteRepeatSnapshot.empty ()));

        assertEquals (DesiredNotePerformance.inactive (), host.effects ().desiredNotePerformance ());
        assertFalse (host.effects ().desiredNoteRepeat ().owned ());
    }


    @Test
    void temporarySessionDuringNeutralizedHandoffRestoresRetainedNoteDestination ()
    {
        final FakeCoreHost host = neutralizedJunoToDrumsHandoff ();

        host.controllerButton (SESSION_BUTTON, true);
        host.controllerButtonLong (SESSION_BUTTON);
        host.controllerButton (SESSION_BUTTON, false);

        final SelectedTrackSnapshot drums = selectedTrack (9, "drums", 0, true, false);
        final NoteViewSnapshot drumPreference = new NoteViewSnapshot (9, "drums", 0, ControllerNoteView.DRUM_PAD, true);
        host.bridge (noteBridge (3, "SESSION", drums, drumPreference, drum (drums), NoteRepeatSnapshot.empty ()));

        assertEquals (ControllerNoteView.DRUM_PAD, host.effects ().desiredControllerLayout ().noteView ());
        assertEquals (DesiredNoteInputRoute.selectedTrack (9, "drums"), host.effects ().desiredNoteInputRoute ());
    }


    @Test
    void sessionExitSuppressesNotePerformanceAndAUsedTemporarySessionRestoresIt ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot juno = selectedTrack (8, "juno", 5, true, false);
        final NoteViewSnapshot preference = new NoteViewSnapshot (8, "juno", 5, ControllerNoteView.PLAY, false);
        host.bridge (noteBridge ("PLAY", juno, preference, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));

        host.controllerButton (SESSION_BUTTON, true);

        assertEquals (DesiredNotePerformance.inactive (), host.effects ().desiredNotePerformance ());
        assertStableSessionDestination (host.effects ().desiredControllerWorkspace ());

        host.bridge (noteBridge (2, "SESSION", juno, preference, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));
        assertStableSessionDestination (host.effects ().desiredControllerWorkspace ());
        host.controllerPad (PushControlIds.pad (1), true);
        host.controllerPad (PushControlIds.pad (1), false);
        host.controllerButton (SESSION_BUTTON, false);
        host.bridge (noteBridge (3, "PLAY", juno, preference, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));

        assertEquals (ControllerNoteView.PLAY, host.effects ().desiredControllerLayout ().noteView ());
        assertEquals (DesiredNoteInputRoute.selectedTrack (8, "juno"), host.effects ().desiredNoteInputRoute ());
    }


    @Test
    void longSessionRestoresThePriorNoteViewerOnRelease ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot juno = selectedTrack (8, "juno", 5, true, false);
        final NoteViewSnapshot preference = new NoteViewSnapshot (8, "juno", 5, ControllerNoteView.PLAY, false);
        host.bridge (noteBridge ("PLAY", juno, preference, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));

        host.controllerButton (SESSION_BUTTON, true);
        host.controllerButtonLong (SESSION_BUTTON);
        host.controllerButton (SESSION_BUTTON, false);

        assertEquals (ControllerNoteView.PLAY, host.effects ().desiredControllerLayout ().noteView ());
        assertEquals (DesiredNoteInputRoute.selectedTrack (8, "juno"), host.effects ().desiredNoteInputRoute ());
    }


    @Test
    void shiftNotePersistsAndUsesTheDrumControllerPreferenceThroughCore ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot drums = selectedTrack (9, "drums", 0, true, false);
        final NoteViewSnapshot preference = new NoteViewSnapshot (9, "drums", 0, ControllerNoteView.PLAY, true);
        host.bridge (noteBridge ("SESSION", drums, preference, drum (drums), NoteRepeatSnapshot.empty ()));
        host.controllerButton (SESSION_BUTTON, true);

        host.controllerButton (SHIFT_BUTTON, true);
        host.controllerButton (NOTE_BUTTON, true);

        assertEquals (new SetNoteViewPreferenceEffect (9, "drums", 0, ControllerNoteView.DRUM_PAD), host.effects ().executionOrder ().getLast ());
        assertEquals (ControllerNoteView.DRUM_PAD, host.effects ().desiredControllerLayout ().noteView ());
        assertEquals (DesiredNoteInputRoute.selectedTrack (9, "drums"), host.effects ().desiredNoteInputRoute ());
    }


    @Test
    void layoutButtonPreservesTheCompleteLegacyTransitionTable ()
    {
        for (final List<ControllerNoteView> cycle: List.of (
            List.of (ControllerNoteView.PLAY, ControllerNoteView.CHORDS, ControllerNoteView.PIANO, ControllerNoteView.DRUM64),
            List.of (ControllerNoteView.SEQUENCER, ControllerNoteView.RAINDROPS, ControllerNoteView.DRUM, ControllerNoteView.DRUM4, ControllerNoteView.DRUM8)))
        {
            for (int index = 0; index < cycle.size (); index++)
                assertLayoutTransition (cycle.get (index), cycle.get ( (index + 1) % cycle.size ()), false);
        }
        for (final ControllerNoteView sequencer: Set.of (ControllerNoteView.DRUM, ControllerNoteView.DRUM4, ControllerNoteView.DRUM8, ControllerNoteView.SEQUENCER, ControllerNoteView.RAINDROPS, ControllerNoteView.POLY_SEQUENCER))
            assertLayoutTransition (sequencer, ControllerNoteView.PLAY, true);
        for (final ControllerNoteView other: Set.of (ControllerNoteView.PLAY, ControllerNoteView.CHORDS, ControllerNoteView.PIANO, ControllerNoteView.DRUM64, ControllerNoteView.DRUM_PAD, ControllerNoteView.DRUM_XOX))
            assertLayoutTransition (other, ControllerNoteView.SEQUENCER, true);
    }


    @Test
    void shiftedLayoutEntersSequencerFromSessionThroughTheSameNoteLifecycle ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot juno = selectedTrack (8, "juno", 5, true, false);
        final NoteViewSnapshot play = new NoteViewSnapshot (8, "juno", 5, ControllerNoteView.PLAY, false);
        host.bridge (noteBridge (1, "SESSION", juno, play, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));

        host.controllerButton (SHIFT_BUTTON, true);
        host.controllerButton (LAYOUT_BUTTON, true);

        assertEquals (new SetNoteViewPreferenceEffect (8, "juno", 5, ControllerNoteView.SEQUENCER), host.effects ().executionOrder ().getLast ());
        assertEquals (ControllerNoteView.SEQUENCER, host.effects ().desiredControllerLayout ().noteView ());
        assertEquals (DesiredNoteInputRoute.selectedTrack (8, "juno"), host.effects ().desiredNoteInputRoute ());
        assertEquals (Set.of (ControllerViewFacet.TRACK_MIXER_PAGE), host.effects ().desiredControllerWorkspace ().facets ());
    }


    @Test
    void layoutButtonFailsClosedWhileSelectedAndPreferenceTargetsDisagree ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot drums = selectedTrack (9, "drums", 0, true, false);
        final NoteViewSnapshot staleJuno = new NoteViewSnapshot (8, "juno", 5, ControllerNoteView.PLAY, false);
        host.bridge (noteBridge (1, "PLAY", drums, staleJuno, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));

        host.controllerButton (LAYOUT_BUTTON, true);

        assertTrue (host.effects ().executionOrder ().isEmpty ());
        assertEquals (DesiredNotePerformance.inactive (), host.effects ().desiredNotePerformance ());
    }


    @Test
    void shiftedLayoutDoesNotCreateAMusicalViewerForAnAudioTarget ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot audio = selectedTrack (8, "audio", 5, false, true);
        host.bridge (noteBridge (1, "CLIP_LENGTH", audio, new NoteViewSnapshot (8, "audio", 5, ControllerNoteView.CLIP_LENGTH, false), DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));

        host.controllerButton (SHIFT_BUTTON, true);
        host.controllerButton (LAYOUT_BUTTON, true);

        assertTrue (host.effects ().executionOrder ().isEmpty ());
        assertEquals (ControllerNoteView.CLIP_LENGTH, host.effects ().desiredControllerLayout ().noteView ());
    }


    @Test
    void derivedNoteViewerAdvancesFromMelodicToDrumAfterAlignedDeviceReadback ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot drums = selectedTrack (9, "drums", 0, true, false);
        final NoteViewSnapshot melodic = new NoteViewSnapshot (9, "drums", 0, ControllerNoteView.NONE, false);

        host.bridge (noteBridge ("PLAY", drums, melodic, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));
        assertEquals (ControllerNoteView.PLAY, host.effects ().desiredControllerLayout ().noteView ());
        assertEquals (DesiredNoteInputRoute.selectedTrack (9, "drums"), host.effects ().desiredNoteInputRoute ());

        final NoteViewSnapshot drumReady = new NoteViewSnapshot (9, "drums", 0, ControllerNoteView.NONE, true);
        host.bridge (noteBridge ("PLAY", drums, drumReady, drum (drums), NoteRepeatSnapshot.empty ()));
        assertEquals (ControllerNoteView.DRUM_PAD, host.effects ().desiredControllerLayout ().noteView ());
    }


    @Test
    void incompatibleStoredNoteViewFallsBackToTheSelectedTrackLayout ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot juno = selectedTrack (10, "juno", 5, true, false);
        final NoteViewSnapshot staleDrumPreference = new NoteViewSnapshot (10, "juno", 5, ControllerNoteView.DRUM_PAD, false);

        host.bridge (noteBridge ("DRUM_PAD", juno, staleDrumPreference, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));

        assertEquals (ControllerNoteView.PLAY, host.effects ().desiredControllerLayout ().noteView ());
    }


    @Test
    void compatibleStoredNoteViewWinsOverTheDerivedTrackLayout ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot juno = selectedTrack (10, "juno", 5, true, false);
        final NoteViewSnapshot chordsPreference = new NoteViewSnapshot (10, "juno", 5, ControllerNoteView.CHORDS, false);

        host.bridge (noteBridge ("PLAY", juno, chordsPreference, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));

        assertEquals (ControllerNoteView.CHORDS, host.effects ().desiredControllerLayout ().noteView ());
    }


    @Test
    void automaticRollIsAttachedOnlyToTheResolvedDrumViewer ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot drums = selectedTrack (9, "drums", 0, true, false);
        final NoteViewSnapshot playPreference = new NoteViewSnapshot (9, "drums", 0, ControllerNoteView.PLAY, true);
        final DesiredNoteRepeat automatic = new DesiredNoteRepeat (true, true, NoteRepeatMode.UP, 0, 1.0 / 4.0, 0.5, false, false, true, true);

        host.bridge (noteBridge ("DRUM_PAD", drums, playPreference, drum (drums), repeatReadback (true, automatic)));

        assertEquals (ControllerNoteView.PLAY, host.effects ().desiredControllerLayout ().noteView ());
        assertFalse (host.effects ().desiredNoteRepeat ().owned ());
    }


    @Test
    void stableModalOverlaysPreserveTheUnderlyingNoteViewer ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot juno = selectedTrack (8, "juno", 5, true, false);
        final NoteViewSnapshot preference = new NoteViewSnapshot (8, "juno", 5, ControllerNoteView.PLAY, false);

        long generation = 1;
        for (final String mode: List.of ("DEVICE", "BROWSER", "SCALES"))
        {
            host.bridge (noteBridge (generation++, "PLAY", mode, juno, preference, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));
            assertEquals (ControllerNoteView.PLAY, host.effects ().desiredControllerLayout ().noteView ());
            assertEquals (DesiredNoteInputRoute.selectedTrack (8, "juno"), host.effects ().desiredNotePerformance ().inputRoute ());
        }
    }


    @Test
    void drumRollOwnsOnlyTheDrumRatePadsAndWaitsForEngineReadback ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot drums = selectedTrack (9, "drums", 0, true, false);
        final NoteViewSnapshot preference = new NoteViewSnapshot (9, "drums", 0, ControllerNoteView.NONE, true);
        final NoteRepeatSnapshot manual = new NoteRepeatSnapshot (true, true, false, NoteRepeatMode.RANDOM, 2, 1.0 / 3.0, 0.25, true, true, false, false);
        host.bridge (noteBridge ("DRUM_PAD", drums, preference, drum (drums), manual));

        assertEquals (InputRouteMode.EXCLUSIVE, host.effects ().desiredInputRoutes ().modeOrNull (PushControlIds.pad (5), InputKind.PAD));
        assertTrue (host.effects ().desiredNoteRepeat ().owned ());
        assertEquals (1.0 / 4.0, host.effects ().desiredNoteRepeat ().period ());

        host.controllerPad (PushControlIds.pad (5), true);
        final DesiredNoteRepeat requested = host.effects ().desiredNoteRepeat ();
        final RgbColor awaitingReadback = light (host, PushControlIds.pad (5));
        assertEquals (2.0 / 3.0, requested.period ());

        host.bridge (noteBridge ("DRUM_PAD", drums, preference, drum (drums), repeatReadback (true, requested)));
        assertFalse (awaitingReadback.equals (light (host, PushControlIds.pad (5))));

        host.bridge (noteBridge ("DRUM_PAD", drums, preference, drum (drums), repeatReadback (false, requested)));
        assertFalse (host.effects ().desiredNoteRepeat ().owned ());
        assertEquals (OFF, light (host, PushControlIds.pad (5)));
    }


    @Test
    void shiftSessionDrumControllerOwnsTheSameAutomaticRoll ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        enterVsLive (host);
        final SelectedTrackSnapshot drums = selectedTrack (9, "drums", 0, true, false);
        final NoteViewSnapshot preference = new NoteViewSnapshot (9, "drums", 0, ControllerNoteView.NONE, true);
        final NoteRepeatSnapshot manual = new NoteRepeatSnapshot (true, true, false, NoteRepeatMode.RANDOM, 2, 1.0 / 3.0, 0.25, true, true, false, false);

        host.bridge (workspaceDrumBridge (drums, preference, drum (drums), manual));

        assertEquals (InputRouteMode.EXCLUSIVE, host.effects ().desiredInputRoutes ().modeOrNull (PushControlIds.pad (5), InputKind.PAD));
        assertTrue (host.effects ().desiredNoteRepeat ().owned ());
        assertEquals (DesiredNoteInputRoute.selectedTrack (9, "drums"), host.effects ().desiredNotePerformance ().inputRoute ());
    }


    @Test
    void vsLivePageOverlayRetainsAHeldDrumRateGesture ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        enterVsLive (host);
        final SelectedTrackSnapshot drums = selectedTrack (9, "drums", 0, true, false);
        final NoteViewSnapshot preference = new NoteViewSnapshot (9, "drums", 0, ControllerNoteView.NONE, true);
        final NoteRepeatSnapshot manual = new NoteRepeatSnapshot (true, true, false, NoteRepeatMode.RANDOM, 2, 1.0 / 3.0, 0.25, true, true, false, false);
        host.bridge (noteBridge (2, "DRUM_PAD", "WORKSPACE", drums, preference, drum (drums), manual));

        host.controllerPad (PushControlIds.pad (5), true);
        assertEquals (2.0 / 3.0, host.effects ().desiredNoteRepeat ().period ());

        host.controllerAction (switchParameterContext (), noteBridge (3, "DRUM_PAD", "TRACK", drums, preference, drum (drums), manual));

        assertEquals (VsLiveWorkspace.NAME + " / Track Mix", host.effects ().desiredControllerWorkspace ().name ());
        assertEquals (2.0 / 3.0, host.effects ().desiredNoteRepeat ().period ());
    }


    @Test
    void vsLiveMasterOverlayRetainsAHeldDrumRateGesture ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        enterVsLive (host);
        final SelectedTrackSnapshot drums = selectedTrack (9, "drums", 0, true, false);
        final NoteViewSnapshot preference = new NoteViewSnapshot (9, "drums", 0, ControllerNoteView.NONE, true);
        final NoteRepeatSnapshot manual = new NoteRepeatSnapshot (true, true, false, NoteRepeatMode.RANDOM, 2, 1.0 / 3.0, 0.25, true, true, false, false);
        host.bridge (noteBridge (2, "DRUM_PAD", "WORKSPACE", drums, preference, drum (drums), manual));

        host.controllerPad (PushControlIds.pad (5), true);
        host.bridge (noteBridge (3, "DRUM_PAD", "MASTER", drums, preference, drum (drums), manual));

        assertMasterOverVsLive (host.effects ().desiredControllerWorkspace ());
        assertEquals (2.0 / 3.0, host.effects ().desiredNoteRepeat ().period ());
    }


    @Test
    void staleDrumLayoutCannotRetainRollAfterTheSelectedTargetChanges ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot drums = selectedTrack (9, "drums", 0, true, false);
        final NoteViewSnapshot drumPreference = new NoteViewSnapshot (9, "drums", 0, ControllerNoteView.NONE, true);
        final DesiredNoteRepeat automatic = new DesiredNoteRepeat (true, true, NoteRepeatMode.UP, 0, 1.0 / 4.0, 0.5, false, false, true, true);
        host.bridge (noteBridge ("DRUM_PAD", drums, drumPreference, drum (drums), repeatReadback (true, automatic)));
        assertTrue (host.effects ().desiredNoteRepeat ().owned ());

        final SelectedTrackSnapshot juno = selectedTrack (10, "juno", 5, true, false);
        final NoteViewSnapshot junoPreference = new NoteViewSnapshot (10, "juno", 5, ControllerNoteView.NONE, false);
        host.bridge (noteBridge ("DRUM_PAD", juno, junoPreference, DrumContextSnapshot.empty (), repeatReadback (true, automatic)));

        assertEquals (ControllerNoteView.PLAY, host.effects ().desiredControllerLayout ().noteView ());
        assertFalse (host.effects ().desiredNoteRepeat ().owned ());
        assertEquals (OFF, light (host, PushControlIds.pad (5)));
    }


    private static CatalogClip clip (final long target, final String name)
    {
        return new CatalogClip (new ClipTargetId (target), name);
    }


    private static void assertLayoutTransition (final ControllerNoteView active, final ControllerNoteView expected, final boolean shifted)
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot juno = selectedTrack (8, "juno", 5, true, false);
        host.bridge (noteBridge (1, active.name (), juno, new NoteViewSnapshot (8, "juno", 5, active, false), DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));
        if (shifted)
            host.controllerButton (SHIFT_BUTTON, true);
        host.controllerButton (LAYOUT_BUTTON, true);

        assertEquals (new SetNoteViewPreferenceEffect (8, "juno", 5, expected), host.effects ().executionOrder ().getLast ());
        assertEquals (expected, host.effects ().desiredControllerLayout ().noteView ());
        assertEquals (DesiredNoteInputRoute.selectedTrack (8, "juno"), host.effects ().desiredNoteInputRoute ());
    }


    private static SelectedTrackSnapshot selectedTrack (final boolean recordArmed)
    {
        return selectedTrack (recordArmed, false, false);
    }


    private static SelectedTrackSnapshot selectedTrack (final boolean recordArmed, final boolean muted, final boolean soloed)
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
            muted,
            soloed,
            false,
            true,
            0.75,
            0.5,
            new RgbColor (100, 50, 25));
    }


    private static SelectedTrackSnapshot selectedTrack (final long generation, final String channelId, final int position, final boolean canHoldNotes, final boolean canHoldAudio)
    {
        return new SelectedTrackSnapshot (generation, channelId, channelId, position, "INSTRUMENT", true, false, false, canHoldNotes, canHoldAudio, true, false, TrackMonitorMode.AUTO, false, false, false, false, 0.75, 0.5, new RgbColor (100, 50, 25));
    }


    private static DrumContextSnapshot drum (final SelectedTrackSnapshot selected)
    {
        return new DrumContextSnapshot (4, selected.generation (), selected.channelId (), "drum-device", true, true, 36, List.of ());
    }


    private static DrumContextSnapshot drum (final SelectedTrackSnapshot selected, final int firstPlayingVelocity)
    {
        return new DrumContextSnapshot (4, selected.generation (), selected.channelId (), "drum-device", true, true, 36, drumPads (firstPlayingVelocity));
    }


    private static List<DrumPadSnapshot> drumPads (final int firstPlayingVelocity)
    {
        final List<DrumPadSnapshot> pads = new ArrayList<> ();
        for (int index = 0; index < 16; index++)
            pads.add (new DrumPadSnapshot (index, 36 + index, "pad-" + index, index != 1, "Pad " + index, new RgbColor (10, 20, 30), true, true, false, false, false, 0.75, 0.5, index == 0 ? firstPlayingVelocity : 0));
        return List.copyOf (pads);
    }


    private static ControllerBridgeSnapshot noteBridge (final String view, final SelectedTrackSnapshot selected, final NoteViewSnapshot noteView, final DrumContextSnapshot drum, final NoteRepeatSnapshot noteRepeat)
    {
        return noteBridge (1, view, selected, noteView, drum, noteRepeat);
    }


    private static ControllerBridgeSnapshot noteBridge (final long layoutGeneration, final String view, final SelectedTrackSnapshot selected, final NoteViewSnapshot noteView, final DrumContextSnapshot drum, final NoteRepeatSnapshot noteRepeat)
    {
        return noteBridge (layoutGeneration, view, "TRACK", selected, noteView, drum, noteRepeat);
    }


    private static ControllerBridgeSnapshot noteBridge (final long layoutGeneration, final String view, final String mode, final SelectedTrackSnapshot selected, final NoteViewSnapshot noteView, final DrumContextSnapshot drum, final NoteRepeatSnapshot noteRepeat)
    {
        return new ControllerBridgeSnapshot (
            TransportSnapshot.empty (),
            selected,
            new ControllerLayoutSnapshot (layoutGeneration, view, mode, "DRUM_PAD".equals (view), "DRUM_PAD".equals (view), 36, GridPressureConfiguration.OFF),
            noteView,
            noteRepeat,
            drum,
            ParameterBridgeSnapshot.empty (),
            MasterSnapshot.empty (),
            ProjectSnapshot.empty ());
    }


    private static ControllerBridgeSnapshot workspaceDrumBridge (final SelectedTrackSnapshot selected, final NoteViewSnapshot noteView, final DrumContextSnapshot drum, final NoteRepeatSnapshot noteRepeat)
    {
        return new ControllerBridgeSnapshot (
            TransportSnapshot.empty (),
            selected,
            new ControllerLayoutSnapshot (1, "WORKSPACE", "PROJECT", true, true, 36, GridPressureConfiguration.OFF),
            noteView,
            noteRepeat,
            drum,
            ParameterBridgeSnapshot.empty (),
            MasterSnapshot.empty (),
            ProjectSnapshot.empty ());
    }


    private static NoteRepeatSnapshot repeatReadback (final boolean rollEnabled, final DesiredNoteRepeat desired)
    {
        return new NoteRepeatSnapshot (true, rollEnabled, desired.active (), desired.mode (), desired.octaves (), desired.period (), desired.noteLength (), desired.latchActive (), desired.freeRunning (), desired.usePressure (), desired.shuffle ());
    }


    private static FakeCoreHost neutralizedJunoToDrumsHandoff ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final SelectedTrackSnapshot juno = selectedTrack (8, "juno", 5, true, false);
        final NoteViewSnapshot junoPreference = new NoteViewSnapshot (8, "juno", 5, ControllerNoteView.PLAY, false);
        host.bridge (noteBridge ("PLAY", juno, junoPreference, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));

        final SelectedTrackSnapshot drums = selectedTrack (9, "drums", 0, true, false);
        host.bridge (noteBridge (2, "SESSION", drums, junoPreference, DrumContextSnapshot.empty (), NoteRepeatSnapshot.empty ()));
        assertEquals (DesiredNotePerformance.inactive (), host.effects ().desiredNotePerformance ());
        return host;
    }


    private static TransportSnapshot transport (final boolean launcherOverdub)
    {
        return transport (true, true, launcherOverdub);
    }


    private static TransportSnapshot transport (final boolean engineActive, final boolean playing, final boolean launcherOverdub)
    {
        return new TransportSnapshot (true, engineActive, playing, false, false, launcherOverdub, false, false, false, 120, 0, 4, 4);
    }


    private static ControllerBridgeSnapshot projectBridge (final String identity, final boolean engineActive, final boolean playing, final boolean canPrevious, final boolean canNext, final boolean pending, final SelectedTrackSnapshot selectedTrack)
    {
        return projectBridge (identity, engineActive, playing, canPrevious, canNext, pending, selectedTrack, false);
    }


    private static ControllerBridgeSnapshot projectBridge (final String identity, final boolean engineActive, final boolean playing, final boolean canPrevious, final boolean canNext, final boolean pending, final SelectedTrackSnapshot selectedTrack, final boolean launcherOverdub)
    {
        return new ControllerBridgeSnapshot (
            transport (engineActive, playing, launcherOverdub),
            selectedTrack,
            ControllerLayoutSnapshot.empty (),
            de.mossgrabers.pull.core.api.DrumContextSnapshot.empty (),
            ParameterBridgeSnapshot.empty (),
            MasterSnapshot.empty (),
            new ProjectSnapshot (true, identity, identity, engineActive, canPrevious, canNext, pending));
    }


    private static ControllerBridgeSnapshot bridgeWithPressure (final GridPressureConfiguration pressure, final int drumBaseMidiNote)
    {
        return new ControllerBridgeSnapshot (
            TransportSnapshot.empty (),
            SelectedTrackSnapshot.empty (),
            new ControllerLayoutSnapshot (1, "WORKSPACE", "PROJECT", true, true, drumBaseMidiNote, pressure),
            de.mossgrabers.pull.core.api.DrumContextSnapshot.empty (),
            de.mossgrabers.pull.core.api.ParameterBridgeSnapshot.empty ());
    }


    private static ControllerBridgeSnapshot controllerMappingFeedbackBridge (final boolean on)
    {
        return controllerMappingFeedbackBridge (on ? 0 : -1);
    }


    private static ControllerBridgeSnapshot controllerMappingUnavailableBridge ()
    {
        return new ControllerBridgeSnapshot (
            TransportSnapshot.empty (),
            SelectedTrackSnapshot.empty (),
            new ControllerLayoutSnapshot (1, "DRUM_PAD", "TRACK", true, true, 36, GridPressureConfiguration.OFF),
            NoteViewSnapshot.empty (),
            NoteRepeatSnapshot.empty (),
            DrumContextSnapshot.empty (),
            ParameterBridgeSnapshot.empty (),
            ControllerMappingFeedbackSnapshot.empty (),
            MasterSnapshot.empty (),
            ProjectSnapshot.empty ());
    }


    private static ControllerBridgeSnapshot controllerMappingFeedbackBridge (final int onSlot)
    {
        final Map<de.mossgrabers.pull.core.api.ControllerMappingId, Boolean> states = new java.util.LinkedHashMap<> ();
        CoreControllerMappings.DRUM_CONTROL_PADS.forEach (mappingId -> states.put (mappingId, Boolean.FALSE));
        if (onSlot >= 0)
            states.put (CoreControllerMappings.DRUM_CONTROL_PADS.get (onSlot), Boolean.TRUE);
        return new ControllerBridgeSnapshot (
            TransportSnapshot.empty (),
            SelectedTrackSnapshot.empty (),
            new ControllerLayoutSnapshot (1, "DRUM_PAD", "TRACK", true, true, 36, GridPressureConfiguration.OFF),
            NoteViewSnapshot.empty (),
            NoteRepeatSnapshot.empty (),
            DrumContextSnapshot.empty (),
            ParameterBridgeSnapshot.empty (),
            new ControllerMappingFeedbackSnapshot (true, states),
            MasterSnapshot.empty (),
            ProjectSnapshot.empty ());
    }


    private static ControllerBridgeSnapshot layoutBridge (final String view, final String mode)
    {
        return layoutBridge (1, view, mode);
    }


    private static ControllerBridgeSnapshot layoutBridge (final long layoutGeneration, final String view, final String mode)
    {
        return new ControllerBridgeSnapshot (
            TransportSnapshot.empty (),
            SelectedTrackSnapshot.empty (),
            new ControllerLayoutSnapshot (layoutGeneration, view, mode, false, false, 0, GridPressureConfiguration.OFF),
            de.mossgrabers.pull.core.api.DrumContextSnapshot.empty (),
            ParameterBridgeSnapshot.empty ());
    }


    private static ControllerBridgeSnapshot sessionBridge (final long layoutGeneration, final String view, final String mode, final SessionBankShape shape)
    {
        final SelectedTrackSnapshot selected = selectedTrack (false);
        final List<SessionTrackSnapshot> tracks = new ArrayList<> ();
        tracks.add (new SessionTrackSnapshot (selected.channelId (), selected.position (), selected.name (), true, true, selected.activated (), selected.recordArmed (), selected.muted (), selected.soloed (), selected.clipPlaying (), selected.color ()));
        while (tracks.size () < shape.tracks ())
            tracks.add (SessionTrackSnapshot.empty ());
        return new ControllerBridgeSnapshot (
            TransportSnapshot.empty (),
            selected,
            new SessionBankSnapshot (12, shape, 0, 0, tracks),
            new ControllerLayoutSnapshot (layoutGeneration, view, mode, false, false, 0, GridPressureConfiguration.OFF),
            NoteViewSnapshot.empty (),
            NoteRepeatSnapshot.empty (),
            DrumContextSnapshot.empty (),
            ParameterBridgeSnapshot.empty (),
            MasterSnapshot.empty (),
            ProjectSnapshot.empty ());
    }


    private static ControllerBridgeSnapshot sessionMixBridge (final Map<ParameterSlot, ParameterTargetSnapshot> parameters)
    {
        final ControllerBridgeSnapshot base = sessionBridge (3, "DRUM_PAD", "TRACK", VsLiveWorkspace.SESSION_BANK);
        return new ControllerBridgeSnapshot (
            base.transport (),
            base.selectedTrack (),
            base.sessionBank (),
            base.layout (),
            base.noteView (),
            base.noteRepeat (),
            base.drum (),
            new ParameterBridgeSnapshot (parameters, Map.of ()),
            base.master (),
            base.project ());
    }


    private static ControllerBridgeSnapshot trackSelectionBridge (final long layoutGeneration, final boolean secondSelected)
    {
        return trackSelectionBridge (layoutGeneration, secondSelected, "WORKSPACE");
    }


    private static ControllerBridgeSnapshot trackSelectionBridge (final long layoutGeneration, final boolean secondSelected, final String mode)
    {
        final SessionBankShape shape = VsLiveWorkspace.SESSION_BANK;
        final List<SessionTrackSnapshot> tracks = new ArrayList<> ();
        tracks.add (new SessionTrackSnapshot ("track-7", 3, "Drums", true, !secondSelected, true, false, false, false, true, new RgbColor (100, 50, 25)));
        tracks.add (new SessionTrackSnapshot ("track-8", 4, "Bass", true, secondSelected, true, false, false, false, false, new RgbColor (25, 50, 100)));
        while (tracks.size () < shape.tracks ())
            tracks.add (SessionTrackSnapshot.empty ());
        return new ControllerBridgeSnapshot (
            TransportSnapshot.empty (),
            secondSelected ? selectedTrack (8, "track-8", 4, true, false) : selectedTrack (false),
            new SessionBankSnapshot (12, shape, 0, 0, tracks),
            new ControllerLayoutSnapshot (layoutGeneration, "DRUM_PAD", mode, false, false, 0, GridPressureConfiguration.OFF),
            NoteViewSnapshot.empty (),
            NoteRepeatSnapshot.empty (),
            DrumContextSnapshot.empty (),
            ParameterBridgeSnapshot.empty (),
            MasterSnapshot.empty (),
            ProjectSnapshot.empty ());
    }


    private static boolean hasFooterSelection (final FakeCoreHost host, final int index, final RgbColor color)
    {
        return host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command ->
            command instanceof final DisplayCommand.Rectangle rectangle &&
                rectangle.x () == index * 120 && rectangle.y () == 143 &&
                rectangle.width () == 120 && rectangle.height () == 17 &&
                color.equals (rectangle.color ()));
    }


    private static void assertMixSelectedWithoutInputOutputFallback (final FakeCoreHost host)
    {
        assertTrue (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command ->
            command instanceof final DisplayCommand.TextBox text && "Mix".equals (text.text ()) && OFF.equals (text.color ())));
        assertFalse (host.effects ().desiredOutput ().display ().commands ().stream ().anyMatch (command ->
            command instanceof final DisplayCommand.TextAt text && ("Track Type".equals (text.text ()) || "Monitor".equals (text.text ()))));
    }


    private static ControllerActionIntent switchParameterContext ()
    {
        return new ControllerActionIntent (ControllerActionId.SWITCH_PARAMETER_CONTEXT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS));
    }


    private static ControllerBridgeSnapshot masterBridge (final boolean canPrevious, final boolean canNext, final boolean pending)
    {
        return masterBridge (canPrevious, canNext, pending, true);
    }


    private static ControllerBridgeSnapshot masterBridge (final boolean canPrevious, final boolean canNext, final boolean pending, final boolean engineActive)
    {
        return masterBridge (canPrevious, canNext, pending, engineActive, 120);
    }


    private static ControllerBridgeSnapshot masterBridge (final boolean canPrevious, final boolean canNext, final boolean pending, final boolean engineActive, final double tempo)
    {
        final Map<ParameterSlot, ParameterTargetSnapshot> slots = Map.of (
            ParameterSlot.TEMPO, parameter (ParameterSlot.TEMPO, "Tempo", tempo, String.format ("%.2f BPM", Double.valueOf (tempo))),
            ParameterSlot.MASTER_VOLUME, parameter (ParameterSlot.MASTER_VOLUME, "Master Volume", 84, "-6.0 dB"),
            ParameterSlot.MASTER_MIX_VOLUME, parameter (ParameterSlot.MASTER_MIX_VOLUME, "Master Volume", 96, "-3.0 dB"),
            ParameterSlot.MASTER_MIX_PAN, parameter (ParameterSlot.MASTER_MIX_PAN, "Pan", 64, "-8.2 %"),
            ParameterSlot.CUE_VOLUME, parameter (ParameterSlot.CUE_VOLUME, "Cue Level", 80, "-9.0 dB"),
            ParameterSlot.CUE_MIX, parameter (ParameterSlot.CUE_MIX, "Cue Mix", 42, "33 %"));
        return new ControllerBridgeSnapshot (
            transport (engineActive, false, false),
            SelectedTrackSnapshot.empty (),
            new ControllerLayoutSnapshot (1, "PLAY", "MASTER", false, false, 0, GridPressureConfiguration.OFF),
            de.mossgrabers.pull.core.api.DrumContextSnapshot.empty (),
            new ParameterBridgeSnapshot (slots, Map.of ()),
            new MasterSnapshot (true, "project-a", "second_test", engineActive, canPrevious, canNext, pending, true, "Master", new RgbColor (10, 80, 140), true, true, false, 64, 48),
            new ProjectSnapshot (true, "project-a", "second_test", engineActive, canPrevious, canNext, pending));
    }


    private static ParameterTargetSnapshot parameter (final ParameterSlot slot, final String name, final double value, final String displayedValue)
    {
        return new ParameterTargetSnapshot (parameterTarget (slot), name, value, value, displayedValue, -1, 0.5);
    }


    private static ParameterTargetRef parameterTarget (final ParameterSlot slot)
    {
        return new ParameterTargetRef (ParameterTargetKind.FIXED, slot.bank ().name ().toLowerCase () + "-" + slot.index (), 0);
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
            ControllerViewFacet.SESSION_NAVIGATION,
            ControllerViewFacet.SESSION_CLIP_GRID_UPPER,
            ControllerViewFacet.SESSION_SCENE_KEYS_UPPER,
            ControllerViewFacet.DRUM_CONTROLLER_LOWER,
            ControllerViewFacet.DRUM_PITCH_BEND), workspace.facets ());
    }


    private static void assertStableSessionDestination (final DesiredControllerWorkspace workspace)
    {
        assertEquals ("Session destination", workspace.name ());
        assertEquals (StableDestinationWorkspace.SESSION_BANK, workspace.sessionBankShape ());
        assertEquals (Set.of (ControllerViewFacet.TRACK_MIXER_PAGE, ControllerViewFacet.SESSION_GRID_FULL), workspace.facets ());
    }


    private static void assertSelectedSession (final DesiredControllerWorkspace workspace)
    {
        assertEquals ("Session", workspace.name ());
        assertEquals (StableDestinationWorkspace.SESSION_BANK, workspace.sessionBankShape ());
        assertEquals (Set.of (ControllerViewFacet.SESSION_GRID_FULL), workspace.facets ());
    }


    private static FakeCoreHost host (final ClipCatalogSnapshot clips)
    {
        final PullCoreProvider provider = new PullCoreProvider ();
        final ShellCapabilities capabilities = provider.descriptor ().requiredCapabilities ();
        return new FakeCoreHost (provider.create (), capabilities, clips, Map.of (), Set.of ());
    }


    private static void startFillCore (final FakeCoreHost host)
    {
        host.initialBridge (fillLayoutBridge (true));
        host.start (Optional.empty ());
    }


    private static ControllerBridgeSnapshot fillLayoutBridge (final boolean active)
    {
        return fillLayoutBridge (1, active);
    }


    private static ControllerBridgeSnapshot fillLayoutBridge (final long generation, final boolean active)
    {
        return new ControllerBridgeSnapshot (
            TransportSnapshot.empty (),
            SelectedTrackSnapshot.empty (),
            new ControllerLayoutSnapshot (generation, active ? "DRUM_PAD" : "PLAY", "TRACK", active, active, 36, GridPressureConfiguration.OFF),
            DrumContextSnapshot.empty (),
            ParameterBridgeSnapshot.empty ());
    }


    private static Map<PadGridPosition, RgbColor> nonBlackPads (final ControllerPadGridOverlay overlay)
    {
        final Map<PadGridPosition, RgbColor> colors = new LinkedHashMap<> ();
        for (final Map.Entry<PadGridPosition, RgbColor> entry: overlay.colors ().entrySet ())
        {
            if (!OFF.equals (entry.getValue ()))
                colors.put (entry.getKey (), entry.getValue ());
        }
        return Map.copyOf (colors);
    }


    private static RgbColor light (final FakeCoreHost host, final ControlId control)
    {
        final int fillIndex = CoreControls.DRUM_FILLS.indexOf (control);
        final ControlId physicalControl = fillIndex < 0 ? control : FILL_LIGHTS.get (fillIndex);
        return host.effects ().desiredOutput ().lights ().get (physicalControl);
    }
}
