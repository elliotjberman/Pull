// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.BrowserSnapshot;
import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerActionIntent;
import de.mossgrabers.pull.core.api.ControllerStateScope;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Browser activity chooses a temporary page in core, with one exact return owner. */
public final class BrowserPageNavigation
{
    private final PageNavigation pages;
    private BrowserSnapshot observed = BrowserSnapshot.empty ();
    private long resolvedGeneration;
    private long ownedToken;

    public BrowserPageNavigation (final PageNavigation pages)
    {
        this.pages = Objects.requireNonNull (pages, "pages");
        // A healthy reload keeps the open Browser's checkpointed temporary return.
        if ("BROWSER".equals (pages.legacyAlias ())) this.ownedToken = pages.state ().temporaryToken ();
    }

    /** Refresh before dispatching deferred actions so a stale open cannot outlive its host state. */
    public void reconcile (final BrowserSnapshot browser)
    {
        final BrowserSnapshot current = Objects.requireNonNull (browser, "browser");
        if (current.available ()) this.observed = current;
    }

    /** Resolve after older admitted actions, then use the ordinary ACTIVE_PARAMETERS barrier. */
    public ResolvedControllerAction resolveAction ()
    {
        final BrowserSnapshot current = this.observed;
        if (!current.available () || current.generation () == this.resolvedGeneration) return null;
        this.resolvedGeneration = current.generation ();
        final PageNavigation.Origin origin = this.pages.origin ();
        final long returnToken = this.ownedToken;
        if (!current.active () && returnToken == 0) return null;
        return ResolvedControllerAction.of (new ControllerActionIntent (ControllerActionId.SWITCH_PARAMETER_CONTEXT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)), () -> {
            if (!current.equals (this.observed)) return List.of ();
            if (current.active ())
            {
                if (!this.pages.matches (origin)) return List.of ();
                if (returnToken != 0 && returnToken == this.pages.state ().temporaryToken ()) return List.of ();
                this.ownedToken = this.pages.temporary (origin, this.pages.resolve ("BROWSER"));
            }
            else
            {
                this.pages.releaseTemporary (returnToken);
                if (this.ownedToken == returnToken) this.ownedToken = 0;
            }
            return List.of ();
        });
    }
}
