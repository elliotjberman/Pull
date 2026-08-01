// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.CatalogClip;
import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.effect.ClipLaunchMode;
import de.mossgrabers.pull.core.api.effect.ClipLaunchPolicy;
import de.mossgrabers.pull.core.api.effect.ClipLaunchQuantization;
import de.mossgrabers.pull.core.api.effect.ClipReleaseTrigger;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
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
    private static final RgbColor AVAILABLE = new RgbColor (127, 0, 0);
    private static final RgbColor HELD = new RgbColor (255, 0, 0);
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
        assertEquals (HELD, light (host, CoreControls.DRUM_FILL_2));

        host.button (CoreControls.DRUM_FILL_2, false);

        assertTrue (host.effects ().clipLease (CoreControls.DRUM_FILL_2).isEmpty ());
        assertInstanceOf (ReleaseClipTargetsEffect.class, host.effects ().executionOrder ().getLast ());
        assertEquals (AVAILABLE, light (host, CoreControls.DRUM_FILL_2));
    }


    @Test
    void lastReadyFillPressReleasesThePreviousFillBeforeLaunching ()
    {
        final ClipTargetId first = new ClipTargetId (1);
        final ClipTargetId second = new ClipTargetId (2);
        final FakeCoreHost host = host (new ClipCatalogSnapshot (42, List.of (
            new CatalogClip (first, "fill one"),
            new CatalogClip (second, "fill two"))));
        host.start (Optional.empty ());
        host.armedClipTargets (host.effects ().desiredClipBindings ());

        host.button (CoreControls.DRUM_FILL_1, true);
        host.button (CoreControls.DRUM_FILL_2, true);

        final List<CoreEffect> effects = host.effects ().executionOrder ();
        assertEquals (3, effects.size ());
        assertEquals (new ReleaseClipTargetsEffect (CoreControls.DRUM_FILL_1), effects.get (1));
        assertEquals (new PressClipTargetEffect (CoreControls.DRUM_FILL_2, 42, second, FILL_POLICY), effects.get (2));
        assertTrue (host.effects ().clipLease (CoreControls.DRUM_FILL_1).isEmpty ());
        assertEquals (second, host.effects ().clipLease (CoreControls.DRUM_FILL_2).orElseThrow ().target ());

        host.button (CoreControls.DRUM_FILL_1, false);
        assertEquals (second, host.effects ().clipLease (CoreControls.DRUM_FILL_2).orElseThrow ().target ());

        host.button (CoreControls.DRUM_FILL_2, false);
        assertTrue (host.effects ().clipLease (CoreControls.DRUM_FILL_2).isEmpty ());
    }


    @Test
    void aNewPressAfterReloadReleasesEveryOlderHeldOwner ()
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

        assertEquals (List.of (
            new ReleaseClipTargetsEffect (CoreControls.DRUM_FILL_1),
            new ReleaseClipTargetsEffect (CoreControls.DRUM_FILL_2),
            new PressClipTargetEffect (CoreControls.DRUM_FILL_3, 43, third, FILL_POLICY)), host.effects ().executionOrder ());
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
        final FakeCoreHost host = new FakeCoreHost (provider.create (), provider.descriptor ().requiredCapabilities (), catalog, armed, armed.keySet ());

        host.start (Optional.empty ());

        assertTrue (host.effects ().executionOrder ().isEmpty ());
        assertTrue (host.effects ().clipLease (CoreControls.DRUM_FILL_1).isEmpty ());
        assertTrue (host.effects ().clipLease (CoreControls.DRUM_FILL_2).isEmpty ());
        assertEquals (HELD, light (host, CoreControls.DRUM_FILL_1));
        assertEquals (HELD, light (host, CoreControls.DRUM_FILL_2));
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

        assertEquals (inserted, host.effects ().desiredClipBindings ().get (CoreControls.DRUM_FILL_1));
        assertEquals (original, host.effects ().desiredClipBindings ().get (CoreControls.DRUM_FILL_2));
        assertInstanceOf (ReleaseClipTargetsEffect.class, host.effects ().executionOrder ().getLast ());
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
        assertEquals (HELD, light (host, CoreControls.DRUM_FILL_1));
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
