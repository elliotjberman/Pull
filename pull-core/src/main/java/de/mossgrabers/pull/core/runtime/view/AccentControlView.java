// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.view.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Retained Accent toggle and temporary-page gesture, with observed configuration feedback. */
public final class AccentControlView implements ControllerView
{
    private static final ControlId BUTTON = PushControlIds.button ("ACCENT");
    private static final long TIMEOUT_NANOS = 5_000_000_000L;
    private static final ViewProfile PROFILE = ViewProfile.fixed ("default", Set.of (
        new SurfaceClaim (SurfaceArea.ACCENT_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.ACCENT_BUTTON, SurfaceClaim.Kind.OUTPUT)), Set.of ());
    private static final Set<ControllerActionBinding> ACTIONS = Set.of (new ControllerActionBinding (BUTTON, InputKind.BUTTON,
        ControllerActionId.SWITCH_PARAMETER_CONTEXT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)));
    private final AuthoritativeBooleanToggle<String> enabled = new AuthoritativeBooleanToggle<> ();
    private final List<Gesture> pending = new ArrayList<> ();
    private Gesture held;
    private ControllerSnapshot latest;
    private final DeferredButtonAdmission admission = new DeferredButtonAdmission ();

    @Override public String id () { return "accent-control"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.CONTROLLER_LAYOUT, BridgeSubscription.CONTROLLER_SETTINGS); }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (this.enabled.pending () || this.pending.stream ().anyMatch (gesture -> gesture.submitted)); }
    @Override public void start (final ControllerSnapshot snapshot) { this.deactivate (); this.latest = snapshot; }
    @Override public void reconcile (final ControllerSnapshot snapshot) { this.latest = snapshot; }
    @Override public void deactivate () { this.admission.clear (); this.held = null; this.pending.clear (); this.enabled.clear (); }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        this.latest = snapshot;
        final Gesture gesture = new Gesture (this.admission.begin (), snapshot.bridge ().layout ());
        this.pending.add (gesture);
        this.held = gesture;
        return this.admission.action (gesture.ticket, binding.intent (), () -> this.advance (gesture, this.latest));
    }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.latest = snapshot;
        if (event instanceof final ControllerInputEvent input && input.controlId ().equals (BUTTON) && input.kind () == InputKind.BUTTON && this.held != null)
        {
            if (input.phase () == InputPhase.LONG && !this.held.longSeen)
                this.held.longSeen = true;
            else if (input.phase () == InputPhase.END)
            {
                this.held.ended = true;
                this.held = null;
            }
        }
        final List<CoreEffect> effects = new ArrayList<> (this.toggle (snapshot, false));
        for (final Gesture gesture: List.copyOf (this.pending)) effects.addAll (this.advance (gesture, snapshot));
        return List.copyOf (effects);
    }

    private List<CoreEffect> advance (final Gesture gesture, final ControllerSnapshot snapshot)
    {
        if (!gesture.ticket.admitted ()) return List.of ();
        final var layout = snapshot.bridge ().layout ();
        if (gesture.longSeen && !gesture.submitted)
        {
            if (!gesture.origin.equals (layout) || layout.generation () == 0)
            {
                this.finish (gesture);
                return List.of ();
            }
            gesture.submitted = true;
            gesture.submittedRevision = snapshot.revision ();
            gesture.submittedAt = snapshot.monotonicTimeNanos ();
            return List.of (new SelectControllerModeEffect (layout.generation (), "ACCENT", SelectControllerModeEffect.Operation.TEMPORARY));
        }
        if (gesture.submitted && snapshot.monotonicTimeNanos () - gesture.submittedAt >= TIMEOUT_NANOS)
        {
            this.finish (gesture);
            return List.of ();
        }
        if (!gesture.ended) return List.of ();
        if (!gesture.longSeen)
        {
            this.finish (gesture);
            return this.toggle (snapshot, true);
        }
        if (snapshot.revision () > gesture.submittedRevision && layout.generation () >= gesture.origin.generation () && layout.temporaryMode () && "ACCENT".equals (layout.modeId ()) && layout.activeModeId ().equals (gesture.origin.activeModeId ()))
        {
            this.finish (gesture);
            return List.of (SelectControllerModeEffect.restore (layout.generation ()));
        }
        return List.of ();
    }

    private void finish (final Gesture gesture)
    {
        this.pending.remove (gesture);
        this.admission.finish (gesture.ticket);
    }

    private List<CoreEffect> toggle (final ControllerSnapshot snapshot, final boolean press)
    {
        final var settings = snapshot.bridge ().controllerSettings ();
        if (!settings.available ()) { this.enabled.clear (); return List.of (); }
        return this.enabled.update ("accent", settings.accentEnabled (), snapshot.monotonicTimeNanos (), press,
            (ignored, value) -> new SetControllerBooleanSettingEffect (SetControllerBooleanSettingEffect.Setting.ACCENT_ENABLED, value.booleanValue ()));
    }

    @Override public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final var settings = snapshot.bridge ().controllerSettings ();
        final int brightness = !settings.available () ? 0 : settings.accentEnabled () ? 255 : 60;
        return new ViewOutput (Map.of (BUTTON, new RgbColor (brightness, brightness, brightness)), Map.of ());
    }

    private static final class Gesture
    {
        private final DeferredButtonAdmission.Ticket ticket;
        private boolean longSeen;
        private boolean ended;
        private final ControllerLayoutSnapshot origin;
        private boolean submitted;
        private long submittedRevision;
        private long submittedAt;
        private Gesture (final DeferredButtonAdmission.Ticket ticket, final ControllerLayoutSnapshot origin) { this.ticket = ticket; this.origin = origin; }
    }
}
