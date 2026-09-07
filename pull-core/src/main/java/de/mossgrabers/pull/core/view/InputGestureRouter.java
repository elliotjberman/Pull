// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import de.mossgrabers.pull.core.api.event.*;
import de.mossgrabers.pull.core.interaction.InteractionLifecycle;
import java.util.*;

/** One core-local owner of physical lifetimes, exact input targets and parameter touch cleanup. */
public final class InputGestureRouter
{
    public static final int CAPACITY = 256;
    private static final Set<Key> CONTROLS = footprint ();
    // IDs never cross this core instance. Host execution is fenced by parent-owned target refs.
    private final InteractionLifecycle<Key, InputTarget> lifecycle = new InteractionLifecycle<> (0, CONTROLS, CAPACITY, CAPACITY);
    private final Map<InteractionLifecycle.Id, Gesture> gestures = new LinkedHashMap<> ();
    private final Map<ResolvedControllerAction, PendingAction> actions = new IdentityHashMap<> ();
    private final Set<ControllerView> started = Collections.newSetFromMap (new IdentityHashMap<> ());
    private final Set<ControllerView> reconciled = Collections.newSetFromMap (new IdentityHashMap<> ());
    private final List<CoreEffect> cleanup = new ArrayList<> ();
    private final Set<BridgeSubscription> emittedSubscriptions = new LinkedHashSet<> ();
    private final Set<ParameterBankId> emittedBanks = new LinkedHashSet<> ();
    private Map<Key, Binding> bindings = Map.of ();

    public void beginEvent () { this.reconciled.clear (); }

    public CoreResult activate (final CompiledWorkspace current, final ControllerSnapshot snapshot)
    {
        this.reconcile (current, snapshot);
        return current.render (this.viewSnapshot (snapshot), List.of ());
    }

    /** Capture all physical events before page admission and before Snapback sees motion. */
    public Dispatch capture (final CoreEvent event, final CompiledWorkspace current)
    {
        final Edge edge = edge (event);
        if (edge == null)
        {
            final Key companion = companion (event);
            if (companion != null && (this.lifecycle.isHeld (companion) || event instanceof ControllerInputEvent input && input.kind () == InputKind.POLY_PRESSURE))
            {
                final Gesture gesture = this.lifecycle.current (companion).map (active -> this.gestures.get (active.id ())).orElse (null);
                return new Dispatch (event, gesture == null ? List.of () : intersection (current.receivers (event), gesture.receivers), gesture, null, gesture == null);
            }
            return new Dispatch (event, current.receivers (event), null, null, false);
        }
        if (!CONTROLS.contains (edge.key)) return new Dispatch (event, List.of (), null, edge, true);
        final Gesture gesture;
        if (edge.phase == InputPhase.BEGIN)
        {
            final var admitted = this.lifecycle.begin (edge.key);
            if (admitted.interaction ().isEmpty ()) return new Dispatch (event, List.of (), null, edge, true);
            final var interaction = admitted.interaction ().orElseThrow ();
            gesture = new Gesture (interaction.id (), edge.key, this.bindings.get (edge.key));
            this.gestures.put (interaction.id (), gesture);
        }
        else
        {
            gesture = this.lifecycle.current (edge.key).map (active -> this.gestures.get (active.id ())).orElse (null);
            if (gesture == null || edge.phase == InputPhase.LONG && gesture.longDelivered)
                return new Dispatch (event, List.of (), null, edge, true);
            if (edge.phase == InputPhase.LONG) gesture.longDelivered = true;
        }
        return new Dispatch (event, gesture.receivers, gesture, edge, false);
    }

    public ResolvedControllerAction resolveAction (final Dispatch dispatch, final ControllerSnapshot snapshot)
    {
        final Gesture gesture = dispatch.gesture;
        if (gesture == null || dispatch.edge == null || dispatch.edge.phase != InputPhase.BEGIN || gesture.binding.action == null)
            return null;
        if (this.actions.size () >= DesiredParameterInteraction.PENDING_ACTION_CAPACITY)
            throw new IllegalStateException ("Pending input actions exceed their bounded capacity");
        final ResolvedControllerAction action = gesture.binding.action.resolve ((ControllerInputEvent) dispatch.event, this.viewSnapshot (snapshot));
        this.actions.put (action, new PendingAction (gesture.key, gesture.binding));
        return action;
    }

