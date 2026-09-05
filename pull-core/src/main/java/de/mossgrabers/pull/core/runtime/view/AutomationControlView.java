// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControllerActionBinding;
import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerStateScope;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreExecutionRequirements;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.ConsumeControllerButtonEffect;
import de.mossgrabers.pull.core.api.effect.ResetAutomationOverridesEffect;
import de.mossgrabers.pull.core.api.effect.SelectControllerModeEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewOutput;
import de.mossgrabers.pull.core.view.ViewProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Retained Automation button policy, independent of the page selected by its long press. */
public final class AutomationControlView implements ControllerView
{
    private static final ControlId BUTTON = PushControlIds.button ("AUTOMATION");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId DELETE = PushControlIds.button ("DELETE");
    private static final ViewProfile PROFILE = ViewProfile.fixed ("default", Set.of (
        new SurfaceClaim (SurfaceArea.AUTOMATION_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.AUTOMATION_BUTTON, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT),
        new SurfaceClaim (SurfaceArea.DELETE_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT)), Set.of ());
    private final AutomationControlState state;
    private static final long ACKNOWLEDGEMENT_TIMEOUT_NANOS = 5_000_000_000L;
    private static final Set<ControllerActionBinding> ACTIONS = Set.of (new ControllerActionBinding (BUTTON, InputKind.BUTTON,
        ControllerActionId.SWITCH_PARAMETER_CONTEXT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)));
    private final DeferredButtonAdmission admission = new DeferredButtonAdmission ();
    private final List<Gesture> pending = new ArrayList<> ();
    private Gesture held;
    private ControllerSnapshot latest;
    private boolean restoreOnRelease;
    private PageEntry lastEntry;

    public AutomationControlView () { this (new AutomationControlState ()); }
    AutomationControlView (final AutomationControlState state) { this.state = java.util.Objects.requireNonNull (state, "state"); }
    @Override public String id () { return "automation-control"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }
    @Override public void start (final ControllerSnapshot snapshot) { this.deactivate (); this.latest = snapshot; }
    @Override public void deactivate () { this.admission.clear (); this.pending.clear (); this.held = null; this.restoreOnRelease = false; this.lastEntry = null; }

    @Override
    public void reconcile (final ControllerSnapshot snapshot)
    {
        this.latest = snapshot;
        this.observeEntry (this.lastEntry, snapshot);
        for (final Gesture gesture: this.pending) this.observeEntry (gesture.entry, snapshot);
    }

    private void observeEntry (final PageEntry entry, final ControllerSnapshot snapshot)
    {
        if (entry == null || !entry.submitted || entry.abandoned || entry.observed) return;
        final var layout = snapshot.bridge ().layout ();
        if (snapshot.revision () > entry.submittedRevision && "AUTOMATION".equals (layout.modeId ()) && layout.temporaryMode ())
            entry.observed = true;
        else if (snapshot.monotonicTimeNanos () - entry.submittedAt >= ACKNOWLEDGEMENT_TIMEOUT_NANOS)
            entry.abandoned = true;
    }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        final boolean deleting = snapshot.pressedControls ().contains (DELETE);
        if (!deleting) this.restoreOnRelease = false;
        final Gesture gesture = new Gesture (this.admission.begin (), this.restoreOnRelease, deleting ? this.lastEntry : null);
        if (deleting && snapshot.bridge ().automation ().available ())
            gesture.reset = new ResetAutomationOverridesEffect (snapshot.bridge ().automation ().projectIdentity ());
        this.pending.add (gesture);
        this.held = gesture;
        final var action = this.admission.action (gesture.ticket, binding.intent (), () -> this.advance (gesture, this.latest));
        // Delete's physical release can precede admission, so consume it at the original BEGIN.
        return deleting ? action.withImmediateConsumption (DELETE) : action;
    }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.AUTOMATION, BridgeSubscription.CONTROLLER_LAYOUT); }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (this.state.pending () || this.pending.stream ().anyMatch (gesture -> gesture.entry != null || gesture.ended)); }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        final List<CoreEffect> effects = new ArrayList<> ();
        this.reconcile (snapshot);
        if (event instanceof final ControllerInputEvent input && BUTTON.equals (input.controlId ()) && input.kind () == InputKind.BUTTON && this.held != null)
        {
            final Gesture gesture = this.held;
            final boolean deleting = snapshot.pressedControls ().contains (DELETE);
            if (input.phase () != InputPhase.BEGIN && deleting) effects.add (new ConsumeControllerButtonEffect (DELETE));
            if (input.phase () == InputPhase.LONG && !deleting)
            {
                this.restoreOnRelease = true;
                gesture.restoreOnRelease = true;
                gesture.entry = new PageEntry (new SelectControllerModeEffect (snapshot.bridge ().layout ().generation (), "AUTOMATION", SelectControllerModeEffect.Operation.TEMPORARY));
                this.lastEntry = gesture.entry;
            }
            else if (input.phase () == InputPhase.END)
            {
                gesture.ended = true;
                this.held = null;
                gesture.releaseSuppressed = deleting;
                if (!deleting && !gesture.restoreOnRelease && snapshot.bridge ().automation ().available ())
                    gesture.toggleProject = snapshot.bridge ().automation ().projectIdentity ();
            }
        }
        for (final Gesture gesture: List.copyOf (this.pending)) effects.addAll (this.advance (gesture, snapshot));
        effects.addAll (this.state.advance (snapshot));
        return List.copyOf (effects);
    }

    private List<CoreEffect> advance (final Gesture gesture, final ControllerSnapshot snapshot)
    {
        if (!gesture.ticket.admitted ()) return List.of ();
        final List<CoreEffect> effects = new ArrayList<> ();
        if (gesture.reset != null)
        {
            effects.add (gesture.reset);
            gesture.reset = null;
        }
        final PageEntry entry = gesture.entry;
        if (entry != null && !entry.submitted)
        {
            entry.submitted = true;
            // Timestamp submission at admission, not the earlier physical LONG or END.
            entry.submittedRevision = snapshot.revision ();
            entry.submittedAt = snapshot.monotonicTimeNanos ();
            effects.add (entry.effect);
            return List.copyOf (effects);
        }
        if (!gesture.ended) return List.copyOf (effects);
        if (!gesture.releaseSuppressed && gesture.restoreOnRelease && (entry == null || !entry.abandoned))
        {
            if (entry != null && !entry.observed) return List.copyOf (effects);
            effects.add (SelectControllerModeEffect.restore (snapshot.bridge ().layout ().generation ()));
        }
        else if (!gesture.releaseSuppressed && !gesture.toggleProject.isEmpty () && gesture.toggleProject.equals (snapshot.bridge ().automation ().projectIdentity ()))
        {
            this.state.toggle (snapshot);
            effects.addAll (this.state.advance (snapshot));
        }
        this.pending.remove (gesture);
        this.admission.finish (gesture.ticket);
        return List.copyOf (effects);
    }

    private static final class Gesture
    {
        private final DeferredButtonAdmission.Ticket ticket;
        private boolean restoreOnRelease;
        private boolean ended;
        private boolean releaseSuppressed;
        private PageEntry entry;
        private ResetAutomationOverridesEffect reset;
        private String toggleProject = "";
        private Gesture (final DeferredButtonAdmission.Ticket ticket, final boolean restoreOnRelease, final PageEntry entry)
        {
            this.ticket = ticket;
            this.restoreOnRelease = restoreOnRelease;
            this.entry = entry;
        }
    }

    /** Delete-BEGIN preserves the prior long-press return flag and its still-pending entry. */
    private static final class PageEntry
    {
        private final SelectControllerModeEffect effect;
        private boolean submitted;
        private boolean observed;
        private boolean abandoned;
        private long submittedRevision;
        private long submittedAt;
        private PageEntry (final SelectControllerModeEffect effect) { this.effect = effect; }
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final var automation = snapshot.bridge ().automation ();
        final RgbColor color = !automation.available () ? new RgbColor (0, 0, 0) : !automation.writingEnabled () ? new RgbColor (30, 30, 30) : snapshot.pressedControls ().contains (SHIFT) ? new RgbColor (89, 29, 0) : new RgbColor (255, 0, 0);
        return new ViewOutput (Map.of (BUTTON, color), Map.of ());
    }
}
