// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerStateScope;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredParameterInteraction;
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
import de.mossgrabers.pull.core.view.ResolvedControllerAction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * Reloadable Shift-controlled parameter capture, restoration, and semantic-action barrier.
 */
final class SnapbackSession
{
    private static final int MAX_SETTLE_TICKS = 8;
    private static final int MAX_RESTORE_TICKS = 16;
    private static final int REQUIRED_SETTLE_CONFIRMATIONS = 2;
    private static final int REQUIRED_RESTORE_CONFIRMATIONS = 2;
    private static final int RESTORE_RETRY_TICKS = 2;

    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final Set<ControllerStateScope> ACTIVE_PARAMETER_SCOPE = Set.of (ControllerStateScope.ACTIVE_PARAMETERS);

    private final Map<ParameterTargetRef, Capture> captures = new LinkedHashMap<> (ParameterSlot.INTERACTION_TARGET_CAPACITY);
    private final ArrayDeque<ResolvedControllerAction> pendingActions = new ArrayDeque<> (DesiredParameterInteraction.PENDING_ACTION_CAPACITY);

    private State state = State.IDLE;
    private boolean triggerHeld;
    private long interactionId;
    private long nextInteractionId = 1;
    private int settleTicks;
    private int restoreTicks;


    /** Restore any stable-retained leases inherited across a core hot reload. */
    void start (final ControllerSnapshot snapshot)
    {
        final ControllerSnapshot initial = Objects.requireNonNull (snapshot, "snapshot");
        this.triggerHeld = initial.pressedControls ().contains (SHIFT);
        if (this.triggerHeld)
            this.beginInteraction ();

        final ParameterBridgeSnapshot parameters = initial.bridge ().parameters ();
        parameters.retainedBaselines ().forEach ( (target, baseline) -> {
            final Map.Entry<ParameterSlot, ParameterTargetSnapshot> slot = findSlot (parameters, target);
            if (slot != null)
            {
                this.ensureInteraction ();
                this.captures.put (target, new Capture (target, slot.getKey (), baseline.doubleValue (), slot.getValue ().value ()));
            }
        });
        if (this.captures.isEmpty ())
            this.state = this.triggerHeld ? State.ACTIVE : State.IDLE;
        else
            this.beginSettlement (parameters);
    }


    /** Observe one non-action core event. */
    Update handle (final CoreEvent event, final ControllerSnapshot snapshot, final ParameterSlot mutationSlot)
    {
        final CoreEvent checkedEvent = Objects.requireNonNull (event, "event");
        final ParameterBridgeSnapshot parameters = Objects.requireNonNull (snapshot, "snapshot").bridge ().parameters ();
        final List<CoreEffect> effects = new ArrayList<> ();

        boolean intercepted = false;
        if (checkedEvent instanceof final ControllerInputEvent input && isShift (input))
            this.handleShift (input, parameters);
        else if (checkedEvent instanceof final ControllerInputEvent input && input.kind () == InputKind.RELATIVE && mutationSlot != null)
            intercepted = !this.captureControllerMutation (mutationSlot, parameters);
        else if (checkedEvent instanceof final ParameterMutationEvent mutation)
            this.capture (mutation, mutationSlot, parameters);
        else if (checkedEvent instanceof ControllerTickEvent)
            effects.addAll (this.advance (parameters));

        return this.finishUpdate (intercepted, effects);
    }


    /** Admit or defer one action already resolved by the active workspace. */
    Update handleAction (final ResolvedControllerAction action, final ControllerSnapshot snapshot)
    {
        final ResolvedControllerAction checkedAction = Objects.requireNonNull (action, "action");
        if (!this.shouldDefer (checkedAction))
            return this.finishUpdate (false, List.of ());

        this.enqueue (checkedAction);
        if (this.state == State.ACTIVE)
            this.beginSettlement (Objects.requireNonNull (snapshot, "snapshot").bridge ().parameters ());
        return this.finishUpdate (true, List.of ());
    }


    /** Add the complete snapback policy state to a workspace result. */
    CoreResult decorate (final CoreResult workspaceResult, final List<? extends CoreEffect> sessionEffects)
    {
        final CoreResult base = Objects.requireNonNull (workspaceResult, "workspaceResult");
        final Set<BridgeSubscription> subscriptions = new LinkedHashSet<> (base.desiredBridgeSubscriptions ().domains ());
        if (this.triggerHeld || !this.captures.isEmpty ())
            subscriptions.add (BridgeSubscription.PARAMETERS);

        final Map<ParameterTargetRef, Double> baselines = new LinkedHashMap<> ();
        this.captures.forEach ( (target, capture) -> baselines.put (target, Double.valueOf (capture.baseline)));
        final DesiredParameterInteraction interaction;
        if (this.interactionId == 0)
            interaction = DesiredParameterInteraction.empty ();
        else
        {
            interaction = new DesiredParameterInteraction (
                this.interactionId,
                this.triggerHeld && this.state == State.ACTIVE,
                baselines,
                this.isRestoring () ? baselines.keySet () : Set.of (),
                baselines.isEmpty () ? Set.of () : ACTIVE_PARAMETER_SCOPE,
                this.pendingActions.size ());
        }

        final List<CoreEffect> effects = new ArrayList<> (base.effects ());
        effects.addAll (Objects.requireNonNull (sessionEffects, "sessionEffects"));
        return new CoreResult (
            base.desiredOutput (),
            base.desiredInputRoutes (),
            new DesiredBridgeSubscriptions (subscriptions),
            base.desiredClipBindings (),
            base.desiredControllerWorkspace (),
            base.desiredControllerLayout (),
            base.desiredNoteRepeat (),
            base.desiredControllerActions (),
            base.desiredParameterBanks (),
            interaction,
            base.executionRequirements (),
            effects);
    }