    public List<CoreEffect> dispatch (final Dispatch dispatch, final ControllerSnapshot snapshot)
    {
        if (dispatch.delivered || dispatch.suppressed || dispatch.gesture != null && this.lifecycle.current (dispatch.gesture.key).filter (active -> active.id ().equals (dispatch.gesture.id)).isEmpty ()) return List.of ();
        dispatch.delivered = true;
        final List<CoreEffect> effects = new ArrayList<> ();
        final Gesture gesture = dispatch.gesture;
        if (gesture != null && dispatch.edge != null && gesture.binding.parameterTouch)
        {
            if (dispatch.edge.phase == InputPhase.BEGIN)
            {
                gesture.touchAdmitted = true;
                gesture.project = snapshot.bridge ().automation ().projectIdentity ();
                if (this.viewSnapshot (snapshot).pressedControls ().contains (PushControlIds.button ("DELETE")))
                {
                    effects.add (new ConsumeControllerButtonEffect (PushControlIds.button ("DELETE")));
                    if (gesture.binding.target instanceof final InputTarget.Parameter parameter)
                        effects.add (new ResetParameterEffect (parameter.reference ()));
                }
            }
        }
        for (final ControllerView view: dispatch.receivers)
        {
            final ControllerSnapshot inputs = dispatch.event instanceof ControllerInputEvent input && input.kind () == InputKind.CHANNEL_PRESSURE ? this.groupInputSnapshot (view, snapshot) : this.viewSnapshot (snapshot);
            final List<CoreEffect> emitted = view.handle (dispatch.event, inputs);
            if (!emitted.isEmpty ()) this.retainEffectRequirements (view);
            effects.addAll (emitted);
        }
        return List.copyOf (effects);
    }

    /** A deferred action keeps its resolved intent, but cannot outlive the binding that admitted it. */
    public List<CoreEffect> dispatchAction (final ResolvedControllerAction action, final ControllerSnapshot snapshot)
    {
        final PendingAction pending = this.actions.remove (action);
        if (pending != null && pending.cancelled) return List.of ();
        final List<CoreEffect> effects = action.dispatch ();
        if (pending != null && !effects.isEmpty ()) this.retainEffectRequirements (pending.binding.action.view ());
        return effects;
    }

    /** Every physical END retires its tail, including an already-cancelled or rejected press. */
    public void finish (final Dispatch dispatch, final ControllerSnapshot snapshot)
    {
        if (dispatch != null && dispatch.edge != null && dispatch.edge.phase == InputPhase.END && CONTROLS.contains (dispatch.edge.key))
            this.lifecycle.release (dispatch.edge.key);
        this.finalizeReady (snapshot);
    }

    /** Cancel before a departing view is deactivated. Hidden views never receive later input/ticks. */
    public void transition (final CompiledWorkspace previous, final CompiledWorkspace next, final ControllerSnapshot snapshot)
    {
        this.synchronize (next, snapshot);
        for (final ControllerView view: previous.viewInstances ())
            if (!next.viewInstances ().contains (view)) view.deactivate ();
    }

    public void reconcile (final CompiledWorkspace current, final ControllerSnapshot snapshot)
    {
        for (final ControllerView view: current.viewInstances ()) this.reconcileOnce (view, snapshot);
        this.synchronize (current, snapshot);
    }

    private void synchronize (final CompiledWorkspace current, final ControllerSnapshot snapshot)
    {
        this.finalizeReady (snapshot);
        final Map<Key, Binding> next = new LinkedHashMap<> ();
        final Map<Key, InputTarget> targets = new LinkedHashMap<> ();
        final Map<ControlId, ParameterSlot> slots = current.parameterSlots (snapshot);
        for (final Key key: CONTROLS)
        {
            final Binding binding = binding (current, key, slots, snapshot);
            if (binding == null) continue;
            next.put (key, binding);
            // The existing clip executor addresses release by logical owner. Its owner cannot
            // be reused for a different clip until the old acquired session has retired.
            final boolean ownerRetiring = binding.target instanceof InputTarget.Clip && this.gestures.values ().stream ().anyMatch (gesture -> gesture.key.equals (key) && gesture.finishRevision >= 0 && gesture.binding.target instanceof InputTarget.Clip);
            if (!ownerRetiring) targets.put (key, binding.target);
        }
        for (final Gesture gesture: this.gestures.values ())
        {
            if (gesture.finishRevision >= 0) continue;
            final Binding binding = next.get (gesture.key);
            if (!gesture.binding.sameBinding (binding)) this.lifecycle.cancel (gesture.key);
            else
            {
                for (final ControllerView view: gesture.receivers)
                    if (!binding.receivers.contains (view))
                    {
                        final List<CoreEffect> effects = view.cancel (gesture.key.control, gesture.key.kind, gesture.binding.targets.get (view), snapshot);
                        this.cleanup.addAll (effects);
                        if (!effects.isEmpty ()) this.retainEffectRequirements (view);
                    }
                gesture.receivers = intersection (binding.receivers, gesture.receivers);
                if (gesture.receivers.isEmpty () && gesture.binding.action == null) this.lifecycle.cancel (gesture.key);
            }
        }
        for (final var pending: this.actions.entrySet ())
        {
            final PendingAction action = pending.getValue ();
            if (action.cancelled || action.binding.sameBinding (next.get (action.key))) continue;
            action.cancelled = true;
            final List<CoreEffect> effects = pending.getKey ().cancel ();
            this.cleanup.addAll (effects);
            if (!effects.isEmpty ()) this.retainEffectRequirements (action.binding.action.view ());
        }
        this.bindings = Map.copyOf (next);
        this.lifecycle.replaceBindings (targets);
        this.finalizeReady (snapshot);
    }

