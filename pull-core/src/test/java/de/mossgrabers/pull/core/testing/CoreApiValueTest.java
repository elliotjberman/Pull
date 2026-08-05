// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.CatalogClip;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.CoreApi;
import de.mossgrabers.pull.core.api.CoreCapabilities;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.DesiredInputRoutes;
import de.mossgrabers.pull.core.api.GridPressureConfiguration;
import de.mossgrabers.pull.core.api.InputRoute;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.TimerId;
import de.mossgrabers.pull.core.api.effect.ClipLaunchMode;
import de.mossgrabers.pull.core.api.effect.ClipLaunchPolicy;
import de.mossgrabers.pull.core.api.effect.ClipLaunchQuantization;
import de.mossgrabers.pull.core.api.effect.ClipReleaseTrigger;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
import de.mossgrabers.pull.core.api.effect.ScheduleTimerEffect;
import de.mossgrabers.pull.core.api.effect.SendNoteInputMidiEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;
import de.mossgrabers.pull.core.api.output.RgbColor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Value, validation, and immutability tests for the parent-loaded API.
 */
class CoreApiValueTest
{
    private static final ClipLaunchPolicy LAUNCH_POLICY = new ClipLaunchPolicy (
        ClipLaunchQuantization.IMMEDIATE,
        ClipLaunchMode.LEGATO_FROM_CLIP_OR_PROJECT,
        ClipReleaseTrigger.ALTERNATE);

    @Test
    void stateEnvelopeCopiesBytesAndUsesContentEquality ()
    {
        final byte [] bytes = new byte []
        {
            1,
            2,
            3
        };
        final StateEnvelope envelope = new StateEnvelope ("schema", 1, bytes);
        bytes[0] = 9;
        final byte [] returnedBytes = envelope.payload ();
        returnedBytes[1] = 9;

        assertEquals (new StateEnvelope ("schema", 1, new byte []
        {
            1,
            2,
            3
        }), envelope);
    }


    @Test
    void snapshotsCatalogsResultsEffectsAndOutputsCopyCollections ()
    {
        final ControlId control = new ControlId ("control");
        final Set<ControlId> pressed = new HashSet<> (Set.of (control));
        final CatalogClip clip = new CatalogClip (new ClipTargetId (1), "fill");
        final List<CatalogClip> clips = new ArrayList<> (List.of (clip));
        final ClipCatalogSnapshot catalog = new ClipCatalogSnapshot (7, clips);
        final Map<ControlId, ClipTargetId> armed = new HashMap<> (Map.of (control, clip.targetId ()));
        final Map<ControlId, ClipTargetId> session = new HashMap<> (armed);
        final ControllerSnapshot snapshot = new ControllerSnapshot (1, 2, ShellCapabilities.empty (), catalog, armed, session, Optional.of (control), pressed, Set.of ());
        pressed.clear ();
        armed.clear ();
        session.clear ();
        clips.clear ();

        final Map<ControlId, RgbColor> lights = new HashMap<> (Map.of (control, new RgbColor (1, 2, 3)));
        final DesiredHardwareOutput output = new DesiredHardwareOutput (lights);
        lights.clear ();

        final PressClipTargetEffect press = new PressClipTargetEffect (control, catalog.generation (), clip.targetId (), LAUNCH_POLICY);
        final List<CoreEffect> effects = new ArrayList<> (List.of (press));
        final Map<ControlId, ClipTargetId> desiredBindings = new HashMap<> (Map.of (control, clip.targetId ()));
        final Set<BridgeSubscription> bridgeDomains = new HashSet<> (Set.of (BridgeSubscription.SELECTED_TRACK));
        final DesiredBridgeSubscriptions bridgeSubscriptions = new DesiredBridgeSubscriptions (bridgeDomains);
        final CoreResult result = new CoreResult (output, DesiredInputRoutes.empty (), bridgeSubscriptions, desiredBindings, effects);
        desiredBindings.clear ();
        bridgeDomains.clear ();
        effects.clear ();

        assertTrue (snapshot.pressedControls ().contains (control));
        assertEquals (Optional.of (control), snapshot.activeClipLaunchOwner ());
        assertEquals (List.of (clip), snapshot.clipCatalog ().clips ());
        assertEquals (clip.targetId (), snapshot.armedClipTargets ().get (control));
        assertEquals (clip.targetId (), snapshot.clipLaunchSessionTargets ().get (control));
        assertEquals (clip.targetId (), press.target ());
        assertEquals (LAUNCH_POLICY, press.launchPolicy ());
        assertEquals (new RgbColor (1, 2, 3), result.desiredOutput ().lights ().get (control));
        assertEquals (Set.of (BridgeSubscription.SELECTED_TRACK), result.desiredBridgeSubscriptions ().domains ());
        assertEquals (clip.targetId (), result.desiredClipBindings ().get (control));
        assertEquals (1, result.effects ().size ());
        assertThrows (UnsupportedOperationException.class, () -> snapshot.pressedControls ().clear ());
        assertThrows (UnsupportedOperationException.class, () -> snapshot.clipCatalog ().clips ().clear ());
        assertThrows (UnsupportedOperationException.class, () -> snapshot.armedClipTargets ().clear ());
        assertThrows (UnsupportedOperationException.class, () -> snapshot.clipLaunchSessionTargets ().clear ());
        assertThrows (UnsupportedOperationException.class, () -> result.desiredClipBindings ().clear ());
        assertThrows (UnsupportedOperationException.class, () -> result.desiredBridgeSubscriptions ().domains ().clear ());
        assertThrows (UnsupportedOperationException.class, () -> result.effects ().clear ());
    }


