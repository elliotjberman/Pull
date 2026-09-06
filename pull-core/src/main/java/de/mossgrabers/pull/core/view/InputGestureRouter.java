// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.*;
import java.util.*;

/**
 * Core-generation ownership of physical edge gestures. The bound exceeds the installed Push
 * edge-control set; arbitrary synthetic identities cannot create an unbounded retention cache.
 * Continuous events deliberately use the currently selected composition.
 */
public final class InputGestureRouter
{
    public static final int CAPACITY = 256;
    private final Map<Key, Gesture> held = new LinkedHashMap<> ();
    private final Set<Gesture> gestures = new LinkedHashSet<> ();
    private final Map<ResolvedControllerAction, Gesture> actions = new IdentityHashMap<> ();
    private final List<ControllerView> departing = new ArrayList<> ();
    private final Set<BridgeSubscription> emittedSubscriptions = new LinkedHashSet<> ();
    private final Set<ParameterBankId> emittedBanks = new LinkedHashSet<> ();
    private final List<ControllerView> reconciled = new ArrayList<> ();

    /** Start one core event; repeated page activations must not advance reconciliation twice. */
    public void beginEvent () { this.reconciled.clear (); }

    /** Start new views or reconcile existing ones at most once in this event. */
    public CoreResult activate (final CompiledWorkspace current, final ControllerSnapshot snapshot)
    {
        return current.activate (snapshot, (view, starting) -> this.reconcileOnce (view, snapshot, starting.booleanValue ()));
    }

    /** Capture before any page-changing action from this event is admitted. */
    public Dispatch capture (final CoreEvent event, final CompiledWorkspace current)
    {
        final Edge edge = edge (event);
        if (edge == null)
        {
            final List<ControllerView> receivers = new ArrayList<> (current.receivers (event));
            if (!(event instanceof ControllerInputEvent) && !(event instanceof ParameterMutationEvent))
                for (final ControllerView view: this.retainedViews ())
                    if (!containsIdentity (receivers, view)) receivers.add (view);
            return new Dispatch (event, List.copyOf (receivers), null, null);
        }
        if (edge.phase () == InputPhase.BEGIN)
        {
            if (this.held.containsKey (edge.key ())) return new Dispatch (event, List.of (), null, edge);
            if (this.gestures.size () >= CAPACITY) throw new IllegalStateException ("Core input gesture capacity exhausted");
            final CompiledWorkspace.ActionOwner owner = event instanceof final ControllerInputEvent input ? current.actionOwner (input) : null;
            final Gesture gesture = new Gesture (current.receivers (event), owner);
            this.held.put (edge.key (), gesture);
            this.gestures.add (gesture);
            return new Dispatch (event, gesture.receivers, gesture, edge);
        }
        final Gesture gesture = this.held.get (edge.key ());
        if (gesture == null || edge.phase () == InputPhase.LONG && gesture.longDelivered)
            return new Dispatch (event, List.of (), null, edge);
        if (edge.phase () == InputPhase.LONG) gesture.longDelivered = true;
        return new Dispatch (event, gesture.receivers, gesture, edge);
    }

    /** Resolve once through the original action owner, preserving the existing BEGIN bypass. */
    public ResolvedControllerAction resolveAction (final Dispatch dispatch, final ControllerSnapshot snapshot)
    {
        final Gesture gesture = dispatch.gesture;
        if (gesture == null || dispatch.edge.phase () != InputPhase.BEGIN || gesture.owner == null || gesture.actionResolved)
            return null;
        gesture.actionResolved = true;
        final CompiledWorkspace.ActionOwner owner = gesture.owner;
        final ResolvedControllerAction action = owner.resolve ((ControllerInputEvent) dispatch.event, snapshot);
        if (this.actions.put (action, gesture) != null)
            throw new IllegalStateException ("a resolved action must belong to one gesture");
        gesture.actionPending = true;
        return action;
    }