    private void finalizeReady (final ControllerSnapshot snapshot)
    {
        for (final var id: this.lifecycle.readyToFinish ())
        {
            final var finish = this.lifecycle.beginFinish (id).orElseThrow ();
            final Gesture gesture = this.gestures.get (id);
            gesture.finishRevision = snapshot.revision ();
            if (finish.reason () != InteractionLifecycle.EndReason.RELEASED)
                for (final ControllerView view: gesture.receivers)
                {
                    final List<CoreEffect> effects = view.cancel (gesture.key.control, gesture.key.kind, gesture.binding.targets.get (view), snapshot);
                    this.cleanup.addAll (effects);
                    if (!effects.isEmpty ()) this.retainEffectRequirements (view);
                }
            if (gesture.touchAdmitted)
            {
                final AutomationSnapshot automation = snapshot.bridge ().automation ();
                // Stopping a touch's automation write is cleanup, also required on cancellation.
                if (automation.available () && automation.stopOnTouchRelease () && automation.writingEnabled () && automation.projectIdentity ().equals (gesture.project))
                {
                    this.cleanup.add (new SetAutomationWriteEffect (gesture.project, false));
                    this.emittedSubscriptions.add (BridgeSubscription.AUTOMATION);
                }
            }
        }
        final var iterator = this.gestures.entrySet ().iterator ();
        while (iterator.hasNext ())
        {
            final var entry = iterator.next ();
            final Gesture gesture = entry.getValue ();
            if (gesture.finishRevision < 0) continue;
            if (gesture.touchAdmitted && gesture.binding.target instanceof final InputTarget.Parameter parameter &&
                (snapshot.revision () <= gesture.finishRevision || snapshot.bridge ().parameters ().touchLeases ().contains (parameter.reference ()))) continue;
            if (gesture.binding.target instanceof InputTarget.Clip &&
                (snapshot.revision () <= gesture.finishRevision || snapshot.clipLaunchSessionTargets ().containsKey (gesture.key.control))) continue;
            // No resource was acquired, or a later shell sample confirms its lease was retired.
            this.lifecycle.completeFinish (entry.getKey ());
            iterator.remove ();
        }
    }

    public CoreResult decorate (final CompiledWorkspace current, final CoreResult result, final ControllerSnapshot snapshot)
    {
        final Set<BridgeSubscription> subscriptions = new LinkedHashSet<> (result.desiredBridgeSubscriptions ().domains ());
        final Set<ParameterBankId> banks = new LinkedHashSet<> (result.desiredParameterBanks ().banks ());
        subscriptions.addAll (this.emittedSubscriptions);
        banks.addAll (this.emittedBanks);
        this.emittedSubscriptions.clear ();
        this.emittedBanks.clear ();
        final Map<ControlId, ParameterTargetRef> touches = new LinkedHashMap<> ();
        boolean awaitingCleanup = this.gestures.values ().stream ().anyMatch (gesture -> gesture.finishRevision >= 0);
        for (final Gesture gesture: this.gestures.values ())
            if (gesture.touchAdmitted && gesture.binding.target instanceof final InputTarget.Parameter parameter)
            {
                subscriptions.add (BridgeSubscription.PARAMETERS);
                if (gesture.finishRevision < 0) touches.put (gesture.key.control, parameter.reference ());
                else awaitingCleanup = true;
            }
        final List<CoreEffect> effects = new ArrayList<> (result.effects ());
        effects.addAll (this.cleanup);
        this.cleanup.clear ();
        return new CoreResult (result.desiredOutput (), result.desiredInputRoutes (), new DesiredBridgeSubscriptions (subscriptions), result.desiredClipBindings (), result.desiredControllerState (), result.desiredNoteRepeat (), result.desiredControllerActions (), new DesiredParameterBanks (banks), result.desiredParameterInteraction (), new DesiredParameterTouches (touches), new CoreExecutionRequirements (result.executionRequirements ().ticksRequested () || awaitingCleanup), effects);
    }

