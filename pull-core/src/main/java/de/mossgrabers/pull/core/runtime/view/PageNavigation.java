// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Core-owned page selection, history and one replaceable temporary owner. */
public final class PageNavigation
{
    private final ControllerPageRef defaultPage;
    private final Function<String, ControllerPageRef> resolveLegacy;
    private DesiredControllerPageState state;
    private long workspaceEpoch;
    private long nextToken;
    private long offeredSequence;
    private long admissionEpoch;
    private LegacyBatch lastBatch;
    private final java.util.Map<Long, Long> legacyTemporaryOwners = new java.util.HashMap<> ();

    public PageNavigation (final ControllerPageRef defaultPage, final Function<String, ControllerPageRef> resolveLegacy)
    {
        this.defaultPage = requirePage (defaultPage);
        this.resolveLegacy = Objects.requireNonNull (resolveLegacy, "resolveLegacy");
        this.state = new DesiredControllerPageState (0, defaultPage, ControllerPageRef.none (), Optional.empty (), 0);
    }

    /** Convenient isolated composition; production supplies its declared page catalog. */
    static PageNavigation defaults ()
    {
        return new PageNavigation (LegacyPageAliases.resolve ("TRACK"), LegacyPageAliases::resolve);
    }

    public DesiredControllerPageState state () { return this.state; }
    public ControllerPageRef visible () { return this.state.effectivePage (); }
    public String legacyAlias () { return this.visible ().legacyAlias (); }
    public long revision () { return this.state.revision (); }
    public Origin origin () { return new Origin (this.revision (), this.workspaceEpoch); }
    public boolean matches (final Origin origin) { return this.origin ().equals (origin); }
    public ControllerPageRef resolve (final String legacyAlias) { return Objects.requireNonNull (this.resolveLegacy.apply (legacyAlias), "resolved page"); }

    /** Hydration invalidates queued closures; temporary tokens never regress within this core. */
    public void restoreState (final DesiredControllerPageState restored)
    {
        final DesiredControllerPageState checked = Objects.requireNonNull (restored, "restored");
        // A saved compatibility page may have migrated since the checkpoint was written.
        this.state = new DesiredControllerPageState (checked.revision (), checked.selected ().isPresent () ? this.promote (checked.selected ()) : this.defaultPage,
            this.promote (checked.previous ()), checked.selected ().isPresent () ? checked.temporary ().map (temporary -> new ControllerTemporaryPage (temporary.token (), this.promote (temporary.page ()))) : Optional.empty (),
            checked.acknowledgedRequestSequence (), checked.parameterIndications ());
        this.nextToken = Math.max (this.nextToken, Math.max (this.revision (), this.state.temporaryToken ()));
        this.offeredSequence = this.state.acknowledgedRequestSequence ();
        this.admissionEpoch++;
        this.lastBatch = null;
        this.legacyTemporaryOwners.clear ();
    }

    private ControllerPageRef promote (final ControllerPageRef page)
    {
        return page.kind () == ControllerPageRef.Kind.LEGACY ? this.resolve (page.legacyAlias ()) : page;
    }

    /** Request acknowledgements belong to the shell stream, independently of UI checkpoints. */
    public void startRequestStream (final LegacyControllerPageRequests inbox)
    {
        final long retired = Objects.requireNonNull (inbox, "inbox").retiredSequence ();
        this.state = new DesiredControllerPageState (this.revision (), this.state.selected (), this.state.previous (), this.state.temporary (), retired, this.state.parameterIndications ());
        this.offeredSequence = retired;
        this.admissionEpoch++;
        this.lastBatch = null;
        this.legacyTemporaryOwners.clear ();
    }

    /** A grid/workspace replacement invalidates old gesture origins and temporary returns. */
    public void workspaceChanged (final long epoch, final ControllerPageRef page)
    {
        if (this.workspaceEpoch == epoch) return;
        this.workspaceEpoch = epoch;
        final ControllerPageRef next = requirePage (page);
        this.replace (next, next.equals (this.state.selected ()) ? this.state.previous () : this.state.selected (), Optional.empty ());
    }

