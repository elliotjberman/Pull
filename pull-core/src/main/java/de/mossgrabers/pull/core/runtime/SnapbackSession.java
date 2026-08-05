// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredInputRoutes;
import de.mossgrabers.pull.core.api.DesiredParameterLeases;
import de.mossgrabers.pull.core.api.InputRoute;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.ParameterBridgeSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterValueEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerTickEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.InputKind;
import de.mossgrabers.pull.core.api.event.InputPhase;
import de.mossgrabers.pull.core.api.event.ParameterMutationEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * Reloadable Shift-controlled parameter capture, restoration, and navigation barrier.
 */
final class SnapbackSession
{
    private static final int MAX_PENDING_INPUTS = 64;
    private static final int MAX_SETTLE_TICKS = 8;
    private static final int MAX_RESTORE_TICKS = 16;
    private static final int REQUIRED_SETTLE_CONFIRMATIONS = 2;
    private static final int REQUIRED_RESTORE_CONFIRMATIONS = 2;
    private static final int RESTORE_RETRY_TICKS = 2;

    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final Set<ControlId> TARGET_REBIND_CONTROLS = Set.of (
        button ("ROW1_1"), button ("ROW1_2"), button ("ROW1_3"), button ("ROW1_4"),
        button ("ROW1_5"), button ("ROW1_6"), button ("ROW1_7"), button ("ROW1_8"),
        button ("ROW2_1"), button ("ROW2_2"), button ("ROW2_3"), button ("ROW2_4"),
        button ("ROW2_5"), button ("ROW2_6"), button ("ROW2_7"), button ("ROW2_8"),
        button ("PAGE_LEFT"), button ("PAGE_RIGHT"),
        button ("ARROW_LEFT"), button ("ARROW_RIGHT"), button ("ARROW_UP"), button ("ARROW_DOWN"),
        button ("DEVICE"),
        button ("TRACK"), button ("CLIP"), button ("USER"), button ("SESSION"), button ("NOTE"),
        button ("MASTERTRACK"), button ("ADD_EFFECT"), button ("ADD_TRACK"), button ("BROWSE"));

    private final Map<ParameterTargetRef, Capture> captures = new LinkedHashMap<> (ParameterBridgeSnapshot.TARGET_CAPACITY);
    private final ArrayDeque<ControllerInputEvent> pendingInputs = new ArrayDeque<> (MAX_PENDING_INPUTS);

    private State state = State.IDLE;
    private boolean triggerHeld;
    private int settleTicks;
    private int restoreTicks;


    /** Restore any stable-retained leases inherited across a core hot reload. */
    void start (final ControllerSnapshot snapshot)
    {
        final ControllerSnapshot initial = Objects.requireNonNull (snapshot, "snapshot");
        this.triggerHeld = initial.pressedControls ().contains (SHIFT);
        final ParameterBridgeSnapshot parameters = initial.bridge ().parameters ();
        parameters.retainedBaselines ().forEach ( (target, baseline) -> {
            final Map.Entry<ParameterSlot, ParameterTargetSnapshot> slot = findSlot (parameters, target);
            if (slot != null)
                this.captures.put (target, new Capture (target, slot.getKey (), controlFor (slot.getKey ()), baseline.doubleValue (), slot.getValue ().value ()));
        });
        if (this.captures.isEmpty ())
            this.state = this.triggerHeld ? State.ACTIVE : State.IDLE;
        else
            this.beginSettlement (parameters);
    }


    /** Observe one core event and update the session before workspace dispatch. */
    Update handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        final CoreEvent checkedEvent = Objects.requireNonNull (event, "event");
        final ControllerSnapshot current = Objects.requireNonNull (snapshot, "snapshot");
        final List<CoreEffect> effects = new ArrayList<> ();
        boolean intercepted = false;

        if (checkedEvent instanceof final ControllerInputEvent input)
        {
            if (isShift (input))
                this.handleShift (input, current.bridge ().parameters ());
            else if (this.shouldDefer (input))
            {
                this.enqueue (input);
                if (this.state == State.ACTIVE)
                    this.beginSettlement (current.bridge ().parameters ());
                intercepted = true;
            }
        }
        else if (checkedEvent instanceof final ParameterMutationEvent mutation)
            this.capture (mutation, current.bridge ().parameters ());
        else if (checkedEvent instanceof ControllerTickEvent)
            effects.addAll (this.advance (current.bridge ().parameters ()));

