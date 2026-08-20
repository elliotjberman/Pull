// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.effect.CoreEffect;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;


/**
 * One bounded toggle lane whose dependent writes wait for authoritative host read-back.
 *
 * @param <T> Immutable target identity
 */
final class AuthoritativeBooleanToggle<T>
{
    private static final long ACKNOWLEDGEMENT_TIMEOUT_NANOS = 5_000_000_000L;

    private T       target;
    private boolean expected;
    private boolean queued;
    private long    submittedAtNanos;


    /** Advance read-back and optionally register one new press. */
    List<CoreEffect> update (final T observedTarget, final boolean observedValue, final long nowNanos, final boolean pressed, final BiFunction<T, Boolean, CoreEffect> effectFactory)
    {
        final T checkedTarget = Objects.requireNonNull (observedTarget, "observedTarget");
        final BiFunction<T, Boolean, CoreEffect> checkedFactory = Objects.requireNonNull (effectFactory, "effectFactory");
        CoreEffect effect = null;
        if (this.target != null && !this.target.equals (checkedTarget))
            this.clear ();
        if (this.target != null)
        {
            if (observedValue == this.expected)
            {
                if (this.queued)
                {
                    this.queued = false;
                    this.expected = !observedValue;
                    this.submittedAtNanos = nowNanos;
                    effect = checkedFactory.apply (checkedTarget, Boolean.valueOf (this.expected));
                }
                else
                    this.clear ();
            }
            else if (nowNanos - this.submittedAtNanos >= ACKNOWLEDGEMENT_TIMEOUT_NANOS)
                this.clear ();
        }

        if (pressed)
        {
            if (this.target == null)
            {
                this.target = checkedTarget;
                this.expected = !observedValue;
                this.submittedAtNanos = nowNanos;
                effect = checkedFactory.apply (checkedTarget, Boolean.valueOf (this.expected));
            }
            else
                this.queued = !this.queued;
        }
        return effect == null ? List.of () : List.of (effect);
    }


    /** Retire pending intent when its authoritative target is unavailable. */
    void clear ()
    {
        this.target = null;
        this.queued = false;
    }
}
