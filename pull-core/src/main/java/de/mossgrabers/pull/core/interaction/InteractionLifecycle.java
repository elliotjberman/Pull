// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.interaction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Bounded, single-threaded lifecycle for target-bound input. No hardware, host, timers or callbacks.
 * A changed binding cancels an interaction; its physical tail stays suppressed until release.
 * Final cleanup remains tracked until its executor reports explicit completion.
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
    public record Interaction<C, T> (Id id, C control, T target) {}
    public enum Admission { STARTED, ALREADY_HELD, UNBOUND, TARGET_BUSY, CAPACITY_EXHAUSTED }
    public record Begin<C, T> (Admission admission, Optional<Interaction<C, T>> interaction) {}
    public enum EndReason { RELEASED, BINDING_CHANGED }
    public record Finish<T> (Id interaction, T target, EndReason reason) {}

    private final long generation;
    private final Set<C> controls;
    private final int maxInteractions;
    private final Map<Id, Session> sessions = new LinkedHashMap<> ();
    // Empty means the press was rejected. A retired ID still suppresses its physical tail.
    private final Map<C, Optional<Id>> held = new LinkedHashMap<> ();
    private Map<C, T> bindings = Map.of ();
    private long nextInteraction;

    public InteractionLifecycle (final long generation, final Set<C> controls, final int maxInteractions)
    {
        if (generation < 0 || maxInteractions <= 0)
            throw new IllegalArgumentException ("Nonnegative generation and positive capacity required");
        this.generation = generation;
        this.controls = Set.copyOf (controls);
        this.maxInteractions = maxInteractions;
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

    /** Ends physical input. Final cleanup remains tracked until explicit completion. */
    public void release (final C control)
    {
        this.requireControl (control);
        final Optional<Id> interaction = this.held.remove (control);
        if (interaction != null)
            interaction.map (this.sessions::get).ifPresent (session -> this.end (session, EndReason.RELEASED));
    }

    /** Candidate IDs only; beginFinish must recheck them immediately before cleanup submission. */
    public List<Id> readyToFinish ()
    {
        return this.sessions.values ().stream ().filter (Session::ready).map (s -> s.interaction.id ()).toList ();
    }

    /**
     * Exactly one finalization per ended interaction.
     * RELEASED permits ordinary release behavior; cancellation permits required cleanup only.
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

    /** No physical tails or unfinished cleanup owned by this manager. */
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
        // Cancellation dominates a not-yet-submitted normal release.
        if (session.reason == null || reason == EndReason.BINDING_CHANGED)
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
        private EndReason reason;
        private boolean finishing;

        private Session (final Interaction<C, T> interaction) { this.interaction = interaction; }
        private boolean ready () { return this.reason != null && !this.finishing; }
    }
}
