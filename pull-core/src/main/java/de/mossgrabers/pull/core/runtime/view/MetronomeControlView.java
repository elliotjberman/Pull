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
import de.mossgrabers.pull.core.api.effect.SetProjectTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.SetTransportSettingEffect;
import de.mossgrabers.pull.core.api.effect.TransportSetting;
import de.mossgrabers.pull.core.api.effect.TransportState;
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

/** Global metronome button, including the legacy consumed-release page latch. */
public final class MetronomeControlView implements ControllerView
{
    private static final ControlId BUTTON = PushControlIds.button ("METRONOME");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ViewProfile PROFILE = ViewProfile.fixed ("default", Set.of (
        new SurfaceClaim (SurfaceArea.METRONOME_BUTTON, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
        new SurfaceClaim (SurfaceArea.METRONOME_BUTTON, SurfaceClaim.Kind.OUTPUT),
        new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT)), Set.of ());
    private final AuthoritativeBooleanToggle<String> metronome;
    private final AuthoritativeBooleanToggle<String> ticks = new AuthoritativeBooleanToggle<> ();
    private static final Set<ControllerActionBinding> ACTIONS = Set.of (new ControllerActionBinding (BUTTON, InputKind.BUTTON,
        ControllerActionId.SWITCH_PARAMETER_CONTEXT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)));
    private final DeferredButtonAdmission admission = new DeferredButtonAdmission ();
    private final List<Gesture> pending = new ArrayList<> ();
    private Gesture held;
    private ControllerSnapshot latest;
    private final ControllerPageTransitions pages;
    private ControllerPageTransitions.Request lastEntry;

    MetronomeControlView (final AuthoritativeBooleanToggle<String> metronome, final ControllerPageTransitions pages)
    {
        this.metronome = java.util.Objects.requireNonNull (metronome, "metronome");
        this.pages = java.util.Objects.requireNonNull (pages, "pages");
    }
    @Override public String id () { return "metronome-control"; }
    @Override public ViewProfile profile () { return PROFILE; }
    @Override public Set<ControllerActionBinding> actionBindings () { return ACTIONS; }
    @Override public void start (final ControllerSnapshot snapshot) { this.deactivate (); this.latest = snapshot; }
    @Override public void deactivate () { this.pending.forEach (gesture -> this.pages.cancel (gesture.page)); this.pages.cancel (this.lastEntry); this.admission.clear (); this.pending.clear (); this.held = null; this.lastEntry = null; this.ticks.clear (); }
    @Override public void reconcile (final ControllerSnapshot snapshot) { this.latest = snapshot; this.pages.observe (); }

    @Override
    public ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        final Gesture gesture = new Gesture (this.admission.begin ());
        this.pending.add (gesture);
        this.held = gesture;
        return this.admission.action (gesture.ticket, binding.intent (), () -> this.advance (gesture, this.latest));
    }
    @Override public Set<BridgeSubscription> bridgeSubscriptions () { return Set.of (BridgeSubscription.PROJECT, BridgeSubscription.TRANSPORT, BridgeSubscription.TRANSPORT_SETTINGS); }
    @Override public CoreExecutionRequirements executionRequirements () { return new CoreExecutionRequirements (this.metronome.pending () || this.ticks.pending ()); }

    @Override
    public List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        final List<CoreEffect> effects = new ArrayList<> ();
        this.reconcile (snapshot);
        if (event instanceof final ControllerInputEvent input && BUTTON.equals (input.controlId ()) && input.kind () == InputKind.BUTTON && this.held != null)
        {
            final Gesture gesture = this.held;
            final boolean shifted = snapshot.pressedControls ().contains (SHIFT);
            final var origin = this.pages.origin ();
            if (input.phase () == InputPhase.LONG && !shifted && !gesture.consumed)
            {
                gesture.consumed = true;
                gesture.page = this.pages.temporary (origin, "TRANSPORT");
                this.lastEntry = gesture.page;
            }
            else if (input.phase () == InputPhase.END)
            {
                gesture.ended = true;
                this.held = null;
                if (!gesture.consumed)
                {
                    if (shifted) gesture.tickProject = snapshot.bridge ().transportSettings ().projectIdentity ();
                    else if ("TRANSPORT".equals (this.pages.visibleAlias ())) gesture.page = this.pages.restore (origin);
                    else gesture.metronomeProject = snapshot.bridge ().project ().projectIdentity ();
                }
            }
        }
        for (final Gesture gesture: List.copyOf (this.pending)) effects.addAll (this.advance (gesture, snapshot));
        effects.addAll (this.advanceToggles (snapshot, false, false));
        return List.copyOf (effects);
    }

    private List<CoreEffect> advance (final Gesture gesture, final ControllerSnapshot snapshot)
    {
        if (!gesture.ticket.admitted ()) return List.of ();
        final List<CoreEffect> effects = new ArrayList<> ();
        if (gesture.ended && gesture.consumed) this.pages.release (gesture.page, false);
        this.pages.advance (gesture.page);
        if (gesture.ended)
        {
            effects.addAll (this.advanceToggles (snapshot,
                !gesture.metronomeProject.isEmpty () && gesture.metronomeProject.equals (snapshot.bridge ().project ().projectIdentity ()),
                !gesture.tickProject.isEmpty () && gesture.tickProject.equals (snapshot.bridge ().transportSettings ().projectIdentity ())));
            this.pending.remove (gesture);
            this.admission.finish (gesture.ticket);
        }
        return List.copyOf (effects);
    }

    private List<CoreEffect> advanceToggles (final ControllerSnapshot snapshot, final boolean toggle, final boolean toggleTicks)
    {
        final List<CoreEffect> effects = new ArrayList<> ();
        final var project = snapshot.bridge ().project ();
        final var transport = snapshot.bridge ().transport ();
        if (project.available () && !project.commandPending () && transport.available ())
            effects.addAll (this.metronome.update (project.projectIdentity (), transport.metronomeEnabled (), snapshot.monotonicTimeNanos (), toggle, (identity, enabled) -> new SetProjectTransportStateEffect (identity, identity, TransportState.METRONOME, enabled.booleanValue ())));
        else this.metronome.clear ();
        final var settings = snapshot.bridge ().transportSettings ();
        if (settings.available ())
            effects.addAll (this.ticks.update (settings.projectIdentity (), settings.tickPlaybackEnabled (), snapshot.monotonicTimeNanos (), toggleTicks, (identity, enabled) -> new SetTransportSettingEffect (identity, TransportSetting.TICK_PLAYBACK, enabled.booleanValue ())));
        else this.ticks.clear ();
        return List.copyOf (effects);
    }

    private static final class Gesture
    {
        private final DeferredButtonAdmission.Ticket ticket;
        private boolean consumed;
        private boolean ended;
        private ControllerPageTransitions.Request page;
        private String metronomeProject = "";
        private String tickProject = "";
        private Gesture (final DeferredButtonAdmission.Ticket ticket) { this.ticket = ticket; }
    }

    @Override
    public ViewOutput render (final ControllerSnapshot snapshot)
    {
        final var state = snapshot.bridge ().transport ();
        final int value = !state.available () ? 0 : state.metronomeEnabled () ? 255 : 60;
        return new ViewOutput (Map.of (BUTTON, new RgbColor (value, value, value)), Map.of ());
    }
}
