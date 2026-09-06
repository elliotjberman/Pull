// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Master is a page over the selected composition; holding its button opens the Frame page. */
public final class MasterButtonView implements ControllerView
{
    private static final ControlId BUTTON = PushControlIds.button ("MASTERTRACK");
    private static final ViewProfile PROFILE = ViewProfile.fixed ("default", Set.of (
        new SurfaceClaim (SurfaceArea.MASTER_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.MASTER_BUTTON, SurfaceClaim.Kind.OUTPUT)), Set.of ());
    private static final Set<ControllerActionBinding> ACTIONS = Set.of (new ControllerActionBinding (BUTTON, InputKind.BUTTON,
        ControllerActionId.SWITCH_PARAMETER_CONTEXT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)));
    private final List<Gesture> pending = new ArrayList<> ();
    private Gesture held;
    private final DeferredButtonAdmission admission = new DeferredButtonAdmission ();
    private final ControllerPageTransitions pages;
    private ControllerPageTransitions.Request lastEntry;
    private boolean returnOnRelease;

    public MasterButtonView () { this (new ControllerPageTransitions ()); }
    MasterButtonView (final ControllerPageTransitions pages) { this.pages = java.util.Objects.requireNonNull (pages, "pages"); }

    @Override public String id () { return "master-button"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }
    @Override public void start (final ControllerSnapshot snapshot) { this.deactivate (); }
    @Override public void deactivate () { this.pending.forEach (gesture -> this.pages.cancel (gesture.page)); this.pages.cancel (this.lastEntry); this.admission.clear (); this.pending.clear (); this.held = null; this.returnOnRelease = false; this.lastEntry = null; }

    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {

        this.pages.observe ();
    }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        if (!this.browser ()) this.returnOnRelease = false;
        final Gesture gesture = new Gesture (this.admission.begin (), this.returnOnRelease);
        if (gesture.returnOnRelease)
        {
            gesture.page = this.pages.adopt (this.lastEntry);
            if (gesture.page != null) this.lastEntry = gesture.page;
        }
        this.pending.add (gesture);
        this.held = gesture;
        return this.admission.action (gesture.ticket, binding.intent (), () -> this.advance (gesture));
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.reconcile (snapshot);
        if (event instanceof final ControllerInputEvent input && BUTTON.equals (input.controlId ()) && input.kind () == InputKind.BUTTON && this.held != null)
        {
            final Gesture gesture = this.held;
            final var origin = this.pages.origin ();
            if (input.phase () == InputPhase.LONG && !this.browser ())
            {
                this.returnOnRelease = true;
                gesture.returnOnRelease = true;
                gesture.page = this.pages.temporary (origin, "FRAME");
                this.lastEntry = gesture.page;
            }
            else if (input.phase () == InputPhase.END)
            {
                gesture.ended = true;
                this.held = null;
                gesture.releaseSuppressed = this.browser ();
                if (!gesture.releaseSuppressed && !gesture.returnOnRelease)
                    gesture.page = "MASTER".equals (this.pages.visibleAlias ()) ? this.pages.restore (origin) : this.pages.select (origin, "MASTER");
            }
        }
        final List<CoreEffect> effects = new ArrayList<> ();
        for (final Gesture gesture: List.copyOf (this.pending)) effects.addAll (this.advance (gesture));
        return List.copyOf (effects);
    }

    private List<CoreEffect> advance (final Gesture gesture)
    {
        if (!gesture.ticket.admitted ()) return List.of ();
        if (gesture.ended && gesture.returnOnRelease) this.pages.release (gesture.page, !gesture.releaseSuppressed);
        this.pages.advance (gesture.page);
        if (gesture.ended && this.pages.complete (gesture.page))
        {
            this.pending.remove (gesture);
            this.admission.finish (gesture.ticket);
        }
        return List.of ();
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final String mode = this.pages.visibleAlias ();
        final int brightness = mode.isEmpty () ? 0 : Set.of ("MASTER", "MASTER_TEMP", "FRAME").contains (mode) ? 255 : 60;
        return new ViewOutput (Map.of (BUTTON, new RgbColor (brightness, brightness, brightness)), Map.of ());
    }

    private boolean browser () { return "BROWSER".equals (this.pages.visibleAlias ()); }

    private static final class Gesture
    {
        private final DeferredButtonAdmission.Ticket ticket;
        private boolean returnOnRelease;
        private boolean ended;
        private boolean releaseSuppressed;
        private ControllerPageTransitions.Request page;

        private Gesture (final DeferredButtonAdmission.Ticket ticket, final boolean returnOnRelease) { this.ticket = ticket; this.returnOnRelease = returnOnRelease; }
    }
}
