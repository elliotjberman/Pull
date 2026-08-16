// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.CatalogClip;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.effect.ClipLaunchMode;
import de.mossgrabers.pull.core.api.effect.ClipLaunchPolicy;
import de.mossgrabers.pull.core.api.effect.ClipLaunchQuantization;
import de.mossgrabers.pull.core.api.effect.ClipReleaseTrigger;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


/**
 * Selected-track drum-fill behavior for its fixed eight-pad region.
 */
public final class DrumFillView implements ControllerView
{
    private static final String           FILL_MARKER = "fill";
    private static final RgbColor         FILL_OFF = new RgbColor (0, 0, 0);
    private static final RgbColor         FILL_AVAILABLE = new RgbColor (167, 107, 34);
    private static final RgbColor         FILL_ACTIVE = new RgbColor (242, 126, 0);
    private static final ClipLaunchPolicy FILL_LAUNCH_POLICY = new ClipLaunchPolicy (
        ClipLaunchQuantization.IMMEDIATE,
        ClipLaunchMode.LEGATO_FROM_CLIP_OR_PROJECT,
        ClipReleaseTrigger.ALTERNATE);
    private static final Set<SurfaceClaim> CLAIMS = Set.of (
        new SurfaceClaim (SurfaceArea.DRUM_FILL_PADS, SurfaceClaim.Kind.DIRECT_INPUT),
        new SurfaceClaim (SurfaceArea.DRUM_FILL_PADS, SurfaceClaim.Kind.OUTPUT));
    private static final ViewProfile PROFILE = ViewProfile.fixed ("default", CLAIMS, Set.of ());

    private Set<ControlId>               previousPressedControls = Set.of ();
    private Map<ControlId, ClipTargetId> desiredBindings = Map.of ();


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "drum-fill";
    }


    /** {@inheritDoc} */
    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    /** {@inheritDoc} */
    @Override
    public void start (final ControllerSnapshot snapshot)
    {
        this.desiredBindings = desiredBindings (snapshot, snapshot.pressedControls ());
        this.previousPressedControls = snapshot.pressedControls ();
    }


    /** {@inheritDoc} */
    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        final Set<ControlId> continuingPresses = new LinkedHashSet<> (snapshot.pressedControls ());
        continuingPresses.retainAll (this.previousPressedControls);
        this.desiredBindings = desiredBindings (snapshot, continuingPresses);
        this.previousPressedControls = snapshot.pressedControls ();
    }


    /** {@inheritDoc} */
    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        if (!(event instanceof final ButtonInputEvent button) || !CoreControls.DRUM_FILLS.contains (button.controlId ()))
            return List.of ();

        if (button.pressed ())
            return pressEffects (snapshot, this.desiredBindings, button.controlId ());
        return List.of (new ReleaseClipTargetsEffect (button.controlId ()));
    }


    /** {@inheritDoc} */
    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        for (final ControlId control: CoreControls.DRUM_FILLS)
        {
            final RgbColor color;
            if (snapshot.activeClipLaunchOwner ().filter (control::equals).isPresent ())
                color = FILL_ACTIVE;
            else if (snapshot.clipLaunchSessionTargets ().containsKey (control))
                color = FILL_AVAILABLE;
            else if (!isReady (snapshot, this.desiredBindings, control))
                color = FILL_OFF;
            else
                color = FILL_AVAILABLE;
            lights.put (control, color);
        }
        return new ViewOutput (lights, this.desiredBindings);
    }


    private static Map<ControlId, ClipTargetId> desiredBindings (final ControllerSnapshot snapshot, final Set<ControlId> controlsToPreserve)
    {
        final Map<ControlId, ClipTargetId> bindings = canonicalBindings (snapshot);
        for (final ControlId control: CoreControls.DRUM_FILLS)
        {
            if (!controlsToPreserve.contains (control))
                continue;

            final ClipTargetId armedTarget = snapshot.armedClipTargets ().get (control);
            if (armedTarget == null)
                continue;

            bindings.entrySet ().removeIf (entry -> !control.equals (entry.getKey ()) && armedTarget.equals (entry.getValue ()));
            bindings.put (control, armedTarget);
        }

        final Set<ClipTargetId> catalogTargets = new LinkedHashSet<> ();
        snapshot.clipCatalog ().clips ().forEach (clip -> catalogTargets.add (clip.targetId ()));
        for (final Map.Entry<ControlId, ClipTargetId> retained: snapshot.clipLaunchSessionTargets ().entrySet ())
        {
            bindings.entrySet ().removeIf (entry -> !retained.getKey ().equals (entry.getKey ()) && retained.getValue ().equals (entry.getValue ()));
            if (catalogTargets.contains (retained.getValue ()))
                bindings.put (retained.getKey (), retained.getValue ());
        }
        return Map.copyOf (bindings);
    }


    private static Map<ControlId, ClipTargetId> canonicalBindings (final ControllerSnapshot snapshot)
    {
        final Map<ControlId, ClipTargetId> bindings = new LinkedHashMap<> ();
        int controlIndex = 0;
        for (final CatalogClip clip: snapshot.clipCatalog ().clips ())
        {
            if (!clip.name ().toLowerCase (Locale.ROOT).contains (FILL_MARKER))
                continue;

            bindings.put (CoreControls.DRUM_FILLS.get (controlIndex), clip.targetId ());
            controlIndex++;
            if (controlIndex == CoreControls.DRUM_FILLS.size ())
                break;
        }
        return bindings;
    }


    private static boolean isReady (final ControllerSnapshot snapshot, final Map<ControlId, ClipTargetId> desiredBindings, final ControlId control)
    {
        final ClipTargetId desiredTarget = desiredBindings.get (control);
        return desiredTarget != null && desiredTarget.equals (snapshot.armedClipTargets ().get (control));
    }


    private static List<CoreEffect> pressEffects (final ControllerSnapshot snapshot, final Map<ControlId, ClipTargetId> desiredBindings, final ControlId owner)
    {
        if (!isReady (snapshot, desiredBindings, owner))
            return List.of ();

        return List.of (new PressClipTargetEffect (owner, snapshot.clipCatalog ().generation (), desiredBindings.get (owner), FILL_LAUNCH_POLICY));
    }
}
