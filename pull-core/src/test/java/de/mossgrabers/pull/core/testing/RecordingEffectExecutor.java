// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.TimerId;
import de.mossgrabers.pull.core.api.effect.CancelTimerEffect;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.PressClipTargetEffect;
import de.mossgrabers.pull.core.api.effect.ReleaseClipTargetsEffect;
import de.mossgrabers.pull.core.api.effect.ScheduleTimerEffect;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Test-only shell effect executor.
 */
final class RecordingEffectExecutor
{
    private final List<CoreEffect> executionOrder = new ArrayList<> ();
    private final Map<TimerId, Long> timerDeadlines = new LinkedHashMap<> ();
    private final Map<ControlId, PressClipTargetEffect> clipLeases = new LinkedHashMap<> ();
    private DesiredHardwareOutput desiredOutput = DesiredHardwareOutput.empty ();
    private Map<ControlId, ClipTargetId> desiredClipBindings = Map.of ();


    /**
     * Apply one core result as the shell would after a successful call.
     *
     * @param result The result
     */
    void apply (final CoreResult result)
    {
        Objects.requireNonNull (result, "result");
        this.desiredOutput = result.desiredOutput ();
        this.desiredClipBindings = result.desiredClipBindings ();
        for (final CoreEffect effect: result.effects ())
        {
            this.executionOrder.add (effect);
            if (effect instanceof final ScheduleTimerEffect schedule)
                this.timerDeadlines.put (schedule.timerId (), Long.valueOf (schedule.deadlineNanos ()));
            else if (effect instanceof final CancelTimerEffect cancel)
                this.timerDeadlines.remove (cancel.timerId ());
            else if (effect instanceof final PressClipTargetEffect press)
                this.clipLeases.put (press.owner (), press);
            else if (effect instanceof final ReleaseClipTargetsEffect release)
                this.clipLeases.remove (release.owner ());
        }
    }


    /**
     * Remove and return the next timer due at or before the supplied time.
     *
     * @param nowNanos The current time
     * @return The next timer in deterministic deadline and identifier order
     */
    Optional<TimerId> takeNextDueTimer (final long nowNanos)
    {
        final Optional<Map.Entry<TimerId, Long>> dueEntry = this.timerDeadlines.entrySet ().stream ()
            .filter (entry -> entry.getValue ().longValue () <= nowNanos)
            .min (Comparator.comparingLong ((Map.Entry<TimerId, Long> entry) -> entry.getValue ().longValue ()).thenComparing (entry -> entry.getKey ().value ()));
        if (dueEntry.isEmpty ())
            return Optional.empty ();

        final TimerId timerId = dueEntry.get ().getKey ();
        this.timerDeadlines.remove (timerId);
        return Optional.of (timerId);
    }


    /**
     * Get a timer deadline.
     *
     * @param timerId The timer
     * @return Its deadline, if scheduled
     */
    OptionalLong deadline (final TimerId timerId)
    {
        final Long deadline = this.timerDeadlines.get (timerId);
        return deadline == null ? OptionalLong.empty () : OptionalLong.of (deadline.longValue ());
    }


    /**
     * Get the active clip lease for an owner.
     *
     * @param owner The logical owner
     * @return Its lease, if pressed
     */
    Optional<PressClipTargetEffect> clipLease (final ControlId owner)
    {
        return Optional.ofNullable (this.clipLeases.get (owner));
    }


    /**
     * Get effects in execution order.
     *
     * @return A defensive copy
     */
    List<CoreEffect> executionOrder ()
    {
        return List.copyOf (this.executionOrder);
    }


    /**
     * Get the latest complete desired output.
     *
     * @return The desired output
     */
    DesiredHardwareOutput desiredOutput ()
    {
        return this.desiredOutput;
    }


    /**
     * Get the latest complete desired clip bindings.
     *
     * @return The desired bindings
     */
    Map<ControlId, ClipTargetId> desiredClipBindings ()
    {
        return this.desiredClipBindings;
    }
}
