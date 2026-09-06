// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.pull.core.api.ControllerPageRef;
import de.mossgrabers.pull.core.api.DesiredControllerPageState;
import de.mossgrabers.pull.core.api.LegacyControllerPageRequest;
import de.mossgrabers.pull.core.api.LegacyControllerPageRequests;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Mechanical projection of core page state and bounded inbox for frozen legacy callers. */
public final class PushControllerPageManager extends ModeManager
{
    private final java.util.Set<TemporaryRequest> temporaryRequests = new java.util.HashSet<> ();
    private final List<LegacyControllerPageRequest> inbox = new ArrayList<> ();
    private final Map<Long, List<Runnable>> continuations = new LinkedHashMap<> ();
    private DesiredControllerPageState projected = DesiredControllerPageState.empty ();
    private DesiredControllerPageState pendingProjection;
    private Origin callbackOrigin;
    private IMode coreAdapter;
    private IMode activeBody;
    private IMode lifecycleBody;
    private long requestSequence;
    private long retiredSequence;
    private long consumerGeneration;
    private boolean consumerHealthy;
    private long lifecycleEpoch;
    private boolean projecting;
    private boolean invalidating;

    public void installCoreAdapter (final IMode adapter)
    {
        if (this.coreAdapter != null) throw new IllegalStateException ("Core page adapter is already installed");
        this.coreAdapter = Objects.requireNonNull (adapter, "adapter");
    }

    @Override public void setActive (final Modes mode) { this.request (LegacyControllerPageRequest.Operation.SELECT, mode == null ? this.defaultID : mode); }
    @Override public void setTemporary (final Modes mode) { this.request (LegacyControllerPageRequest.Operation.TEMPORARY, Objects.requireNonNull (mode, "mode")); }
    @Override public void restore () { this.request (LegacyControllerPageRequest.Operation.RESTORE, null); }
    @Override public void setPreviousID (final Modes mode) { this.request (LegacyControllerPageRequest.Operation.SET_PREVIOUS, mode); }
    @Override public Modes getActiveID () { return alias (this.projected.effectivePage ()); }
    @Override public Modes getActiveIDIgnoreTemporary () { return alias (this.projected.selected ()); }
    @Override public Modes getPreviousID () { return alias (this.projected.previous ()); }
    @Override public boolean isTemporary () { return this.projected.temporary ().isPresent (); }
    @Override public IMode getActive () { return this.activeBody == null ? this.coreAdapter : this.activeBody; }
    @Override public IMode getPrevious () { return this.body (this.projected.previous ()); }

    public DesiredControllerPageState pageState () { return this.projected; }
    public ControllerPageRef capturePage () { return this.projected.effectivePage (); }
    public ControllerPageRef captureSelectedPage () { return this.projected.selected (); }
    public LegacyControllerPageRequests requests () { return this.requests (true); }
    public LegacyControllerPageRequests requests (final boolean includePending) { return new LegacyControllerPageRequests (this.retiredSequence, includePending ? this.inbox : List.of ()); }
    public boolean canReplaceCore () { return !this.consumerHealthy || this.isIdle (); }

    /** Called only after a replacement result has committed successfully. */
    public void activateConsumer (final long generation)
    {
        if (generation <= 0) throw new IllegalArgumentException ("Consumer generation must be positive");
        if (!this.consumerHealthy || this.consumerGeneration != generation)
            this.lifecycleEpoch = Math.incrementExact (this.lifecycleEpoch);
        this.consumerGeneration = generation;
        this.consumerHealthy = true;
    }
    public boolean isIdle () { return this.temporaryRequests.isEmpty () && this.inbox.isEmpty () && this.continuations.isEmpty () && !this.projecting && this.pendingProjection == null; }

    /** Reduce a frozen legacy toggle in core, including repeated presses before projection. */
    public void toggleTemporary (final Modes mode) { this.request (LegacyControllerPageRequest.Operation.TOGGLE_TEMPORARY, Objects.requireNonNull (mode, "mode")); }

    public TemporaryRequest beginTemporary (final Modes mode) { return this.beginTemporary (mode, null); }