    public boolean select (final Origin origin, final ControllerPageRef page)
    {
        if (!this.matches (origin)) return false;
        this.select (page);
        return true;
    }

    /** Explicit selection also supports the Mix button's captured-page return policy. */
    public void select (final ControllerPageRef page)
    {
        final ControllerPageRef next = requirePage (page);
        if (!next.equals (this.visible ())) this.replace (next, this.state.selected (), Optional.empty ());
    }

    public long temporary (final Origin origin, final ControllerPageRef page)
    {
        if (!this.matches (origin)) return 0;
        final long token = Math.addExact (Math.max (this.nextToken, this.revision ()), 1);
        this.nextToken = token;
        this.replace (this.state.selected (), this.state.previous (), Optional.of (new ControllerTemporaryPage (token, requirePage (page))));
        return token;
    }

    public boolean releaseTemporary (final long token)
    {
        if (token == 0 || token != this.state.temporaryToken ()) return false;
        this.replace (this.state.selected (), this.state.previous (), Optional.empty ());
        return true;
    }

    public boolean restore (final Origin origin)
    {
        if (!this.matches (origin)) return false;
        if (this.state.temporary ().isPresent ()) this.releaseTemporary (this.state.temporaryToken ());
        else if (this.state.previous ().isPresent () && !this.state.previous ().equals (this.state.selected ()))
            this.replace (this.state.previous (), this.state.previous (), Optional.empty ());
        return true;
    }

    private void replace (final ControllerPageRef selected, final ControllerPageRef previous, final Optional<ControllerTemporaryPage> temporary)
    {
        this.state = new DesiredControllerPageState (Math.addExact (this.revision (), 1), selected, previous, temporary, this.state.acknowledgedRequestSequence ());
    }

