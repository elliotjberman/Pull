// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.interaction;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Bounded, single-threaded lifecycle for target-bound input. No hardware, host, timers or callbacks.
 * A changed binding cancels an interaction; its physical tail stays suppressed until release.
 * Submitted operations and final cleanup have independent lifetimes and explicit completion.
 *
 * <p>Use one instance per externally assigned generation; never reuse a generation. C and T must
 * be immutable value identities. T identifies an exact target incarnation, never a moving slot.
 * Physical routing and target observation adapters use the same cancellation and retirement rules.</p>
 *
 * @param <C> Logical physical control identity (all companion events share this identity)
 * @param <T> Exact target identity, including its generation where applicable
 */
public final class InteractionLifecycle<C, T>
{
    public record Id (long generation, long sequence) {}
    public record OperationId (Id interaction, long sequence) {}
    public record Interaction<C, T> (Id id, C control, T target) {}
    public record Operation<T> (OperationId id, T target) {}
    public enum Admission { STARTED, ALREADY_HELD, UNBOUND, TARGET_BUSY, CAPACITY_EXHAUSTED, STOPPED }
    public record Begin<C, T> (Admission admission, Optional<Interaction<C, T>> interaction) {}
    public enum EndReason { RELEASED, BINDING_CHANGED, TARGET_LOST, STOPPED }
    public record Finish<T> (Id interaction, T target, EndReason reason) {}

    private final long generation;
    private final Set<C> controls;
    private final int maxInteractions;
    private final int maxOperations;
    private final Map<Id, Session> sessions = new LinkedHashMap<> ();
    // Empty means the press was rejected. A retired ID still suppresses its physical tail.
    private final Map<C, Optional<Id>> held = new LinkedHashMap<> ();
    private Map<C, T> bindings = Map.of ();
    private long nextInteraction;
    private boolean stopped;

    public InteractionLifecycle (final long generation, final Set<C> controls, final int maxInteractions, final int maxOperations)
    {
        if (generation < 0 || maxInteractions <= 0 || maxOperations <= 0)
            throw new IllegalArgumentException ("Nonnegative generation and positive capacities required");
        this.generation = generation;
        this.controls = Set.copyOf (controls);
        this.maxInteractions = maxInteractions;
        this.maxOperations = maxOperations;
    }

    /** Complete current bindings. Replaying a binding does not restart or revive any interaction. */
    public void replaceBindings (final Map<C, T> current)
    {
        final Map<C, T> copy = Map.copyOf (current);
        copy.keySet ().forEach (this::requireControl);
        this.bindings = copy;
        for (final Session session: this.sessions.values ())
            if (!Objects.equals (copy.get (session.interaction.control ()), session.interaction.target ()))
                this.end (session, EndReason.BINDING_CHANGED);
    }

    /** Rejected presses are also remembered until physical release, never retried mid-gesture. */
    public Begin<C, T> begin (final C control)
    {
        this.requireControl (control);
        if (this.held.containsKey (control)) return rejected (Admission.ALREADY_HELD);
        this.held.put (control, Optional.empty ());
        if (this.stopped) return rejected (Admission.STOPPED);
        final T target = this.bindings.get (control);
        if (target == null) return rejected (Admission.UNBOUND);
        if (this.hasTargetWork (target)) return rejected (Admission.TARGET_BUSY);
        if (this.sessions.size () >= this.maxInteractions) return rejected (Admission.CAPACITY_EXHAUSTED);
        this.nextInteraction = Math.incrementExact (this.nextInteraction);
        final Id id = new Id (this.generation, this.nextInteraction);
        final Interaction<C, T> interaction = new Interaction<> (id, control, target);
        this.sessions.put (id, new Session (interaction));
        this.held.put (control, Optional.of (id));
        return new Begin<> (Admission.STARTED, Optional.of (interaction));
    }

    /** The owning view/route departed even if the same host target remains available. */
    public void cancel (final C control)
    {
        this.requireControl (control);
        this.held.getOrDefault (control, Optional.empty ()).map (this.sessions::get)
            .ifPresent (session -> this.end (session, EndReason.BINDING_CHANGED));
    }

    /** Includes rejected/cancelled physical tails, until an explicit release arrives. */
    public boolean isHeld (final C control)
    {
        this.requireControl (control);
        return this.held.containsKey (control);
    }

    /** Resolves companion motion to this physical gesture, never to the replacement binding. */
    public Optional<Interaction<C, T>> current (final C control)
    {
        this.requireControl (control);
        return this.held.getOrDefault (control, Optional.empty ()).map (this.sessions::get)
            .filter (session -> session.reason == null).map (session -> session.interaction);
    }