    /** Dispatch only the frozen receivers; rendering remains the active workspace's job. */
    public List<CoreEffect> dispatch (final Dispatch dispatch, final ControllerSnapshot snapshot)
    {
        if (dispatch.delivered) return List.of ();
        dispatch.delivered = true;
        final List<CoreEffect> effects = new ArrayList<> ();
        for (final ControllerView view: dispatch.receivers)
        {
            final List<CoreEffect> emitted = view.handle (dispatch.event, snapshot);
            if (!emitted.isEmpty ()) this.retainEffectRequirements (view);
            effects.addAll (emitted);
        }
        return List.copyOf (effects);
    }

    /** Execute an immediate or Snapback-released action before retiring its owning view. */
    public List<CoreEffect> dispatchAction (final ResolvedControllerAction action, final ControllerSnapshot snapshot)
    {
        final Gesture gesture = this.actions.remove (action);
        if (gesture != null)
        {
            gesture.actionPending = false;
            for (final ControllerView view: gesture.views ()) this.reconcileOnce (view, snapshot, false);
        }
        try
        {
            final List<CoreEffect> effects = action.dispatch ();
            if (gesture != null && !effects.isEmpty ()) this.retainEffectRequirements (gesture.owner.view ());
            return effects;
        }
        finally { if (gesture != null) this.retire (gesture); }
    }

    /** Physical END and semantic-action completion are independent retention conditions. */
    public void finish (final Dispatch dispatch, final CompiledWorkspace current)
    {
        if (dispatch != null && dispatch.gesture != null && dispatch.edge.phase () == InputPhase.END)
        {
            this.held.remove (dispatch.edge.key (), dispatch.gesture);
            dispatch.gesture.ended = true;
            this.retire (dispatch.gesture);
        }
        this.reap (current);
    }

    /** Retire departing identities before callbacks; held identities keep their lifecycle. */
    public void transition (final CompiledWorkspace previous, final CompiledWorkspace next)
    {
        for (final ControllerView view: previous.viewInstances ())
            if (!containsIdentity (next.viewInstances (), view) && !containsIdentity (this.departing, view)) this.departing.add (view);
        this.reap (next);
    }

    /** Reconcile current and captured identities once each, without rendering hidden pages. */
    public void reconcile (final CompiledWorkspace current, final ControllerSnapshot snapshot)
    {
        final List<ControllerView> views = new ArrayList<> (current.viewInstances ());
        for (final ControllerView view: this.retainedViews ())
            if (!containsIdentity (views, view)) views.add (view);
        for (final ControllerView view: views) this.reconcileOnce (view, snapshot, false);
    }

    /** Retain data and admitted touches only; never retain old routes, visuals, or page state. */
    public CoreResult decorate (final CompiledWorkspace current, final CoreResult result, final ControllerSnapshot snapshot)
    {
        this.reap (current);
        final Set<BridgeSubscription> subscriptions = new LinkedHashSet<> (result.desiredBridgeSubscriptions ().domains ());
        final Set<ParameterBankId> banks = new LinkedHashSet<> (result.desiredParameterBanks ().banks ());
        subscriptions.addAll (this.emittedSubscriptions);
        banks.addAll (this.emittedBanks);
        this.emittedSubscriptions.clear ();
        this.emittedBanks.clear ();
        final Map<ControlId, ParameterTargetRef> touches = new LinkedHashMap<> (result.desiredParameterTouches ().targets ());
        boolean ticks = result.executionRequirements ().ticksRequested ();
        for (final ControllerView view: this.retainedViews ())
        {
            if (containsIdentity (current.viewInstances (), view)) continue;
            subscriptions.addAll (view.bridgeSubscriptions ());
            banks.addAll (view.parameterBanks ());
            ticks |= view.executionRequirements ().ticksRequested ();
            view.parameterTouches (snapshot).targets ().forEach ((control, target) -> {
                if (!this.ownsHeldTouch (view, control))
                    throw new IllegalStateException ("retained view emitted a parameter touch outside its captured touch gesture");
                if (touches.putIfAbsent (control, target) != null)
                    throw new IllegalStateException ("current and retained views both own one parameter touch");
            });
        }
        return new CoreResult (result.desiredOutput (), result.desiredInputRoutes (), new DesiredBridgeSubscriptions (subscriptions), result.desiredClipBindings (), result.desiredControllerState (), result.desiredNoteRepeat (), result.desiredControllerActions (), new DesiredParameterBanks (banks), result.desiredParameterInteraction (), new DesiredParameterTouches (touches), new CoreExecutionRequirements (ticks), result.effects ());
    }