        final List<ControllerInputEvent> released = this.state == State.COMPLETED ? this.complete () : List.of ();
        return new Update (intercepted, released, effects);
    }


    /** Add the complete snapback policy state to a workspace result. */
    CoreResult decorate (final CoreResult workspaceResult, final List<? extends CoreEffect> sessionEffects)
    {
        final CoreResult base = Objects.requireNonNull (workspaceResult, "workspaceResult");
        final Set<BridgeSubscription> subscriptions = new LinkedHashSet<> (base.desiredBridgeSubscriptions ().domains ());
        if (this.triggerHeld || !this.captures.isEmpty ())
            subscriptions.add (BridgeSubscription.PARAMETERS);

        final Map<RouteKey, InputRoute> routes = new LinkedHashMap<> ();
        base.desiredInputRoutes ().routes ().forEach (route -> routes.put (new RouteKey (route.controlId (), route.kind ()), route));
        if (!this.captures.isEmpty ())
        {
            for (final ControlId control: TARGET_REBIND_CONTROLS)
                routes.put (new RouteKey (control, InputKind.BUTTON), new InputRoute (control, InputKind.BUTTON, InputRouteMode.DEFER_STABLE));
        }
        if (this.isRestoring ())
        {
            for (final Capture capture: this.captures.values ())
                routes.put (new RouteKey (capture.control, InputKind.RELATIVE), new InputRoute (capture.control, InputKind.RELATIVE, InputRouteMode.SUPPRESS_STABLE));
        }

        final Map<ParameterTargetRef, Double> baselines = new LinkedHashMap<> ();
        this.captures.forEach ( (target, capture) -> baselines.put (target, Double.valueOf (capture.baseline)));
        final List<CoreEffect> effects = new ArrayList<> (base.effects ());
        effects.addAll (Objects.requireNonNull (sessionEffects, "sessionEffects"));
        return new CoreResult (
            base.desiredOutput (),
            new DesiredInputRoutes (Set.copyOf (routes.values ())),
            new DesiredBridgeSubscriptions (subscriptions),
            base.desiredClipBindings (),
            base.desiredControllerWorkspace (),
            new DesiredParameterLeases (baselines),
            effects);
    }


    private void handleShift (final ControllerInputEvent input, final ParameterBridgeSnapshot parameters)
    {
        if (input.phase () == InputPhase.BEGIN)
        {
            this.triggerHeld = true;
            if (this.state == State.IDLE)
                this.state = State.ACTIVE;
        }
        else if (input.phase () == InputPhase.END)
        {
            this.triggerHeld = false;
            if (this.state == State.ACTIVE)
            {
                if (this.captures.isEmpty ())
                    this.state = State.IDLE;
                else
                    this.beginSettlement (parameters);
            }
        }
    }


    private void capture (final ParameterMutationEvent mutation, final ParameterBridgeSnapshot parameters)
    {
        if (!this.triggerHeld || this.state != State.ACTIVE)
            return;
        final ControlId expectedControl = controlFor (mutation.slot ());
        if (!expectedControl.equals (mutation.controlId ()))
            return;

        for (final Capture capture: this.captures.values ())
        {
            if (capture.slot.equals (mutation.slot ()) && !capture.target.equals (mutation.target ().target ()))
            {
                this.beginSettlement (parameters);
                return;
            }
        }
        if (this.captures.size () >= ParameterBridgeSnapshot.TARGET_CAPACITY)
            return;
        this.captures.putIfAbsent (
            mutation.target ().target (),
            new Capture (mutation.target ().target (), mutation.slot (), mutation.controlId (), mutation.target ().value (), mutation.target ().value ()));
    }


    private List<CoreEffect> advance (final ParameterBridgeSnapshot parameters)
    {
        return switch (this.state)
        {
            case SETTLING -> this.advanceSettlement (parameters);
            case RESTORING -> this.advanceRestoration (parameters);
            default -> List.of ();
        };
    }


    private List<CoreEffect> advanceSettlement (final ParameterBridgeSnapshot parameters)
    {
        boolean settled = true;
        for (final java.util.Iterator<Capture> iterator = this.captures.values ().iterator (); iterator.hasNext ();)
        {
            final Capture capture = iterator.next ();
            final ParameterTargetSnapshot current = parameters.targetOrNull (capture.target);
            if (current == null)
            {
                iterator.remove ();
                continue;
            }
            if (Double.compare (current.value (), capture.lastObservedValue) == 0)
                capture.settleConfirmations++;
            else
            {
                capture.lastObservedValue = current.value ();
                capture.settleConfirmations = 0;
            }
            settled &= capture.settleConfirmations >= REQUIRED_SETTLE_CONFIRMATIONS;
        }
        if (this.captures.isEmpty ())
        {
            this.state = State.COMPLETED;
            return List.of ();
        }
        if (!settled && ++this.settleTicks < MAX_SETTLE_TICKS)
            return List.of ();

        this.state = State.RESTORING;
        this.restoreTicks = 0;
        final List<CoreEffect> effects = new ArrayList<> (this.captures.size ());
        for (final Capture capture: this.captures.values ())
        {
            capture.restoreRetryTicks = 0;
            effects.add (new SetParameterValueEffect (capture.target, capture.baseline));
        }
        return effects;
    }


    private List<CoreEffect> advanceRestoration (final ParameterBridgeSnapshot parameters)
    {
        final List<CoreEffect> effects = new ArrayList<> ();
        for (final java.util.Iterator<Capture> iterator = this.captures.values ().iterator (); iterator.hasNext ();)
        {
            final Capture capture = iterator.next ();
            final ParameterTargetSnapshot current = parameters.targetOrNull (capture.target);
            if (current == null)
            {
                iterator.remove ();
                continue;
            }
            if (current.isAt (capture.baseline))
            {
                capture.restoreRetryTicks = 0;
                if (++capture.restoreConfirmations >= REQUIRED_RESTORE_CONFIRMATIONS)
                    iterator.remove ();
                continue;
            }

            capture.restoreConfirmations = 0;
            if (++capture.restoreRetryTicks >= RESTORE_RETRY_TICKS)
            {
                capture.restoreRetryTicks = 0;
                effects.add (new SetParameterValueEffect (capture.target, capture.baseline));
            }
        }
        if (this.captures.isEmpty ())
            this.state = State.COMPLETED;
        else if (++this.restoreTicks >= MAX_RESTORE_TICKS)
        {
            effects.clear ();
            this.captures.clear ();
            this.state = State.COMPLETED;
        }
        return effects;
    }


    private void beginSettlement (final ParameterBridgeSnapshot parameters)
    {
        if (this.state == State.SETTLING || this.state == State.RESTORING)
            return;
        this.state = State.SETTLING;
        this.settleTicks = 0;
        this.restoreTicks = 0;
        for (final java.util.Iterator<Capture> iterator = this.captures.values ().iterator (); iterator.hasNext ();)
        {
            final Capture capture = iterator.next ();
            final ParameterTargetSnapshot current = parameters.targetOrNull (capture.target);
            if (current == null)
                iterator.remove ();
            else
            {
                capture.lastObservedValue = current.value ();
                capture.settleConfirmations = 0;
            }
        }
        if (this.captures.isEmpty ())
            this.state = State.COMPLETED;
    }


    private List<ControllerInputEvent> complete ()
    {
        final List<ControllerInputEvent> released = List.copyOf (this.pendingInputs);
        this.pendingInputs.clear ();
        this.settleTicks = 0;
        this.restoreTicks = 0;
        this.state = this.triggerHeld ? State.ACTIVE : State.IDLE;
        return released;
    }


    private boolean shouldDefer (final ControllerInputEvent input)
    {
        return input.kind () == InputKind.BUTTON && TARGET_REBIND_CONTROLS.contains (input.controlId ()) && !this.captures.isEmpty ();
    }


    private void enqueue (final ControllerInputEvent input)
    {
        if (this.pendingInputs.size () >= MAX_PENDING_INPUTS)
            throw new IllegalStateException ("Snapback pending input capacity exhausted");
        this.pendingInputs.addLast (input);
    }


    private boolean isRestoring ()
    {
        return this.state == State.SETTLING || this.state == State.RESTORING;
    }


    private static boolean isShift (final ControllerInputEvent input)
    {
        return input.kind () == InputKind.BUTTON && SHIFT.equals (input.controlId ());
    }


    private static Map.Entry<ParameterSlot, ParameterTargetSnapshot> findSlot (final ParameterBridgeSnapshot parameters, final ParameterTargetRef target)
    {
        for (final Map.Entry<ParameterSlot, ParameterTargetSnapshot> slot: parameters.slots ().entrySet ())
        {
            if (target.equals (slot.getValue ().target ()))
                return slot;
        }
        return null;
    }


    private static ControlId controlFor (final ParameterSlot slot)
    {
        return switch (slot.domain ())
        {
            case ACTIVE -> PushControlIds.continuous ("KNOB" + (slot.index () + 1));
            case TEMPO -> PushControlIds.continuous ("TEMPO");
            case MASTER_VOLUME -> PushControlIds.continuous ("MASTER_KNOB");
        };
    }


    private static ControlId button (final String name)
    {
        return PushControlIds.button (name);
    }


    record Update (boolean intercepted, List<ControllerInputEvent> releasedInputs, List<CoreEffect> effects)
    {
        Update
        {
            releasedInputs = List.copyOf (releasedInputs);
            effects = List.copyOf (effects);
        }
    }


    private static final class Capture
    {
        private final ParameterTargetRef target;
        private final ParameterSlot slot;
        private final ControlId control;
        private final double baseline;
        private double lastObservedValue;
        private int settleConfirmations;
        private int restoreConfirmations;
        private int restoreRetryTicks;


        private Capture (final ParameterTargetRef target, final ParameterSlot slot, final ControlId control, final double baseline, final double lastObservedValue)
        {
            this.target = Objects.requireNonNull (target, "target");
            this.slot = Objects.requireNonNull (slot, "slot");
            this.control = Objects.requireNonNull (control, "control");
            this.baseline = baseline;
            this.lastObservedValue = lastObservedValue;
        }
    }


    private record RouteKey (ControlId control, InputKind kind)
    {
    }


    private enum State
    {
        IDLE,
        ACTIVE,
        SETTLING,
        RESTORING,
        COMPLETED
    }
}