    private static Binding binding (final CompiledWorkspace current, final Key key, final Map<ControlId, ParameterSlot> slots, final ControllerSnapshot snapshot)
    {
        final var input = new ControllerInputEvent (0, 0, key.control, key.kind, InputPhase.BEGIN, 1);
        final List<ControllerView> receivers = current.receivers (input);
        final CompiledWorkspace.ActionOwner action = current.actionOwner (input);
        if (receivers.isEmpty () && action == null) return null;
        final List<ControllerView> owners = action != null ? List.of (action.view ()) : receivers.stream ().filter (view -> view.claims ().stream ().anyMatch (claim -> claim.kind () == SurfaceClaim.Kind.EXCLUSIVE_INPUT && claim.area ().contains (input))).toList ();
        final Map<ControllerView, InputTarget> targets = new LinkedHashMap<> ();
        final Set<ControllerView> participants = new LinkedHashSet<> (receivers);
        participants.addAll (owners);
        for (final ControllerView view: participants)
        {
            InputTarget target = view.inputTarget (key.control, key.kind, snapshot);
            if (target == null) continue;
            final ParameterSlot slot = slots.get (key.control);
            if (key.kind == InputKind.TOUCH && slot != null && view.parameterBindings ().containsKey (key.control))
            {
                final var parameter = snapshot.bridge ().parameters ().slots ().get (slot);
                if (parameter != null) target = new InputTarget.Parameter (parameter.target ());
            }
            targets.put (view, target);
        }
        if (targets.isEmpty () || owners.stream ().anyMatch (owner -> !targets.containsKey (owner))) return null;
        final ControllerView principal = owners.isEmpty () ? targets.keySet ().iterator ().next () : owners.get (0);
        final boolean parameterTouch = key.kind == InputKind.TOUCH && principal.parameterTouchControls (snapshot).contains (key.control);
        return new Binding (receivers.stream ().filter (targets::containsKey).toList (), owners, action, targets.get (principal), Map.copyOf (targets), parameterTouch);
    }

    /** Core view input projection: a cancelled physical tail is no longer an active modifier.
     * Only input sets change; host values, target identities and the ingress snapshot stay intact.
     */
    ControllerSnapshot viewSnapshot (final ControllerSnapshot snapshot)
    {
        final Set<ControlId> suppressed = new LinkedHashSet<> ();
        for (final Key key: CONTROLS)
            if (this.lifecycle.isHeld (key) && this.lifecycle.current (key).isEmpty ()) suppressed.add (key.control);
        if (suppressed.isEmpty ()) return snapshot;
        final Set<ControlId> pressed = new LinkedHashSet<> (snapshot.pressedControls ());
        final Set<ControlId> touched = new LinkedHashSet<> (snapshot.touchedControls ());
        pressed.removeAll (suppressed);
        touched.removeAll (suppressed);
        return inputSnapshot (snapshot, pressed, touched);
    }

    private static ControllerSnapshot inputSnapshot (final ControllerSnapshot snapshot, final Set<ControlId> pressed, final Set<ControlId> touched)
    {
        return new ControllerSnapshot (snapshot.revision (), snapshot.monotonicTimeNanos (), snapshot.capabilities (), snapshot.bridge (), snapshot.clipCatalog (), snapshot.armedClipTargets (), snapshot.clipLaunchSessionTargets (), snapshot.activeClipLaunchOwner (), pressed, touched);
    }

    /** Channel pressure describes a group: expose only pads still admitted to this receiver.
     * The host domains are unchanged; raw physical pressed state remains in the ingress snapshot.
     */
    private ControllerSnapshot groupInputSnapshot (final ControllerView view, final ControllerSnapshot snapshot)
    {
        final Set<ControlId> admitted = new LinkedHashSet<> ();
        for (final Gesture gesture: this.gestures.values ())
            if (gesture.key.kind == InputKind.PAD && gesture.receivers.contains (view) && this.lifecycle.current (gesture.key).filter (active -> active.id ().equals (gesture.id)).isPresent ()) admitted.add (gesture.key.control);
        return inputSnapshot (snapshot, admitted, this.viewSnapshot (snapshot).touchedControls ());
    }

