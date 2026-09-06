// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerActionId;
import de.mossgrabers.pull.core.api.ControllerActionIntent;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ControllerStateScope;
import de.mossgrabers.pull.core.api.MasterSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.effect.NavigateProjectEffect;
import de.mossgrabers.pull.core.view.PageId;
import de.mossgrabers.pull.core.view.ResolvedControllerAction;
import java.util.List;
import java.util.Set;

/** Page policy for DAW Master selection; host observations never own controller navigation. */
public final class MasterTrackPageNavigation
{
    private final PageNavigation pages;
    private MasterSnapshot observed = MasterSnapshot.empty ();
    private long retainedRevision = -1;

    public MasterTrackPageNavigation (final PageNavigation pages) { this.pages = java.util.Objects.requireNonNull (pages, "pages"); }

    /** Hydrate the existing selection without turning a reload into a new DAW selection edge. */
    public void start (final ControllerSnapshot snapshot) { this.observed = snapshot.bridge ().master (); }

    /** Master-owned project navigation retains its page until a later explicit page selection. */
    public void observeEffects (final List<CoreEffect> effects)
    {
        if (effects.stream ().anyMatch (NavigateProjectEffect.class::isInstance)) this.retainedRevision = this.pages.revision ();
    }

    public ResolvedControllerAction observe (final ControllerSnapshot snapshot)
    {
        final MasterSnapshot current = snapshot.bridge ().master ();
        if (!current.available ()) return null;
        final MasterSnapshot previous = this.observed;
        this.observed = current;
        if (this.retainedRevision == this.pages.revision ()) return null;
        if (previous.available () && !previous.projectIdentity ().equals (current.projectIdentity ())) return null;
        if (current.trackSelected () == previous.trackSelected ()) return null;
        final var origin = this.pages.origin ();
        final boolean selected = current.trackSelected ();
        return ResolvedControllerAction.of (new ControllerActionIntent (ControllerActionId.SWITCH_PARAMETER_CONTEXT, Set.of (ControllerStateScope.ACTIVE_PARAMETERS)), () -> {
            if (!this.pages.matches (origin) || !this.observed.projectIdentity ().equals (current.projectIdentity ()) || this.observed.trackSelected () != selected) return List.of ();
            if (selected) this.pages.select (origin, LegacyPageAliases.reference (PageId.MASTER));
            else if (PageId.MASTER.value ().equals (this.pages.visible ().id ())) this.pages.restore (origin);
            return List.of ();
        });
    }
}
