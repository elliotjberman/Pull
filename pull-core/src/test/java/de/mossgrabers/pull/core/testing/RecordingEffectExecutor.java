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
import de.mossgrabers.pull.core.api.effect.ReactivateClipTargetEffect;
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
    private final List<PressClipTargetEffect> clipLaunchChain = new ArrayList<> ();
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
                this.applyPress (press);
            else if (effect instanceof final ReactivateClipTargetEffect reactivate)
                this.applyReactivate (reactivate);
            else if (effect instanceof final ReleaseClipTargetsEffect release)
                this.applyRelease (release);
        }
    }


    private void applyPress (final PressClipTargetEffect press)
    {
        if (this.indexOfOwner (press.owner ()) >= 0)
            throw new IllegalStateException ("A retained owner must be reactivated without another press");
        this.clipLaunchChain.add (press);
    }


    private void applyReactivate (final ReactivateClipTargetEffect reactivate)
    {
        final int retainedIndex = this.indexOfOwner (reactivate.owner ());
        if (retainedIndex < 0)
            throw new IllegalStateException ("Cannot reactivate an owner outside the clip-launch session");
        while (this.clipLaunchChain.size () > retainedIndex + 1)
            this.clipLaunchChain.removeLast ();
    }


    private void applyRelease (final ReleaseClipTargetsEffect release)
    {
        final int ownerIndex = this.indexOfOwner (release.owner ());
        if (ownerIndex == this.clipLaunchChain.size () - 1)
            this.clipLaunchChain.clear ();
    }


    private int indexOfOwner (final ControlId owner)
    {
        for (int index = 0; index < this.clipLaunchChain.size (); index++)
        {
            if (owner.equals (this.clipLaunchChain.get (index).owner ()))
                return index;
        }
        return -1;
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
        final int index = this.indexOfOwner (owner);
        return index < 0 ? Optional.empty () : Optional.of (this.clipLaunchChain.get (index));
    }


    /**
     * Get every target retained in the ordered clip-launch session.
     *
     * @return Retained targets by owner
     */
    Map<ControlId, ClipTargetId> clipLaunchSessionTargets ()
    {
        final Map<ControlId, ClipTargetId> targets = new LinkedHashMap<> ();
        for (final PressClipTargetEffect frame: this.clipLaunchChain)
            targets.put (frame.owner (), frame.target ());
        return Map.copyOf (targets);
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
