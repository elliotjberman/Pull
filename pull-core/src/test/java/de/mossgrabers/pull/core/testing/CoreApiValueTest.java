// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.CatalogClip;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreApi;
import de.mossgrabers.pull.core.api.CoreCapabilities;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.TimerId;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
import de.mossgrabers.pull.core.api.effect.ScheduleTimerEffect;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;
import de.mossgrabers.pull.core.api.output.RgbColor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
        final ControllerSnapshot snapshot = new ControllerSnapshot (1, 2, ShellCapabilities.empty (), catalog, armed, pressed, Set.of ());
        pressed.clear ();
        armed.clear ();
        clips.clear ();

        final Map<ControlId, RgbColor> lights = new HashMap<> (Map.of (control, new RgbColor (1, 2, 3)));
        final DesiredHardwareOutput output = new DesiredHardwareOutput (lights);
        lights.clear ();

        final PressClipTargetEffect press = new PressClipTargetEffect (control, catalog.generation (), clip.targetId ());
        final List<CoreEffect> effects = new ArrayList<> (List.of (press));
        final Map<ControlId, ClipTargetId> desiredBindings = new HashMap<> (Map.of (control, clip.targetId ()));
        final CoreResult result = new CoreResult (output, desiredBindings, effects);
        desiredBindings.clear ();
        effects.clear ();

        assertTrue (snapshot.pressedControls ().contains (control));
        assertEquals (List.of (clip), snapshot.clipCatalog ().clips ());
        assertEquals (clip.targetId (), snapshot.armedClipTargets ().get (control));
        assertEquals (clip.targetId (), press.target ());
        assertEquals (new RgbColor (1, 2, 3), result.desiredOutput ().lights ().get (control));
        assertEquals (clip.targetId (), result.desiredClipBindings ().get (control));
        assertEquals (1, result.effects ().size ());
        assertThrows (UnsupportedOperationException.class, () -> snapshot.pressedControls ().clear ());
        assertThrows (UnsupportedOperationException.class, () -> snapshot.clipCatalog ().clips ().clear ());
        assertThrows (UnsupportedOperationException.class, () -> snapshot.armedClipTargets ().clear ());
        assertThrows (UnsupportedOperationException.class, () -> result.desiredClipBindings ().clear ());
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
        assertEquals (3, CoreApi.VERSION);
        assertEquals ("input.drum-fill", CoreCapabilities.INPUT_DRUM_FILL);
        assertEquals ("snapshot.selected-track-clips", CoreCapabilities.SNAPSHOT_SELECTED_TRACK_CLIPS);
        assertEquals ("binding.clip-target", CoreCapabilities.BINDING_CLIP_TARGET);
        assertEquals ("effect.clip-launch-hold", CoreCapabilities.EFFECT_CLIP_LAUNCH_HOLD);
        assertEquals ("output.rgb-light", CoreCapabilities.OUTPUT_RGB_LIGHT);
        assertEquals (12, CoreControls.DRUM_FILLS.size ());
        assertEquals (12, new HashSet<> (CoreControls.DRUM_FILLS).size ());
        for (int index = 0; index < CoreControls.DRUM_FILLS.size (); index++)
            assertEquals (new ControlId ("drum.fill." + (index + 1)), CoreControls.DRUM_FILLS.get (index));
        assertEquals (CoreControls.DRUM_FILLS, CoreControls.drumFills ());
        assertThrows (UnsupportedOperationException.class, () -> CoreControls.DRUM_FILLS.clear ());
    }


    @Test
    void clipEffectsEnforceOwnerGenerationAndTarget ()
    {
        final ControlId owner = new ControlId ("owner");
        final ClipTargetId target = new ClipTargetId (1);

        assertEquals (owner, new ReleaseClipTargetsEffect (owner).owner ());
        assertThrows (NullPointerException.class, () -> new ReleaseClipTargetsEffect (null));
        assertThrows (NullPointerException.class, () -> new PressClipTargetEffect (null, 0, target));
        assertThrows (IllegalArgumentException.class, () -> new PressClipTargetEffect (owner, -1, target));
        assertThrows (NullPointerException.class, () -> new PressClipTargetEffect (owner, 0, null));
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
        assertThrows (IllegalArgumentException.class, () -> new SnapshotChangedEvent (-1, 0));
        assertThrows (IllegalArgumentException.class, () -> new SnapshotChangedEvent (0, -1));
        assertThrows (IllegalArgumentException.class, () -> new ShellCapabilities (Map.of ("lights", Integer.valueOf (0))));
        assertThrows (IllegalArgumentException.class, () -> new RgbColor (256, 0, 0));
        assertEquals (3, new ScheduleTimerEffect (new TimerId ("timer"), 3).deadlineNanos ());
    }
}
