// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.parameter;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.function.Consumer;


/**
 * Shift-triggered session layer around eligible controller parameter mutations.
 */
final class SnapbackInterceptor
{
    private static final int MAX_CAPTURES = 16;
    private static final int MAX_PENDING_ACTIONS = 64;
    private static final int MAX_SETTLE_TICKS = 8;
    private static final int MAX_RESTORE_TICKS = 16;
    private static final int REQUIRED_SETTLE_CONFIRMATIONS = 2;
    private static final int REQUIRED_RESTORE_CONFIRMATIONS = 2;

    private final Consumer<String> warningSink;
    private final Map<ParameterTargetRef, Capture> captures = new LinkedHashMap<> ();
    private final Queue<Runnable> pendingActions = new ArrayDeque<> (MAX_PENDING_ACTIONS);

    private State state = State.IDLE;
    private boolean triggerHeld;
    private int settleTicks;
    private int restoreTicks;


    /**
     * Constructor.
     *
     * @param warningSink Warning sink
     */
    SnapbackInterceptor (final Consumer<String> warningSink)
    {
        this.warningSink = Objects.requireNonNull (warningSink, "warningSink");
    }


    /**
     * Apply one snapback-eligible mutation.
     *
     * @param target Current target
     * @param mutation Mutation to apply
     */
    void mutate (final ParameterMutationTarget target, final Runnable mutation)
    {
        final ParameterMutationTarget checkedTarget = Objects.requireNonNull (target, "target");
        final Runnable checkedMutation = Objects.requireNonNull (mutation, "mutation");

        if (this.isRestoring ())
            return;
        if (!this.triggerHeld)
        {
            checkedMutation.run ();
            return;
        }

        if (!checkedTarget.isCurrent ())
        {
            this.warningSink.accept ("Rejected snapback mutation for stale target " + checkedTarget.reference ());
            return;
        }

        if (!this.captures.containsKey (checkedTarget.reference ()))
        {
            if (this.captures.size () >= MAX_CAPTURES)
            {
                this.warningSink.accept ("Rejected snapback mutation because the bounded capture set was full");
                return;
            }
            this.captures.put (checkedTarget.reference (), new Capture (checkedTarget, checkedTarget.readAuthoritativeValue ()));
        }
        checkedMutation.run ();
    }


    /**
     * Open one trigger session.
     */
    void triggerPressed ()
    {
        this.triggerHeld = true;
        if (this.state == State.IDLE)
            this.state = State.ACTIVE;
    }


    /**
     * End the trigger session and request restoration of every retained target.
     */
    void triggerReleased ()
    {
        this.triggerHeld = false;
        if (this.isRestoring ())
            return;

        if (this.captures.isEmpty ())
        {
            this.state = State.IDLE;
            return;
        }

        this.beginSettlement ();
    }


    /**
     * Serialize an action which may rebind a retained proxy target.
     *
     * @param action Navigation action
     */
    void beforePotentialTargetRebind (final Runnable action)
    {
        final Runnable checkedAction = Objects.requireNonNull (action, "action");
        if (this.isRestoring ())
        {
            this.enqueue (checkedAction);
            return;
        }
        if (this.captures.isEmpty ())
        {
            checkedAction.run ();
            return;
        }

        this.enqueue (checkedAction);
        this.beginSettlement ();
    }


    /**
     * Defer the core-observed half of a potential target-rebinding edge until restoration has
     * completed. The stable half enters the same queue first, preserving OBSERVE ordering.
     *
     * @param action Core event delivery
     */
    void afterPotentialTargetRebind (final Runnable action)
    {
        final Runnable checkedAction = Objects.requireNonNull (action, "action");
        if (this.isRestoring ())
        {
            this.enqueue (checkedAction);
            return;
        }
        checkedAction.run ();
    }


    /**
     * Reconcile retained targets with authoritative host read-back.
     */
    void tick ()
    {
        if (this.state == State.ACTIVE)
        {
            if (this.captures.values ().stream ().anyMatch (capture -> !capture.target.isCurrent ()))
                this.beginSettlement ();
            return;
        }
        if (this.state == State.SETTLING)
        {
            this.tickSettlement ();
            return;
        }
        if (this.state != State.RESTORING)
            return;

        final Iterator<Map.Entry<ParameterTargetRef, Capture>> iterator = this.captures.entrySet ().iterator ();
        while (iterator.hasNext ())
        {
            final Capture capture = iterator.next ().getValue ();
            if (!capture.target.isCurrent ())
            {
                this.warningSink.accept ("Abandoned snapback restoration for rebound target " + capture.target.reference ());
                iterator.remove ();
            }
            else if (capture.target.isAt (capture.baseline))
            {
                if (++capture.restoreConfirmations >= REQUIRED_RESTORE_CONFIRMATIONS)
                    iterator.remove ();
            }
            else if (capture.restoreConfirmations > 0)
            {
                capture.restoreConfirmations = 0;
                try
                {
                    capture.target.restore (capture.baseline);
                }
                catch (final RuntimeException ex)
                {
                    this.warningSink.accept ("Failed snapback restoration retry for " + capture.target.reference () + ": " + ex.getMessage ());
                    iterator.remove ();
                }
            }
        }
        if (this.captures.isEmpty ())
            this.completeRestoration ();
        else if (++this.restoreTicks >= MAX_RESTORE_TICKS)
        {
            this.warningSink.accept ("Timed out waiting for authoritative snapback restoration; released " + this.captures.size () + " retained target(s)");
            this.captures.clear ();
            this.completeRestoration ();
        }
    }