    private void handleShift (final ControllerInputEvent input, final ParameterBridgeSnapshot parameters)
    {
        if (input.phase () == InputPhase.BEGIN)
        {
            this.triggerHeld = true;
            if (this.state == State.IDLE)
            {
                this.beginInteraction ();
                this.state = State.ACTIVE;
            }
        }
        else if (input.phase () == InputPhase.END)
        {
            this.triggerHeld = false;
            if (this.state == State.ACTIVE)
            {
                if (this.captures.isEmpty ())
                {
                    this.state = State.IDLE;
                    this.interactionId = 0;
                }
                else
                    this.beginSettlement (parameters);
            }
        }
    }


    private void capture (final ParameterMutationEvent mutation, final ParameterSlot mappedSlot, final ParameterBridgeSnapshot parameters)
    {
        if (!this.triggerHeld || this.state != State.ACTIVE || mappedSlot == null)
            return;

        this.capture (mutation.target (), mappedSlot, mutation.target ().value (), parameters);
    }


    private boolean captureControllerMutation (final ParameterSlot mappedSlot, final ParameterBridgeSnapshot parameters)
    {
        final ParameterTargetSnapshot authoritative = parameters.slots ().get (mappedSlot);
        if (authoritative == null)
            return false;
        if (this.isRestoring () && this.captures.containsKey (authoritative.target ()))
            return false;
        if (!this.triggerHeld || this.state != State.ACTIVE)
            return true;
        return this.capture (authoritative, mappedSlot, authoritative.value (), parameters);
    }


    private boolean capture (final ParameterTargetSnapshot observed, final ParameterSlot mappedSlot, final double baseline, final ParameterBridgeSnapshot parameters)
    {

        for (final Capture capture: this.captures.values ())
        {
            if (capture.slot.equals (mappedSlot) && !capture.target.equals (observed.target ()))
            {
                this.beginSettlement (parameters);
                return false;
            }
        }

        final ParameterTargetSnapshot authoritative = parameters.slots ().get (mappedSlot);
        if (authoritative == null || !authoritative.target ().equals (observed.target ()))
            return false;
        if (this.captures.size () >= ParameterSlot.INTERACTION_TARGET_CAPACITY)
            return false;

        this.ensureInteraction ();
        this.captures.putIfAbsent (
            authoritative.target (),
            new Capture (authoritative.target (), mappedSlot, baseline, authoritative.value ()));
        return true;
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


    private Update finishUpdate (final boolean intercepted, final List<? extends CoreEffect> effects)
    {
        final List<ResolvedControllerAction> released = this.state == State.COMPLETED ? this.complete () : List.of ();
        return new Update (intercepted, released, new ArrayList<> (effects));
    }


    private List<ResolvedControllerAction> complete ()
    {
        final List<ResolvedControllerAction> released = List.copyOf (this.pendingActions);
        this.pendingActions.clear ();
        this.settleTicks = 0;
        this.restoreTicks = 0;
        this.interactionId = 0;
        this.state = this.triggerHeld ? State.ACTIVE : State.IDLE;
        if (this.triggerHeld)
            this.beginInteraction ();
        return released;
    }


    private boolean shouldDefer (final ResolvedControllerAction action)
    {
        return !this.captures.isEmpty () && action.intent ().invalidates ().contains (ControllerStateScope.ACTIVE_PARAMETERS);
    }


    private void enqueue (final ResolvedControllerAction action)
    {
        if (this.pendingActions.size () >= DesiredParameterInteraction.PENDING_ACTION_CAPACITY)
            throw new IllegalStateException ("Snapback pending action capacity exhausted");
        this.pendingActions.addLast (action);
    }


    private void beginInteraction ()
    {
        if (this.nextInteractionId == Long.MAX_VALUE)
            throw new IllegalStateException ("Parameter interaction identity sequence exhausted");
        this.interactionId = this.nextInteractionId++;
    }


    private void ensureInteraction ()
    {
        if (this.interactionId == 0)
            this.beginInteraction ();
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


    record Update (boolean intercepted, List<ResolvedControllerAction> releasedActions, List<CoreEffect> effects)
    {
        Update
        {
            releasedActions = List.copyOf (releasedActions);
            effects = List.copyOf (effects);
        }
    }


    private static final class Capture
    {
        private final ParameterTargetRef target;
        private final ParameterSlot slot;
        private final double baseline;
        private double lastObservedValue;
        private int settleConfirmations;
        private int restoreConfirmations;
        private int restoreRetryTicks;


        private Capture (final ParameterTargetRef target, final ParameterSlot slot, final double baseline, final double lastObservedValue)
        {
            this.target = Objects.requireNonNull (target, "target");
            this.slot = Objects.requireNonNull (slot, "slot");
            this.baseline = baseline;
            this.lastObservedValue = lastObservedValue;
        }
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