    /**
     * Admit immediately before submitting an operation, not while preparing/queueing intent.
     * The executor must also validate the captured target against the live host at application.
     * Empty means cancelled, stale or at capacity; no host request is authorized in that case.
     * A rejected submission must still report a terminal outcome via completeOperation.
     */
    public Optional<Operation<T>> beginOperation (final Id interaction)
    {
        final Session session = this.sessions.get (interaction);
        if (session == null || session.reason != null || this.sessions.values ().stream ().mapToInt (s -> s.operations.size ()).sum () >= this.maxOperations)
            return Optional.empty ();
        session.nextOperation = Math.incrementExact (session.nextOperation);
        final OperationId operation = new OperationId (interaction, session.nextOperation);
        session.operations.add (operation);
        return Optional.of (new Operation<> (operation, session.interaction.target ()));
    }

    /** Later authoritative completion or explicit terminal failure; a void submission is neither. */
    public boolean completeOperation (final OperationId operation)
    {
        final Session session = this.sessions.get (operation.interaction ());
        return session != null && session.operations.remove (operation);
    }

    /** Ends input only. Already submitted work and cleanup remain tracked independently. */
    public void release (final C control)
    {
        this.requireControl (control);
        final Optional<Id> interaction = this.held.remove (control);
        if (interaction != null)
            interaction.map (this.sessions::get).ifPresent (session -> this.end (session, EndReason.RELEASED));
    }

    /**
     * Addressability was lost externally. Never request cleanup through a replacement target.
     * Outstanding operations still need terminal outcomes; target loss does not fabricate them.
     * A later binding publication may offer only authoritatively valid target incarnations.
     */
    public void targetLost (final T target)
    {
        Objects.requireNonNull (target, "target");
        final Map<C, T> remaining = new LinkedHashMap<> (this.bindings);
        remaining.values ().removeIf (target::equals);
        this.bindings = Map.copyOf (remaining);
        for (final Session session: this.sessions.values ())
            if (target.equals (session.interaction.target ())) this.end (session, EndReason.TARGET_LOST);
    }

    /** Close admission and cancel existing interactions, without pretending input/work has ended. */
    public void stop ()
    {
        this.stopped = true;
        for (final Session session: this.sessions.values ()) this.end (session, EndReason.STOPPED);
    }

    /** Candidate IDs only; beginFinish must recheck them immediately before cleanup submission. */
    public List<Id> readyToFinish ()
    {
        return this.sessions.values ().stream ().filter (Session::ready).map (s -> s.interaction.id ()).toList ();
    }

    /**
     * Exactly one finalization per interaction, after all its submitted work has settled.
     * RELEASED permits ordinary release behavior; cancellation permits required cleanup only.
     * TARGET_LOST permits reporting/abandonment only, never a write to the lost/replacement target.
     * Cleanup has reserved capacity independent of the ordinary operation budget.
     */
    public Optional<Finish<T>> beginFinish (final Id interaction)
    {
        final Session session = this.sessions.get (interaction);
        if (session == null || !session.ready ()) return Optional.empty ();
        session.finishing = true;
        return Optional.of (new Finish<> (interaction, session.interaction.target (), session.reason));
    }

    /** Finish only after the executor's real terminal outcome; this does not imply physical UP. */
    public boolean completeFinish (final Id interaction)
    {
        final Session session = this.sessions.get (interaction);
        if (session == null || !session.finishing) return false;
        this.sessions.remove (interaction);
        return true;
    }

    /** No physical tails, submitted operations, or unfinished cleanup owned by this manager. */
    public boolean isIdle ()
    {
        return this.held.isEmpty () && this.sessions.isEmpty ();
    }

    /** A target may be reused after cleanup, even while its cancelled physical tail stays held. */
    public boolean hasTargetWork (final T target)
    {
        Objects.requireNonNull (target, "target");
        return this.sessions.values ().stream ().anyMatch (session -> target.equals (session.interaction.target ()));
    }

    private void end (final Session session, final EndReason reason)
    {
        // Cancellation dominates a not-yet-submitted normal release; loss dominates all cleanup.
        if (session.reason == null || reason == EndReason.TARGET_LOST || session.reason == EndReason.RELEASED && reason != EndReason.RELEASED)
            session.reason = reason;
    }

    private void requireControl (final C control)
    {
        if (!this.controls.contains (Objects.requireNonNull (control, "control")))
            throw new IllegalArgumentException ("Control is outside the installed footprint: " + control);
    }

    private static <C, T> Begin<C, T> rejected (final Admission admission)
    {
        return new Begin<> (admission, Optional.empty ());
    }

    private final class Session
    {
        private final Interaction<C, T> interaction;
        private final Set<OperationId> operations = new LinkedHashSet<> ();
        private long nextOperation;
        private EndReason reason;
        private boolean finishing;

        private Session (final Interaction<C, T> interaction) { this.interaction = interaction; }
        private boolean ready () { return this.reason != null && this.operations.isEmpty () && !this.finishing; }
    }
}
