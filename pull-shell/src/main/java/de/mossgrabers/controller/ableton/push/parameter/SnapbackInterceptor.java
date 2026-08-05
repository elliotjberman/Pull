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
 * Shift-triggered session layer around the controller parameter mutation gateway.
 */
public final class SnapbackInterceptor implements ParameterMutationGateway
{
    private static final int MAX_CAPTURES = 16;
    private static final int MAX_PENDING_ACTIONS = 64;
    private static final int MAX_RESTORE_TICKS = 16;

    private final ParameterMutationGateway delegate;
    private final Consumer<String> warningSink;
    private final Map<ParameterTargetRef, Capture> captures = new LinkedHashMap<> ();
    private final Queue<Runnable> pendingActions = new ArrayDeque<> (MAX_PENDING_ACTIONS);

    private State state = State.IDLE;
    private boolean triggerHeld;
    private int restoreTicks;


    /**
     * Constructor.
     *
     * @param delegate Mutation gateway to decorate
     * @param warningSink Warning sink
     */
    public SnapbackInterceptor (final ParameterMutationGateway delegate, final Consumer<String> warningSink)
    {
        this.delegate = Objects.requireNonNull (delegate, "delegate");
        this.warningSink = Objects.requireNonNull (warningSink, "warningSink");
    }


    /** {@inheritDoc} */
    @Override
    public void mutate (final ParameterMutationRequest request)
    {
        final ParameterMutationRequest checkedRequest = Objects.requireNonNull (request, "request");
        if (checkedRequest.persistence () == ParameterMutationRequest.PersistencePolicy.PERSISTENT)
        {
            this.delegate.mutate (checkedRequest);
            return;
        }

        if (this.state == State.RESTORING)
            return;
        if (!this.triggerHeld)
        {
            this.delegate.mutate (checkedRequest);
            return;
        }

        final ParameterMutationTarget target = checkedRequest.target ().orElseThrow ();
        if (!target.isCurrent ())
        {
            this.warningSink.accept ("Rejected snapback mutation for stale target " + target.reference ());
            return;
        }

        if (!this.captures.containsKey (target.reference ()))
        {
            if (this.captures.size () >= MAX_CAPTURES)
            {
                this.warningSink.accept ("Rejected snapback mutation because the bounded capture set was full");
                return;
            }
            this.captures.put (target.reference (), new Capture (target, target.readAuthoritativeValue ()));
        }
        this.delegate.mutate (checkedRequest);
    }


    /**
     * Open one trigger session.
     */
    public void triggerPressed ()
    {
        this.triggerHeld = true;
        if (this.state == State.IDLE)
            this.state = State.ACTIVE;
    }


    /**
     * End the trigger session and request restoration of every retained target.
     */
    public void triggerReleased ()
    {
        this.triggerHeld = false;
        if (this.state == State.RESTORING)
            return;

        if (this.captures.isEmpty ())
        {
            this.state = State.IDLE;
            return;
        }

        this.beginRestoration ();
    }


    /**
     * Serialize an action which may rebind a retained proxy target.
     *
     * @param action Navigation action
     */
    public void beforePotentialTargetRebind (final Runnable action)
    {
        final Runnable checkedAction = Objects.requireNonNull (action, "action");
        if (this.state == State.RESTORING)
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
        this.beginRestoration ();
    }


    /**
     * Defer the core-observed half of a potential target-rebinding edge until restoration has
     * completed. The stable half enters the same queue first, preserving OBSERVE ordering.
     *
     * @param action Core event delivery
     */
    public void afterPotentialTargetRebind (final Runnable action)
    {
        final Runnable checkedAction = Objects.requireNonNull (action, "action");
        if (this.state == State.RESTORING)
        {
            this.enqueue (checkedAction);
            return;
        }
        checkedAction.run ();
    }


    /**
     * Reconcile retained targets with authoritative host read-back.
     */
    public void tick ()
    {
        if (this.state == State.ACTIVE)
        {
            if (this.captures.values ().stream ().anyMatch (capture -> !capture.target.isCurrent ()))
                this.beginRestoration ();
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
                iterator.remove ();
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
    public void shutdown ()
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
        this.restoreTicks = 0;
    }


    boolean isRestoring ()
    {
        return this.state == State.RESTORING;
    }


    int captureCount ()
    {
        return this.captures.size ();
    }


    private void beginRestoration ()
    {
        if (this.state == State.RESTORING)
            return;
        this.state = State.RESTORING;
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
        RESTORING
    }


    private record Capture (ParameterMutationTarget target, double baseline)
    {
        private Capture
        {
            Objects.requireNonNull (target, "target");
        }
    }
}
