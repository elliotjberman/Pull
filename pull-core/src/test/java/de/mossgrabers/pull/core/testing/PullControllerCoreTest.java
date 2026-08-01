// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.CatalogClip;
import de.mossgrabers.pull.core.api.CatalogParameter;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.ParameterCatalogSnapshot;
import de.mossgrabers.pull.core.api.ParameterTargetId;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.effect.ClipLaunchMode;
import de.mossgrabers.pull.core.api.effect.ClipLaunchPolicy;
import de.mossgrabers.pull.core.api.effect.ClipLaunchQuantization;
import de.mossgrabers.pull.core.api.effect.ClipReleaseTrigger;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterValueEffect;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.runtime.PullCoreProvider;

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
    void drumRibbonTargetsOnlyTheExactPullDrumPitchRemote ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        final ParameterTargetId wrongPage = new ParameterTargetId (1);
        final ParameterTargetId drumPitch = new ParameterTargetId (2);
        host.selectedTrackParameters (new ParameterCatalogSnapshot (7, List.of (
            new CatalogParameter (wrongPage, "Other", "Drum Pitch", 0.1),
            new CatalogParameter (drumPitch, "Pull", "Drum Pitch", 0.5))));

        host.absolute (CoreControls.DRUM_PITCH_RIBBON, 0.75);

        assertEquals (new SetParameterValueEffect (7, drumPitch, 0.75), host.effects ().executionOrder ().getLast ());
        assertEquals (Set.of (CoreControls.DRUM_PITCH_RIBBON), host.effects ().claimedInputs ());
        assertEquals (Double.valueOf (0.5), host.effects ().desiredOutput ().absoluteValues ().get (CoreControls.DRUM_PITCH_RIBBON));
    }


    @Test
    void duplicateExactDrumPitchRemotesAreAmbiguousAndUnclaimed ()
    {
        final FakeCoreHost host = host (ClipCatalogSnapshot.empty ());
        host.start (Optional.empty ());
        host.selectedTrackParameters (new ParameterCatalogSnapshot (8, List.of (
            new CatalogParameter (new ParameterTargetId (1), "Pull", "Drum Pitch", 0.25),
            new CatalogParameter (new ParameterTargetId (2), "Pull", "Drum Pitch", 0.75))));

        host.absolute (CoreControls.DRUM_PITCH_RIBBON, 0.5);

        assertTrue (host.effects ().executionOrder ().isEmpty ());
        assertTrue (host.effects ().claimedInputs ().isEmpty ());
        assertTrue (host.effects ().desiredOutput ().absoluteValues ().isEmpty ());
    }


    private static CatalogClip clip (final long target, final String name)
    {
        return new CatalogClip (new ClipTargetId (target), name);
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
