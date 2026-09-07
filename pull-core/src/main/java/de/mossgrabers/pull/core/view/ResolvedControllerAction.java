// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.ControllerActionIntent;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.effect.ConsumeControllerButtonEffect;
import de.mossgrabers.pull.core.api.effect.CoreEffect;

import java.util.List;
import java.util.Objects;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.function.Supplier;


/**
 * One active-workspace semantic action and its owning view.
 */
public final class ResolvedControllerAction
{
    private final ControllerActionIntent intent;
    private final Supplier<List<CoreEffect>> dispatch;
    private final Supplier<List<CoreEffect>> cancellation;
    private final List<ConsumeControllerButtonEffect> immediateEffects;


    private ResolvedControllerAction (final ControllerActionIntent intent, final Supplier<List<CoreEffect>> dispatch)
    {
        this (intent, dispatch, List.of (), List::of);
    }


    private ResolvedControllerAction (final ControllerActionIntent intent, final Supplier<List<CoreEffect>> dispatch, final List<ConsumeControllerButtonEffect> immediateEffects, final Supplier<List<CoreEffect>> cancellation)
    {
        this.intent = Objects.requireNonNull (intent, "intent");
        this.dispatch = Objects.requireNonNull (dispatch, "dispatch");
        this.immediateEffects = List.copyOf (immediateEffects);
        this.cancellation = Objects.requireNonNull (cancellation, "cancellation");
    }


    /** Create an immutable resolved action. */
    public static ResolvedControllerAction of (final ControllerActionIntent intent, final Supplier<List<CoreEffect>> dispatch)
    {
        return new ResolvedControllerAction (intent, dispatch);
    }


    /** Create a stable-owned action whose behavior executes outside the core. */
    public static ResolvedControllerAction stable (final ControllerActionIntent intent)
    {
        return new ResolvedControllerAction (intent, List::of);
    }


    /** Get the immutable semantic intent. */
    public ControllerActionIntent intent ()
    {
        return this.intent;
    }


    /**
     * Consume existing button gestures before waiting for semantic-action admission. These are
     * input-lifecycle effects only: parameter, transport, and page effects still wait for dispatch.
     */
    public ResolvedControllerAction withImmediateConsumption (final ControlId... controls)
    {
        final LinkedHashSet<ControlId> distinct = new LinkedHashSet<> ();
        this.immediateEffects.forEach (effect -> distinct.add (effect.controlId ()));
        for (final ControlId control: Objects.requireNonNull (controls, "controls"))
            distinct.add (Objects.requireNonNull (control, "consumed control"));
        if (distinct.size () > 8)
            throw new IllegalArgumentException ("one action may consume at most eight button gestures");
        final List<ConsumeControllerButtonEffect> effects = new ArrayList<> (distinct.size ());
        distinct.forEach (control -> effects.add (new ConsumeControllerButtonEffect (control)));
        return new ResolvedControllerAction (this.intent, this.dispatch, effects, this.cancellation);
    }


    /** Cleanup of this exact deferred intent, even if its physical press has already ended. */
    public ResolvedControllerAction onCancellation (final Supplier<List<CoreEffect>> cleanup)
    {
        return new ResolvedControllerAction (this.intent, this.dispatch, this.immediateEffects, cleanup);
    }


    /** Called once by the routing owner; never resolve a new gesture here. */
    List<CoreEffect> cancel ()
    {
        return List.copyOf (Objects.requireNonNull (this.cancellation.get (), "cancelled action effects"));
    }


    /** Effects emitted once at the original physical BEGIN, never when a deferred action resumes. */
    public List<ConsumeControllerButtonEffect> immediateEffects ()
    {
        return this.immediateEffects;
    }


    /** Execute behavior captured when the action was resolved. */
    List<CoreEffect> dispatch ()
    {
        return List.copyOf (Objects.requireNonNull (this.dispatch.get (), "resolved action effects"));
    }
}