    /** The optional visible-page precondition is checked by core after earlier queued requests. */
    public TemporaryRequest beginTemporary (final Modes mode, final Modes requiredVisible)
    {
        final TemporaryRequest handle = new TemporaryRequest (this.lifecycleEpoch);
        if (!this.consumerHealthy || this.invalidating) return handle;
        if (this.temporaryRequests.size () == LegacyControllerPageRequests.CAPACITY) throw new IllegalStateException ("Temporary page handle capacity exhausted");
        if (this.get (Objects.requireNonNull (mode, "mode")) == null) throw new IllegalArgumentException ("Legacy page is not installed: " + mode);
        this.enqueue (LegacyControllerPageRequest.Operation.BEGIN_TEMPORARY, mode.name (), requiredVisible == null ? ControllerPageRef.none () : ControllerPageRef.legacy (requiredVisible.name ()));
        handle.entrySequence = this.requestSequence;
        this.temporaryRequests.add (handle);
        return handle;
    }

    /** Parent-owned once-close lifetime; it carries no child-core code or projected-state guess. */
    public final class TemporaryRequest implements AutoCloseable
    {
        private final long epoch;
        private long entrySequence;
        private TemporaryRequest (final long epoch) { this.epoch = epoch; }
        @Override public void close () { this.finish (LegacyControllerPageRequest.Operation.END_TEMPORARY); }
        public void cancel () { this.finish (LegacyControllerPageRequest.Operation.CANCEL_TEMPORARY); }
        private void finish (final LegacyControllerPageRequest.Operation operation)
        {
            if (!PushControllerPageManager.this.temporaryRequests.remove (this) || this.epoch != PushControllerPageManager.this.lifecycleEpoch || !PushControllerPageManager.this.consumerHealthy) return;
            PushControllerPageManager.this.enqueue (operation, "", ControllerPageRef.none (), this.entrySequence);
        }
    }

    public void requestCapturedPage (final ControllerPageRef page)
    {
        this.enqueue (LegacyControllerPageRequest.Operation.SELECT_CAPTURED, "", Objects.requireNonNull (page, "page"));
    }

    /** Preserve the originating page across a pre-existing delayed legacy callback. */
    public Runnable freezeRequestOrigin (final Runnable callback)
    {
        final Runnable checked = Objects.requireNonNull (callback, "callback");
        final Origin origin = this.origin ();
        final long epoch = this.lifecycleEpoch;
        final boolean accepted = this.consumerHealthy && !this.invalidating;
        return () -> { if (accepted && epoch == this.lifecycleEpoch) this.withOrigin (origin, checked); };
    }

    /** Preserve a legacy read-after-selection notification until that request is acknowledged. */
    public void afterPageRequest (final Runnable callback)
    {
        if (this.inbox.isEmpty ())
            return;
        if (this.continuations.values ().stream ().mapToInt (List::size).sum () >= LegacyControllerPageRequests.CAPACITY) throw new IllegalStateException ("Page notification continuation limit reached");
        this.continuations.computeIfAbsent (Long.valueOf (this.inbox.get (this.inbox.size () - 1).sequence ()), ignored -> new ArrayList<> ()).add (Objects.requireNonNull (callback, "callback"));
    }

    private void request (final LegacyControllerPageRequest.Operation operation, final Modes mode)
    {
        if (!this.consumerHealthy || this.invalidating) return;
        if (mode != null && this.get (mode) == null) throw new IllegalArgumentException ("Legacy page is not installed: " + mode);
        this.enqueue (operation, mode == null ? "" : mode.name (), ControllerPageRef.none ());
    }

    private void enqueue (final LegacyControllerPageRequest.Operation operation, final String mode, final ControllerPageRef captured)
    {
        this.enqueue (operation, mode, captured, 0);
    }

    private void enqueue (final LegacyControllerPageRequest.Operation operation, final String mode, final ControllerPageRef captured, final long temporaryRequestSequence)
    {
        if (!this.consumerHealthy || this.invalidating) return;
        if (this.inbox.size () == LegacyControllerPageRequests.CAPACITY || this.requestSequence == Long.MAX_VALUE)
            throw new IllegalStateException ("Controller page request inbox is full");
        final Origin origin = this.origin ();
        this.inbox.add (new LegacyControllerPageRequest (++this.requestSequence, origin.revision (), origin.temporaryToken (), operation, mode, captured, temporaryRequestSequence));
    }

