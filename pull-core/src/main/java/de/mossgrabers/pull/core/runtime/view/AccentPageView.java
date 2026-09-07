// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.ui.page.AccentPagePresentation;
import de.mossgrabers.pull.core.ui.page.AccentPageRenderer;
import de.mossgrabers.pull.core.view.*;
import java.util.*;
import java.util.stream.IntStream;

/** Complete fixed-velocity page, including inherited current-track selection. */
public final class AccentPageView implements ControllerView
{
    private static final List<ControlId> KNOBS = IntStream.rangeClosed (1, 8).mapToObj (i -> PushControlIds.continuous ("KNOB" + i)).toList ();
    private static final ViewProfile PROFILE = ViewProfile.fixed ("full-page", Set.of (
        new SurfaceClaim (SurfaceArea.ENCODERS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.OUTPUT)), Set.of ());
    private final CurrentTrackRowSelection rows;
    private final Set<ControlId> touched = new HashSet<> ();
    private final ControllerIntegerSetting velocity;

    public AccentPageView (final SessionStopGesture stopGesture, final PageNavigation pages)
    {
        this.rows = new CurrentTrackRowSelection (stopGesture, pages);
        this.velocity = new ControllerIntegerSetting (SetControllerIntegerSettingEffect.Setting.ACCENT_VELOCITY, pages);
    }

    @Override public String id () { return "accent-page"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<ControllerActionBinding> actionBindings () { return CurrentTrackRowSelection.actionBindings (); }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.CONTROLLER_SETTINGS, BridgeSubscription.ENCODER_CONFIGURATION, BridgeSubscription.CURRENT_TRACK_BANK); }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (this.velocity.pending ()); }
    @Override public void start (final ControllerSnapshot snapshot) { this.deactivate (); this.reconcile (snapshot); }
    @Override public void reconcile (final ControllerSnapshot snapshot) { this.rows.reconcile (snapshot); }
    @Override public void deactivate () { this.rows.deactivate (); this.touched.clear (); this.velocity.clear (); }

    @Override
    public InputTarget inputTarget (final ControlId control, final InputKind kind, final ControllerSnapshot snapshot)
    {
        if (kind == InputKind.BUTTON && CurrentTrackRowSelection.accepts (control))
            return this.rows.inputTarget (control, snapshot);
        if (KNOBS.contains (control) && (!snapshot.bridge ().controllerSettings ().available () || !snapshot.bridge ().encoderConfiguration ().available ()))
            return null;
        return ControllerView.super.inputTarget (control, kind, snapshot);
    }

    @Override
    public List<CoreEffect> cancel (final ControlId control, final InputKind kind, final InputTarget target, final ControllerSnapshot snapshot)
    {
        if (kind == InputKind.BUTTON) this.rows.cancel (control);
        this.touched.remove (control);
        return List.of ();
    }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        return this.rows.resolveAction (binding, input, snapshot);
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        final var settings = snapshot.bridge ().controllerSettings ();
        final List<CoreEffect> effects = new ArrayList<> ();
        if (settings.available ()) effects.addAll (this.velocity.observe (snapshot, settings.accentVelocity ()));
        else this.velocity.clear ();
        effects.addAll (this.rows.handle (event, snapshot));
        if (!(event instanceof final ControllerInputEvent input)) return List.copyOf (effects);
        if (KNOBS.contains (input.controlId ()))
        {
            if (input.kind () == InputKind.TOUCH)
            {
                if (input.phase () == InputPhase.BEGIN) this.touched.add (input.controlId ());
                else if (input.phase () == InputPhase.END) this.touched.remove (input.controlId ());
            }
            else if (input.kind () == InputKind.RELATIVE && snapshot.bridge ().controllerSettings ().available () && snapshot.bridge ().encoderConfiguration ().available ())
            {
                final int observed = settings.accentVelocity ();
                final int desired = (int) Math.max (1, Math.min (127, this.velocity.intended (observed) + input.value () * snapshot.bridge ().encoderConfiguration ().baseStep () * 0.1));
                effects.addAll (this.velocity.request (snapshot, observed, desired));
            }
        }
        return List.copyOf (effects);
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final var settings = snapshot.bridge ().controllerSettings ();
        final var encoder = snapshot.bridge ().encoderConfiguration ();
        final boolean available = settings.available () && encoder.available ();
        final double position = available ? Math.floor (settings.accentVelocity () * (encoder.valueUpperBound () - 1.0) / 127) / encoder.valueUpperBound () : 0;
        final var presentation = new AccentPagePresentation (available, settings.accentVelocity (), position, this.touched.contains (KNOBS.get (7)));
        final var visuals = AccentPageRenderer.render (presentation);
        return new ViewOutput (visuals.lights (), Map.of (), visuals.display ());
    }

}
