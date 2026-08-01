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
import java.util.Set;

/**
 * Test-only shell effect executor.
 */
final class RecordingEffectExecutor
{
    private final List<CoreEffect> executionOrder = new ArrayList<> ();
    private final Map<TimerId, Long> timerDeadlines = new LinkedHashMap<> ();
    private PressClipTargetEffect activeClipLease;
    private DesiredHardwareOutput desiredOutput = DesiredHardwareOutput.empty ();
    private Map<ControlId, ClipTargetId> desiredClipBindings = Map.of ();
    private Set<ControlId> claimedInputs = Set.of ();


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
        this.claimedInputs = result.claimedInputs ();
        for (final CoreEffect effect: result.effects ())
        {
            this.executionOrder.add (effect);
            if (effect instanceof final ScheduleTimerEffect schedule)
                this.timerDeadlines.put (schedule.timerId (), Long.valueOf (schedule.deadlineNanos ()));
            else if (effect instanceof final CancelTimerEffect cancel)
                this.timerDeadlines.remove (cancel.timerId ());
            else if (effect instanceof final PressClipTargetEffect press)
                this.applyPress (press);
            else if (effect instanceof final ReleaseClipTargetsEffect release)
                this.applyRelease (release);
        }
    }


    private void applyPress (final PressClipTargetEffect press)
    {
        this.activeClipLease = press;
    }


    private void applyRelease (final ReleaseClipTargetsEffect release)
    {
        if (this.activeClipLease != null && release.owner ().equals (this.activeClipLease.owner ()))
            this.activeClipLease = null;
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
        return this.activeClipLease != null && owner.equals (this.activeClipLease.owner ()) ? Optional.of (this.activeClipLease) : Optional.empty ();
    }


    /**
     * Get the target retained in the single-active clip-launch session.
     *
     * @return Retained targets by owner
     */
    Map<ControlId, ClipTargetId> clipLaunchSessionTargets ()
    {
        return this.activeClipLease == null ? Map.of () : Map.of (this.activeClipLease.owner (), this.activeClipLease.target ());
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


    /**
     * Get the latest complete core-owned physical-input set.
     *
     * @return Claimed inputs
     */
    Set<ControlId> claimedInputs ()
    {
        return this.claimedInputs;
    }
}