    /**
     * Resolve each replayed compatibility request once. The caller dispatches these actions through
     * the same ACTIVE_PARAMETERS barrier as a physical page control, in request order. Requests
     * captured together may advance history together; an intervening core selection cancels them.
     */
    public List<ResolvedControllerAction> resolveLegacyActions (final LegacyControllerPageRequests inbox)
    {
        final List<ResolvedControllerAction> actions = new ArrayList<> ();
        for (final LegacyControllerPageRequest request: inbox.requests ())
        {
            if (request.sequence () <= this.offeredSequence) continue;
            if (this.lastBatch == null || !this.lastBatch.sameOrigin (request, this.workspaceEpoch))
                this.lastBatch = new LegacyBatch (request, this.workspaceEpoch);
            final LegacyBatch batch = this.lastBatch;
            final long epoch = this.admissionEpoch;
            this.offeredSequence = request.sequence ();
            // A rejected conditional entry must not initiate parameter restoration. An earlier
            // deferred page action still keeps this request behind the same FIFO barrier; the
            // condition is checked again after that action actually selects its page.
            final boolean rejectedEntry = request.operation () == LegacyControllerPageRequest.Operation.BEGIN_TEMPORARY && request.capturedTarget ().isPresent () && !request.capturedTarget ().equals (this.visible ());
            // A release for a rejected/replaced temporary owner cannot change the page. A deferred
            // entry still keeps its release behind the existing FIFO barrier until dispatch.
            final boolean inertReturn = request.operation () == LegacyControllerPageRequest.Operation.CANCEL_TEMPORARY ||
                request.operation () == LegacyControllerPageRequest.Operation.END_TEMPORARY && !Objects.equals (this.legacyTemporaryOwners.get (Long.valueOf (request.temporaryRequestSequence ())), Long.valueOf (this.state.temporaryToken ()));
            final Set<ControllerStateScope> invalidates = rejectedEntry || inertReturn ? Set.of () : Set.of (ControllerStateScope.ACTIVE_PARAMETERS);
            actions.add (ResolvedControllerAction.of (new ControllerActionIntent (ControllerActionId.SWITCH_PARAMETER_CONTEXT, invalidates), () -> {
                if (epoch != this.admissionEpoch || request.sequence () <= this.state.acknowledgedRequestSequence ()) return List.of ();
                if (request.sequence () != this.state.acknowledgedRequestSequence () + 1)
                    throw new IllegalStateException ("Page requests must be dispatched in inbox order");
                final boolean matchingBatch = batch.matches (this);
                if (request.operation () == LegacyControllerPageRequest.Operation.END_TEMPORARY || request.operation () == LegacyControllerPageRequest.Operation.CANCEL_TEMPORARY)
                {
                    final Long token = this.legacyTemporaryOwners.remove (Long.valueOf (request.temporaryRequestSequence ()));
                    if (request.operation () == LegacyControllerPageRequest.Operation.END_TEMPORARY && token != null && this.releaseTemporary (token.longValue ()))
                    {
                        batch.expectedRevision = this.revision ();
                        batch.expectedTemporaryToken = this.state.temporaryToken ();
                    }
                }
                else if (matchingBatch)
                {
                    switch (request.operation ())
                    {
                        case SELECT -> this.select (this.resolve (request.legacyModeId ()));
                        case SELECT_CAPTURED -> this.select (request.capturedTarget ());
                        case TEMPORARY -> this.temporary (this.origin (), this.resolve (request.legacyModeId ()));
                        case TOGGLE_TEMPORARY -> {
                            final ControllerPageRef target = this.resolve (request.legacyModeId ());
                            if (target.equals (this.visible ())) this.restore (this.origin ());
                            else this.temporary (this.origin (), target);
                        }
                        case BEGIN_TEMPORARY -> {
                            if (!request.capturedTarget ().isPresent () || request.capturedTarget ().equals (this.visible ()))
                            {
                                if (this.legacyTemporaryOwners.size () == LegacyControllerPageRequests.CAPACITY) throw new IllegalStateException ("Temporary page handle capacity exhausted");
                                this.legacyTemporaryOwners.put (Long.valueOf (request.sequence ()), Long.valueOf (this.temporary (this.origin (), this.resolve (request.legacyModeId ()))));
                            }
                        }
                        case END_TEMPORARY, CANCEL_TEMPORARY -> throw new IllegalStateException ("Temporary return must use its captured owner");
                        case RESTORE -> this.restore (this.origin ());
                        case SET_PREVIOUS -> this.replace (this.state.selected (), request.legacyModeId ().isEmpty () ? ControllerPageRef.none () : this.resolve (request.legacyModeId ()), this.state.temporary ());
                    }
                    batch.expectedRevision = this.revision ();
                    batch.expectedTemporaryToken = this.state.temporaryToken ();
                }
                this.state = new DesiredControllerPageState (this.revision (), this.state.selected (), this.state.previous (), this.state.temporary (), request.sequence ());
                return List.of ();
            }));
        }
        return List.copyOf (actions);
    }

    private static ControllerPageRef requirePage (final ControllerPageRef page)
    {
        if (!Objects.requireNonNull (page, "page").isPresent ()) throw new IllegalArgumentException ("A selected page must be present");
        return page;
    }

    /** Controller ownership only; Bitwig layout generations are deliberately independent. */
    public record Origin (long revision, long workspaceEpoch) { }

    private static final class LegacyBatch
    {
        private final long originRevision;
        private final long originTemporaryToken;
        private final long workspaceEpoch;
        private long expectedRevision;
        private long expectedTemporaryToken;
        private LegacyBatch (final LegacyControllerPageRequest request, final long workspaceEpoch)
        {
            this.originRevision = request.originPageRevision ();
            this.originTemporaryToken = request.originTemporaryToken ();
            this.workspaceEpoch = workspaceEpoch;
            this.expectedRevision = this.originRevision;
            this.expectedTemporaryToken = this.originTemporaryToken;
        }
        private boolean sameOrigin (final LegacyControllerPageRequest request, final long epoch)
        {
            return request.originPageRevision () == this.originRevision && request.originTemporaryToken () == this.originTemporaryToken && epoch == this.workspaceEpoch;
        }
        private boolean matches (final PageNavigation pages)
        {
            return this.workspaceEpoch == pages.workspaceEpoch && this.expectedRevision == pages.revision () && this.expectedTemporaryToken == pages.state.temporaryToken ();
        }
    }
}
