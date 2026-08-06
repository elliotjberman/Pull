// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.ControllerActionIntent;
import de.mossgrabers.pull.core.api.effect.CoreEffect;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;


/**
 * One active-workspace semantic action and its owning view.
 */
public final class ResolvedControllerAction
{
    private final ControllerActionIntent intent;
    private final Supplier<List<CoreEffect>> dispatch;


    private ResolvedControllerAction (final ControllerActionIntent intent, final Supplier<List<CoreEffect>> dispatch)
    {
        this.intent = Objects.requireNonNull (intent, "intent");
        this.dispatch = Objects.requireNonNull (dispatch, "dispatch");
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


    /** Execute behavior captured when the action was resolved. */
    List<CoreEffect> dispatch ()
    {
        return List.copyOf (Objects.requireNonNull (this.dispatch.get (), "resolved action effects"));
    }
}
