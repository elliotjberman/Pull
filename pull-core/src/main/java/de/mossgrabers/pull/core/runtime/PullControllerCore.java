// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.CatalogClip;
import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Reloadable Pull behavior. The stable shell owns physical mappings and all effect execution.
 */
final class PullControllerCore implements ControllerCore
{
    private static final String FILL_MARKER = "fill";
    private static final RgbColor FILL_OFF = new RgbColor (0, 0, 0);
    private static final RgbColor FILL_AVAILABLE = new RgbColor (127, 0, 0);
    private static final RgbColor FILL_HELD = new RgbColor (255, 0, 0);

    private Lifecycle lifecycle = Lifecycle.NEW;
    private Set<ControlId> previousPressedControls = Set.of ();


    /** {@inheritDoc} */
    @Override
    public CoreResult start (final ControllerSnapshot snapshot, final Optional<StateEnvelope> previousState)
    {
        Objects.requireNonNull (snapshot, "snapshot");
        Objects.requireNonNull (previousState, "previousState");
        if (this.lifecycle != Lifecycle.NEW)
            throw new IllegalStateException ("Core can only be started once");

        this.lifecycle = Lifecycle.RUNNING;
        final Map<ControlId, ClipTargetId> desiredBindings = desiredBindings (snapshot, snapshot.pressedControls ());
        this.previousPressedControls = snapshot.pressedControls ();
        // The shell retains any lease that was actually acquired before a reload. Starting from a
        // held+armed snapshot cannot distinguish that lease from a DOWN which was rejected while
        // unarmed and became ready later, so startup must never synthesize a press.
        return result (snapshot, desiredBindings, List.of ());
    }


    /** {@inheritDoc} */
    @Override
    public CoreResult handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.requireRunning ();
        Objects.requireNonNull (event, "event");
        Objects.requireNonNull (snapshot, "snapshot");

        final Set<ControlId> continuingPresses = new LinkedHashSet<> (snapshot.pressedControls ());
        continuingPresses.retainAll (this.previousPressedControls);
        final Map<ControlId, ClipTargetId> desiredBindings = desiredBindings (snapshot, continuingPresses);
        final List<CoreEffect> effects;
        if (event instanceof final ButtonInputEvent button && CoreControls.DRUM_FILLS.contains (button.controlId ()))
        {
            if (button.pressed ())
                effects = isReady (snapshot, desiredBindings, button.controlId ()) ? List.of (pressEffect (snapshot, button.controlId (), desiredBindings.get (button.controlId ()))) : List.of ();
            else
                effects = List.of (new ReleaseClipTargetsEffect (button.controlId ()));
        }
        else
            effects = List.of ();

        this.previousPressedControls = snapshot.pressedControls ();
        return result (snapshot, desiredBindings, effects);
    }


    /** {@inheritDoc} */
    @Override
    public StateEnvelope checkpoint ()
    {
        this.requireRunning ();
        return new StateEnvelope (PullCoreProvider.STATE_SCHEMA, PullCoreProvider.STATE_SCHEMA_VERSION, new byte [0]);
    }


    /** {@inheritDoc} */
    @Override
    public void stop ()
    {
        this.lifecycle = Lifecycle.STOPPED;
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


    private static PressClipTargetEffect pressEffect (final ControllerSnapshot snapshot, final ControlId owner, final ClipTargetId target)
    {
        return new PressClipTargetEffect (owner, snapshot.clipCatalog ().generation (), target);
    }


    private static CoreResult result (final ControllerSnapshot snapshot, final Map<ControlId, ClipTargetId> desiredBindings, final List<CoreEffect> effects)
    {
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        for (final ControlId control: CoreControls.DRUM_FILLS)
        {
            final RgbColor color;
            if (!isReady (snapshot, desiredBindings, control))
                color = FILL_OFF;
            else if (snapshot.pressedControls ().contains (control))
                color = FILL_HELD;
            else
                color = FILL_AVAILABLE;
            lights.put (control, color);
        }
        return new CoreResult (new DesiredHardwareOutput (lights), desiredBindings, effects);
    }


    private void requireRunning ()
    {
        if (this.lifecycle != Lifecycle.RUNNING)
            throw new IllegalStateException ("Core is not running");
    }


    private enum Lifecycle
    {
        NEW,
        RUNNING,
        STOPPED
    }
}