    public DesiredControllerPageState prepare (final DesiredControllerPageState state)
    {
        final DesiredControllerPageState value = Objects.requireNonNull (state, "state");
        this.validate (value.selected ());
        this.validate (value.previous ());
        value.temporary ().ifPresent (temporary -> this.validate (temporary.page ()));
        if (value.acknowledgedRequestSequence () < this.retiredSequence || value.acknowledgedRequestSequence () > this.requestSequence)
            throw new IllegalArgumentException ("Page acknowledgement is outside the current request stream");
        if (value.effectivePage ().kind () == ControllerPageRef.Kind.CORE && this.coreAdapter == null) throw new IllegalStateException ("Core page adapter is not installed");
        return value;
    }

    public void apply (final DesiredControllerPageState state)
    {
        if (this.invalidating && state.effectivePage ().isPresent ()) return;
        this.pendingProjection = this.prepare (state);
        if (this.projecting) return;
        this.projecting = true;
        try
        {
            while (this.pendingProjection != null)
            {
                final DesiredControllerPageState next = this.pendingProjection;
                this.pendingProjection = null;
                final DesiredControllerPageState old = this.projected;
                final IMode oldBody = this.lifecycleBody;
                final IMode nextBody = this.body (next.effectivePage ());
                final List<Runnable> ready = new ArrayList<> ();
                this.retiredSequence = next.acknowledgedRequestSequence ();
                this.inbox.removeIf (request -> request.sequence () <= this.retiredSequence);
                final var callbacks = this.continuations.entrySet ().iterator ();
                while (callbacks.hasNext ())
                {
                    final var entry = callbacks.next ();
                    if (entry.getKey ().longValue () <= next.acknowledgedRequestSequence ()) { ready.addAll (entry.getValue ()); callbacks.remove (); }
                }
                // Foreign lifecycle callbacks see the complete new projection, but requests
                // originating in old deactivation retain the old revision/token fence.
                this.projected = next;
                this.activeBody = nextBody;
                if (oldBody != nextBody)
                {
                    this.lifecycleBody = null;
                    if (oldBody != null) this.withOrigin (Origin.of (old), oldBody::onDeactivate);
                    if (this.pendingProjection != null) continue;
                    this.lifecycleBody = nextBody;
                    if (nextBody != null) this.withOrigin (Origin.of (next), nextBody::onActivate);
                }
                if (this.pendingProjection != null) continue;
                if (!old.effectivePage ().equals (next.effectivePage ()))
                    this.withOrigin (Origin.of (next), () -> this.notifyObservers (alias (old.effectivePage ()), alias (next.effectivePage ())));
                for (final Runnable callback: ready) this.withOrigin (Origin.of (next), callback);
            }
        }
        finally
        {
            this.projecting = false;
            if (this.invalidating)
            {
                this.inbox.clear ();
                this.continuations.clear ();
                this.invalidating = false;
            }
        }
    }

    /** Fault/exit blanking retires deferred notifications; no legacy mode is revived. */
    public void invalidate ()
    {
        this.invalidating = true;
        this.lifecycleEpoch = Math.incrementExact (this.lifecycleEpoch);
        this.consumerHealthy = false;
        this.retiredSequence = this.requestSequence;
        this.inbox.clear ();
        this.temporaryRequests.clear ();
        this.continuations.clear ();
        this.apply (new DesiredControllerPageState (0, ControllerPageRef.none (), ControllerPageRef.none (), java.util.Optional.empty (), this.retiredSequence));
    }

    private void validate (final ControllerPageRef page)
    {
        alias (page);
        if (page.kind () == ControllerPageRef.Kind.LEGACY && this.get (Modes.valueOf (page.id ())) == null)
            throw new IllegalArgumentException ("Legacy page is not installed: " + page.id ());
    }

    private IMode body (final ControllerPageRef page)
    {
        return page.kind () == ControllerPageRef.Kind.LEGACY ? this.get (Modes.valueOf (page.id ())) : this.coreAdapter;
    }

    private static Modes alias (final ControllerPageRef page) { return page.legacyAlias ().isEmpty () ? null : Modes.valueOf (page.legacyAlias ()); }
    private Origin origin () { return this.callbackOrigin == null ? Origin.of (this.projected) : this.callbackOrigin; }
    private void withOrigin (final Origin origin, final Runnable callback)
    {
        final Origin previous = this.callbackOrigin;
        this.callbackOrigin = origin;
        try { callback.run (); } finally { this.callbackOrigin = previous; }
    }
    private record Origin (long revision, long temporaryToken)
    {
        static Origin of (final DesiredControllerPageState state) { return new Origin (state.revision (), state.temporaryToken ()); }
    }
}
