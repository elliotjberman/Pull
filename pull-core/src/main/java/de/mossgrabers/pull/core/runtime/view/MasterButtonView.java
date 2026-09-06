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
    private ControllerSnapshot latest;
    private final DeferredButtonAdmission admission = new DeferredButtonAdmission ();
    private final ControllerPageTransitions pages;
    private ControllerPageTransitions.Request lastEntry;
    private boolean returnOnRelease;

    public MasterButtonView () { this (new ControllerPageTransitions ()); }
    MasterButtonView (final ControllerPageTransitions pages) { this.pages = java.util.Objects.requireNonNull (pages, "pages"); }

    @Override public String id () { return "master-button"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.CONTROLLER_LAYOUT); }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (this.pages.pending ()); }
    @Override public void start (final ControllerSnapshot snapshot) { this.deactivate (); this.latest = snapshot; }
    @Override public void deactivate () { this.pending.forEach (gesture -> this.pages.cancel (gesture.page)); this.pages.cancel (this.lastEntry); this.admission.clear (); this.pending.clear (); this.held = null; this.returnOnRelease = false; this.lastEntry = null; }

    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        this.latest = snapshot;
        this.pages.observe (snapshot);
    }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        if (!browser (snapshot)) this.returnOnRelease = false;
        final Gesture gesture = new Gesture (this.admission.begin (), this.returnOnRelease);
        if (gesture.returnOnRelease)
        {
            gesture.page = this.pages.adopt (this.lastEntry);
            if (gesture.page != null) this.lastEntry = gesture.page;
        }
        this.pending.add (gesture);
        this.held = gesture;
        return this.admission.action (gesture.ticket, binding.intent (), () -> this.advance (gesture, this.latest));
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.reconcile (snapshot);
        if (event instanceof final ControllerInputEvent input && BUTTON.equals (input.controlId ()) && input.kind () == InputKind.BUTTON && this.held != null)
        {
            final Gesture gesture = this.held;
            final var layout = snapshot.bridge ().layout ();
            if (input.phase () == InputPhase.LONG && !browser (snapshot))
            {
                this.returnOnRelease = true;
                gesture.returnOnRelease = true;
                gesture.page = this.pages.temporary (layout, "FRAME");
                this.lastEntry = gesture.page;
            }
            else if (input.phase () == InputPhase.END)
            {
                gesture.ended = true;
                this.held = null;
                gesture.releaseSuppressed = browser (snapshot);
                if (!gesture.releaseSuppressed && !gesture.returnOnRelease)
                    gesture.page = "MASTER".equals (layout.modeId ()) ? this.pages.restore (layout) : this.pages.select (layout, "MASTER");
            }
        }
        final List<CoreEffect> effects = new ArrayList<> ();
        for (final Gesture gesture: List.copyOf (this.pending)) effects.addAll (this.advance (gesture, snapshot));
        return List.copyOf (effects);
    }

    private List<CoreEffect> advance (final Gesture gesture, final ControllerSnapshot snapshot)
    {
        if (!gesture.ticket.admitted ()) return List.of ();
        if (gesture.ended && gesture.returnOnRelease) this.pages.release (gesture.page, !gesture.releaseSuppressed);
        final List<CoreEffect> effects = this.pages.advance (gesture.page, snapshot);
        if (gesture.ended && this.pages.complete (gesture.page))
        {
            this.pending.remove (gesture);
            this.admission.finish (gesture.ticket);
        }
        return effects;
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final String mode = snapshot.bridge ().layout ().modeId ();
        final int brightness = mode.isEmpty () ? 0 : Set.of ("MASTER", "MASTER_TEMP", "FRAME").contains (mode) ? 255 : 60;
        return new ViewOutput (Map.of (BUTTON, new RgbColor (brightness, brightness, brightness)), Map.of ());
    }

    private static boolean browser (final ControllerSnapshot snapshot) { return "BROWSER".equals (snapshot.bridge ().layout ().modeId ()); }

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