    @Test
    void versionedCapabilitiesRequireEqualOrNewerImplementations ()
    {
        final ShellCapabilities available = new ShellCapabilities (Map.of ("lights", Integer.valueOf (2), "timers", Integer.valueOf (1)));

        assertTrue (available.supports (new ShellCapabilities (Map.of ("lights", Integer.valueOf (1)))));
        assertTrue (available.supports (new ShellCapabilities (Map.of ("lights", Integer.valueOf (2)))));
        assertFalse (available.supports (new ShellCapabilities (Map.of ("lights", Integer.valueOf (3)))));
        assertFalse (available.supports (new ShellCapabilities (Map.of ("clips", Integer.valueOf (1)))));
    }


    @Test
    void publishesStableVersionCapabilityAndControlIdentifiers ()
    {
        assertEquals (11, CoreApi.VERSION);
        assertEquals ("input.drum-fill", CoreCapabilities.INPUT_DRUM_FILL);
        assertEquals ("snapshot.selected-track-clips", CoreCapabilities.SNAPSHOT_SELECTED_TRACK_CLIPS);
        assertEquals ("binding.clip-target", CoreCapabilities.BINDING_CLIP_TARGET);
        assertEquals ("snapshot.clip-launch-session", CoreCapabilities.SNAPSHOT_CLIP_LAUNCH_SESSION);
        assertEquals ("effect.clip-launch-hold", CoreCapabilities.EFFECT_CLIP_LAUNCH_HOLD);
        assertEquals ("output.rgb-light", CoreCapabilities.OUTPUT_RGB_LIGHT);
        assertEquals ("output.controller-workspace", CoreCapabilities.OUTPUT_CONTROLLER_WORKSPACE);
        assertEquals ("input.controller", CoreCapabilities.INPUT_CONTROLLER);
        assertEquals ("routing.controller-input", CoreCapabilities.ROUTING_CONTROLLER_INPUT);
        assertEquals ("snapshot.controller-bridge", CoreCapabilities.SNAPSHOT_CONTROLLER_BRIDGE);
        assertEquals ("subscription.controller-bridge", CoreCapabilities.SUBSCRIPTION_CONTROLLER_BRIDGE);
        assertEquals ("effect.transport", CoreCapabilities.EFFECT_TRANSPORT);
        assertEquals ("effect.selected-track", CoreCapabilities.EFFECT_SELECTED_TRACK);
        assertEquals ("effect.drum-pad", CoreCapabilities.EFFECT_DRUM_PAD);
        assertEquals ("effect.note-input-midi", CoreCapabilities.EFFECT_NOTE_INPUT_MIDI);
        assertEquals (12, CoreControls.DRUM_FILLS.size ());
        assertEquals (12, new HashSet<> (CoreControls.DRUM_FILLS).size ());
        for (int index = 0; index < CoreControls.DRUM_FILLS.size (); index++)
            assertEquals (new ControlId ("drum.fill." + (index + 1)), CoreControls.DRUM_FILLS.get (index));
        assertEquals (CoreControls.DRUM_FILLS, CoreControls.drumFills ());
        assertThrows (UnsupportedOperationException.class, () -> CoreControls.DRUM_FILLS.clear ());
    }