    /**
     * Best-effort restoration before the stable shell exits.
     */
    void shutdown ()
    {
        for (final Capture capture: this.captures.values ())
        {
            if (!capture.target.isCurrent ())
                continue;
            try
            {
                capture.target.restore (capture.baseline);
            }
            catch (final RuntimeException ex)
            {
                this.warningSink.accept ("Failed terminal snapback restoration for " + capture.target.reference () + ": " + ex.getMessage ());
            }
        }
        this.captures.clear ();
        this.pendingActions.clear ();
        this.triggerHeld = false;
        this.state = State.IDLE;
        this.settleTicks = 0;
        this.restoreTicks = 0;
    }


    boolean isRestoring ()
    {
        return this.state == State.SETTLING || this.state == State.RESTORING;
    }


    boolean isInterceptingMutations ()
    {
        return this.state != State.IDLE;
    }


    int captureCount ()
    {
        return this.captures.size ();
    }


    private void beginSettlement ()
    {
        if (this.isRestoring ())
            return;
        this.state = State.SETTLING;
        this.settleTicks = 0;

        final Iterator<Map.Entry<ParameterTargetRef, Capture>> iterator = this.captures.entrySet ().iterator ();
        while (iterator.hasNext ())
        {
            final Capture capture = iterator.next ().getValue ();
            if (!capture.target.isCurrent ())
            {
                this.warningSink.accept ("Skipped snapback settlement for rebound target " + capture.target.reference ());
                iterator.remove ();
                continue;
            }
            capture.lastObservedValue = capture.target.readAuthoritativeValue ();
            capture.settleConfirmations = 0;
        }
        if (this.captures.isEmpty ())
            this.completeRestoration ();
    }


    private void tickSettlement ()
    {
        boolean settled = true;
        final Iterator<Map.Entry<ParameterTargetRef, Capture>> iterator = this.captures.entrySet ().iterator ();
        while (iterator.hasNext ())
        {
            final Capture capture = iterator.next ().getValue ();
            if (!capture.target.isCurrent ())
            {
                this.warningSink.accept ("Abandoned snapback settlement for rebound target " + capture.target.reference ());
                iterator.remove ();
                continue;
            }

            final double currentValue = capture.target.readAuthoritativeValue ();
            if (Double.compare (currentValue, capture.lastObservedValue) == 0)
                capture.settleConfirmations++;
            else
            {
                capture.lastObservedValue = currentValue;
                capture.settleConfirmations = 0;
            }
            settled &= capture.settleConfirmations >= REQUIRED_SETTLE_CONFIRMATIONS;
        }

        if (this.captures.isEmpty ())
            this.completeRestoration ();
        else if (settled || ++this.settleTicks >= MAX_SETTLE_TICKS)
            this.beginRestoration ();
    }


    private void beginRestoration ()
    {
        if (this.state == State.RESTORING)
            return;
        this.state = State.RESTORING;
        this.settleTicks = 0;
        this.restoreTicks = 0;

        final Iterator<Map.Entry<ParameterTargetRef, Capture>> iterator = this.captures.entrySet ().iterator ();
        while (iterator.hasNext ())
        {
            final Capture capture = iterator.next ().getValue ();
            if (!capture.target.isCurrent ())
            {
                this.warningSink.accept ("Skipped snapback restoration for rebound target " + capture.target.reference ());
                iterator.remove ();
                continue;
            }
            try
            {
                capture.target.restore (capture.baseline);
            }
            catch (final RuntimeException ex)
            {
                this.warningSink.accept ("Failed snapback restoration for " + capture.target.reference () + ": " + ex.getMessage ());
                iterator.remove ();
            }
        }
        if (this.captures.isEmpty ())
            this.completeRestoration ();
    }


    private void completeRestoration ()
    {
        while (!this.pendingActions.isEmpty ())
        {
            try
            {
                this.pendingActions.remove ().run ();
            }
            catch (final RuntimeException ex)
            {
                this.warningSink.accept ("Deferred controller action failed after snapback restoration: " + ex.getMessage ());
            }
        }
        this.state = this.triggerHeld ? State.ACTIVE : State.IDLE;
        this.settleTicks = 0;
        this.restoreTicks = 0;
    }


    private void enqueue (final Runnable action)
    {
        if (this.pendingActions.size () >= MAX_PENDING_ACTIONS)
        {
            this.warningSink.accept ("Dropped controller action while the bounded snapback restoration queue was full");
            return;
        }
        this.pendingActions.add (action);
    }


    private enum State
    {
        IDLE,
        ACTIVE,
        SETTLING,
        RESTORING
    }


    private static final class Capture
    {
        private final ParameterMutationTarget target;
        private final double baseline;
        private double lastObservedValue;
        private int settleConfirmations;
        private int restoreConfirmations;


        private Capture (final ParameterMutationTarget target, final double baseline)
        {
            this.target = Objects.requireNonNull (target, "target");
            this.baseline = baseline;
        }
    }
}