    private void reconcileOnce (final ControllerView view, final ControllerSnapshot snapshot)
    {
        if (!this.reconciled.add (view)) return;
        if (!this.started.contains (view) && this.started.size () >= CAPACITY)
            throw new IllegalStateException ("Core view registry exceeds its capacity");
        if (this.started.add (view)) view.start (this.viewSnapshot (snapshot)); else view.reconcile (this.viewSnapshot (snapshot));
    }
    private void retainEffectRequirements (final ControllerView view) { this.emittedSubscriptions.addAll (view.bridgeSubscriptions ()); this.emittedBanks.addAll (view.parameterBanks ()); }
    private static List<ControllerView> intersection (final List<ControllerView> current, final List<ControllerView> original) { return original.stream ().filter (current::contains).toList (); }
    private static Key companion (final CoreEvent event)
    {
        final ControlId control;
        final InputKind kind;
        if (event instanceof final ParameterMutationEvent mutation) { control = mutation.controlId (); kind = InputKind.TOUCH; }
        else if (event instanceof final ControllerInputEvent input)
        {
            control = input.controlId ();
            kind = switch (input.kind ()) { case RELATIVE, ABSOLUTE -> InputKind.TOUCH; case POLY_PRESSURE -> InputKind.PAD; default -> null; };
        }
        else return null;
        final Key key = kind == null ? null : new Key (control, kind);
        return key != null && CONTROLS.contains (key) ? key : null;
    }
    private static Edge edge (final CoreEvent event)
    {
        if (event instanceof final ControllerInputEvent input && input.kind ().isEdge ()) return new Edge (new Key (input.controlId (), input.kind ()), input.phase ());
        if (event instanceof final ButtonInputEvent input) return new Edge (new Key (input.controlId (), CONTROLS.contains (new Key (input.controlId (), InputKind.BUTTON)) ? InputKind.BUTTON : InputKind.PAD), input.pressed () ? InputPhase.BEGIN : InputPhase.END);
        if (event instanceof final TouchInputEvent input) return new Edge (new Key (input.controlId (), InputKind.TOUCH), input.touched () ? InputPhase.BEGIN : InputPhase.END);
        return null;
    }
    private static Set<Key> footprint ()
    {
        final Set<Key> keys = new LinkedHashSet<> ();
        for (final SurfaceArea area: SurfaceArea.values ()) for (final InputKind kind: area.inputKinds ()) if (kind.isEdge ()) for (final ControlId control: area.controls ()) keys.add (new Key (control, kind));
        return Set.copyOf (keys);
    }
    private record Key (ControlId control, InputKind kind) { }
    private record Edge (Key key, InputPhase phase) { }
    private record Binding (List<ControllerView> receivers, List<ControllerView> owners, CompiledWorkspace.ActionOwner action, InputTarget target, Map<ControllerView, InputTarget> targets, boolean parameterTouch)
    {
        boolean sameOwner (final Binding other) { return other != null && this.owners.equals (other.owners) && Objects.equals (this.action, other.action); }
        boolean sameBinding (final Binding other) { return this.sameOwner (other) && this.target.equals (other.target) && this.parameterTouch == other.parameterTouch && this.targets.entrySet ().stream ().allMatch (entry -> !other.targets.containsKey (entry.getKey ()) || entry.getValue ().equals (other.targets.get (entry.getKey ()))); }
    }
    public static final class Dispatch
    {
        private final CoreEvent event;
        private final List<ControllerView> receivers;
        private final Gesture gesture;
        private final Edge edge;
        private final boolean suppressed;
        private boolean delivered;
        private Dispatch (final CoreEvent event, final List<ControllerView> receivers, final Gesture gesture, final Edge edge, final boolean suppressed)
        { this.event = event; this.receivers = receivers; this.gesture = gesture; this.edge = edge; this.suppressed = suppressed; }
        public boolean suppressed () { return this.suppressed; }
    }
    private static final class Gesture
    {
        private final InteractionLifecycle.Id id;
        private final Key key;
        private final Binding binding;
        private List<ControllerView> receivers;
        private boolean longDelivered;
        private boolean touchAdmitted;
        private String project = "";
        private long finishRevision = -1;
        private Gesture (final InteractionLifecycle.Id id, final Key key, final Binding binding) { this.id = id; this.key = key; this.binding = binding; this.receivers = binding.receivers; }
    }
    private static final class PendingAction
    {
        private final Key key;
        private final Binding binding;
        private boolean cancelled;
        private PendingAction (final Key key, final Binding binding) { this.key = key; this.binding = binding; }
    }
}