    @Test
    void pressureConfigurationAndMidiEffectsAreTypedAndBounded ()
    {
        assertEquals (GridPressureConfiguration.Mode.POLY_AFTERTOUCH, GridPressureConfiguration.POLY.mode ());
        assertEquals (74, GridPressureConfiguration.controlChange (74).controller ());
        assertThrows (IllegalArgumentException.class, () -> GridPressureConfiguration.controlChange (128));
        assertThrows (IllegalArgumentException.class, () -> new GridPressureConfiguration (GridPressureConfiguration.Mode.CHANNEL_AFTERTOUCH, 1));

        assertEquals (0xA0, new SendNoteInputMidiEffect (0xA0, 60, 91).status ());
        assertThrows (IllegalArgumentException.class, () -> new SendNoteInputMidiEffect (0x90, 60, 91));
    }


    @Test
    void controllerWorkspacesAreCompleteImmutableValues ()
    {
        final Set<ControllerViewFacet> facets = new HashSet<> (Set.of (ControllerViewFacet.PROJECT_MACRO_CONTROLS));
        final DesiredControllerWorkspace workspace = new DesiredControllerWorkspace ("  live  ", facets);
        facets.clear ();

        assertEquals ("live", workspace.name ());
        assertEquals (Set.of (ControllerViewFacet.PROJECT_MACRO_CONTROLS), workspace.facets ());
        assertTrue (workspace.isActive ());
        assertFalse (DesiredControllerWorkspace.empty ().isActive ());
        assertThrows (UnsupportedOperationException.class, () -> workspace.facets ().clear ());
        assertThrows (IllegalArgumentException.class, () -> new DesiredControllerWorkspace ("named", Set.of ()));
        assertThrows (IllegalArgumentException.class, () -> new DesiredControllerWorkspace ("", Set.of (ControllerViewFacet.PROJECT_MACRO_CONTROLS)));
    }


    @Test
    void inputRoutesRejectDuplicateKeysAndExposeObserveAndExclusiveModes ()
    {
        final ControlId play = PushControlIds.button ("PLAY");
        final ControlId stop = PushControlIds.button ("STOP");
        final DesiredInputRoutes routes = new DesiredInputRoutes (Set.of (
            new InputRoute (play, InputKind.BUTTON, InputRouteMode.OBSERVE),
            new InputRoute (stop, InputKind.BUTTON, InputRouteMode.EXCLUSIVE)));

        assertEquals (Optional.of (InputRouteMode.OBSERVE), routes.mode (play, InputKind.BUTTON));
        assertTrue (routes.observes (play, InputKind.BUTTON));
        assertFalse (routes.ownsExclusively (play, InputKind.BUTTON));
        assertEquals (Optional.of (InputRouteMode.EXCLUSIVE), routes.mode (stop, InputKind.BUTTON));
        assertTrue (routes.observes (stop, InputKind.BUTTON));
        assertTrue (routes.ownsExclusively (stop, InputKind.BUTTON));
        assertEquals (Optional.empty (), routes.mode (stop, InputKind.TOUCH));
        assertFalse (routes.observes (stop, InputKind.TOUCH));

        assertThrows (IllegalArgumentException.class, () -> new DesiredInputRoutes (Set.of (
            new InputRoute (play, InputKind.BUTTON, InputRouteMode.OBSERVE),
            new InputRoute (play, InputKind.BUTTON, InputRouteMode.EXCLUSIVE))));
    }


    @Test
    void controllerInputAcceptsExactCoalescedRelativeDeltasAndRejectsInvalidShapes ()
    {
        final ControlId encoder = PushControlIds.continuous ("KNOB1");

        assertEquals (384, new ControllerInputEvent (1, 2, encoder, InputKind.RELATIVE, InputPhase.UPDATE, 384).value ());
        assertEquals (-257, new ControllerInputEvent (2, 3, encoder, InputKind.RELATIVE, InputPhase.UPDATE, -257).value ());
        assertThrows (IllegalArgumentException.class, () -> new ControllerInputEvent (3, 4, encoder, InputKind.RELATIVE, InputPhase.UPDATE, 0));
        assertThrows (IllegalArgumentException.class, () -> new ControllerInputEvent (3, 4, encoder, InputKind.RELATIVE, InputPhase.BEGIN, 1));
        assertThrows (IllegalArgumentException.class, () -> new ControllerInputEvent (3, 4, encoder, InputKind.ABSOLUTE, InputPhase.UPDATE, 16384));
        assertThrows (IllegalArgumentException.class, () -> new ControllerInputEvent (3, 4, encoder, InputKind.BUTTON, InputPhase.END, 1));
    }