    private void reconcileOnce (final ControllerView view, final ControllerSnapshot snapshot, final boolean starting)
    {
        if (containsIdentity (this.reconciled, view)) return;
        this.reconciled.add (view);
        if (starting) view.start (snapshot);
        else view.reconcile (snapshot);
    }

    private void retainEffectRequirements (final ControllerView view)
    {
        this.emittedSubscriptions.addAll (view.bridgeSubscriptions ());
        this.emittedBanks.addAll (view.parameterBanks ());
    }

    private boolean ownsHeldTouch (final ControllerView view, final ControlId control)
    {
        final Gesture gesture = this.held.get (new Key (control, InputKind.TOUCH));
        return gesture != null && containsIdentity (gesture.receivers, view) && view.claims ().stream ().anyMatch (claim -> claim.kind () == SurfaceClaim.Kind.EXCLUSIVE_INPUT && claim.area ().controls ().contains (control) && claim.area ().inputKinds ().contains (InputKind.TOUCH));
    }

    private void retire (final Gesture gesture)
    {
        if (gesture.ended && !gesture.actionPending) this.gestures.remove (gesture);
    }

    private List<ControllerView> retainedViews ()
    {
        final List<ControllerView> views = new ArrayList<> ();
        for (final Gesture gesture: this.gestures)
            for (final ControllerView view: gesture.views ())
                if (!containsIdentity (views, view)) views.add (view);
        return views;
    }

    private void reap (final CompiledWorkspace current)
    {
        final List<ControllerView> retained = this.retainedViews ();
        final List<ControllerView> retired = new ArrayList<> ();
        for (final ControllerView view: List.copyOf (this.departing))
        {
            if (containsIdentity (current.viewInstances (), view)) this.departing.removeIf (candidate -> candidate == view);
            else if (!containsIdentity (retained, view)) { this.departing.removeIf (candidate -> candidate == view); retired.add (view); }
        }
        retired.forEach (ControllerView::deactivate);
    }

    private static Edge edge (final CoreEvent event)
    {
        if (event instanceof final ControllerInputEvent input && input.kind ().isEdge ())
            return new Edge (new Key (input.controlId (), input.kind ()), input.phase ());
        if (event instanceof final ButtonInputEvent input)
            return new Edge (new Key (input.controlId (), InputKind.BUTTON), input.pressed () ? InputPhase.BEGIN : InputPhase.END);
        if (event instanceof final TouchInputEvent input)
            return new Edge (new Key (input.controlId (), InputKind.TOUCH), input.touched () ? InputPhase.BEGIN : InputPhase.END);
        return null;
    }

    private static boolean containsIdentity (final Collection<ControllerView> views, final ControllerView candidate)
    {
        return views.stream ().anyMatch (view -> view == candidate);
    }

    private record Key (ControlId control, InputKind kind) { }
    private record Edge (Key key, InputPhase phase) { }

    /** Opaque, event-scoped receiver plan. It never owns a page or retargets an old edge. */
    public static final class Dispatch
    {
        private final CoreEvent event;
        private final List<ControllerView> receivers;
        private final Gesture gesture;
        private final Edge edge;
        private boolean delivered;
        private Dispatch (final CoreEvent event, final List<ControllerView> receivers, final Gesture gesture, final Edge edge)
        { this.event = event; this.receivers = receivers; this.gesture = gesture; this.edge = edge; }
    }

    private static final class Gesture
    {
        private final List<ControllerView> receivers;
        private final CompiledWorkspace.ActionOwner owner;
        private boolean ended;
        private boolean longDelivered;
        private boolean actionResolved;
        private boolean actionPending;
        private Gesture (final List<ControllerView> receivers, final CompiledWorkspace.ActionOwner owner)
        { this.receivers = List.copyOf (receivers); this.owner = owner; }
        private List<ControllerView> views ()
        {
            if (this.owner == null || containsIdentity (this.receivers, this.owner.view ())) return this.receivers;
            final List<ControllerView> views = new ArrayList<> (this.receivers);
            views.add (this.owner.view ());
            return views;
        }
    }
}