    @Test
    void clipEffectsEnforceOwnerGenerationAndTarget ()
    {
        final ControlId owner = new ControlId ("owner");
        final ClipTargetId target = new ClipTargetId (1);

        assertEquals (owner, new ReleaseClipTargetsEffect (owner).owner ());
        assertThrows (NullPointerException.class, () -> new ReleaseClipTargetsEffect (null));
        assertThrows (NullPointerException.class, () -> new PressClipTargetEffect (null, 0, target, LAUNCH_POLICY));
        assertThrows (IllegalArgumentException.class, () -> new PressClipTargetEffect (owner, -1, target, LAUNCH_POLICY));
        assertThrows (NullPointerException.class, () -> new PressClipTargetEffect (owner, 0, null, LAUNCH_POLICY));
        assertThrows (NullPointerException.class, () -> new PressClipTargetEffect (owner, 0, target, null));
        assertThrows (NullPointerException.class, () -> new ClipLaunchPolicy (null, ClipLaunchMode.DEFAULT, ClipReleaseTrigger.MAIN));
        assertThrows (NullPointerException.class, () -> new ClipLaunchPolicy (ClipLaunchQuantization.DEFAULT, null, ClipReleaseTrigger.MAIN));
        assertThrows (NullPointerException.class, () -> new ClipLaunchPolicy (ClipLaunchQuantization.DEFAULT, ClipLaunchMode.DEFAULT, null));
    }


    @Test
    void rejectsInvalidBoundaryValues ()
    {
        assertThrows (IllegalArgumentException.class, () -> new ControlId (" "));
        assertThrows (IllegalArgumentException.class, () -> new TimerId (""));
        assertThrows (IllegalArgumentException.class, () -> new ClipTargetId (-1));
        assertThrows (NullPointerException.class, () -> new CatalogClip (null, "clip"));
        assertThrows (NullPointerException.class, () -> new CatalogClip (new ClipTargetId (0), null));
        assertThrows (IllegalArgumentException.class, () -> new ClipCatalogSnapshot (-1, List.of ()));
        assertThrows (NullPointerException.class, () -> new ClipCatalogSnapshot (0, null));
        final ClipTargetId duplicateTarget = new ClipTargetId (0);
        assertThrows (IllegalArgumentException.class, () -> new ClipCatalogSnapshot (0, List.of (
            new CatalogClip (duplicateTarget, "first"),
            new CatalogClip (duplicateTarget, "second"))));
        assertThrows (IllegalArgumentException.class, () -> new ControllerSnapshot (-1, 0, ShellCapabilities.empty (), ClipCatalogSnapshot.empty (), Map.of (), Set.of (), Set.of ()));
        assertThrows (NullPointerException.class, () -> new ControllerSnapshot (0, 0, ShellCapabilities.empty (), null, Map.of (), Set.of (), Set.of ()));
        assertThrows (NullPointerException.class, () -> new ControllerSnapshot (0, 0, ShellCapabilities.empty (), ClipCatalogSnapshot.empty (), null, Set.of (), Set.of ()));
        assertThrows (NullPointerException.class, () -> new ControllerSnapshot (0, 0, ShellCapabilities.empty (), ClipCatalogSnapshot.empty (), Map.of (), null, Optional.empty (), Set.of (), Set.of ()));
        assertThrows (NullPointerException.class, () -> new ControllerSnapshot (0, 0, ShellCapabilities.empty (), ClipCatalogSnapshot.empty (), Map.of (), Map.of (), null, Set.of (), Set.of ()));
        assertThrows (IllegalArgumentException.class, () -> new ControllerSnapshot (0, 0, ShellCapabilities.empty (), ClipCatalogSnapshot.empty (), Map.of (), Map.of (), Optional.of (new ControlId ("owner")), Set.of (), Set.of ()));
        assertThrows (IllegalArgumentException.class, () -> new ControllerSnapshot (0, 0, ShellCapabilities.empty (), ClipCatalogSnapshot.empty (), Map.of (), Map.of (new ControlId ("one"), new ClipTargetId (1), new ControlId ("two"), new ClipTargetId (2)), Optional.empty (), Set.of (), Set.of ()));
        assertThrows (NullPointerException.class, () -> new CoreResult (DesiredHardwareOutput.empty (), Map.of (), null));
        assertThrows (IllegalArgumentException.class, () -> new SnapshotChangedEvent (-1, 0));
        assertThrows (IllegalArgumentException.class, () -> new SnapshotChangedEvent (0, -1));
        assertThrows (IllegalArgumentException.class, () -> new ShellCapabilities (Map.of ("lights", Integer.valueOf (0))));
        assertThrows (IllegalArgumentException.class, () -> new RgbColor (256, 0, 0));
        assertEquals (3, new ScheduleTimerEffect (new TimerId ("timer"), 3).deadlineNanos ());
    }
}
