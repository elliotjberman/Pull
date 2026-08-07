// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.workspace;

import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;

import java.util.Objects;


/**
 * Realizes the page layer of a desired controller workspace independently from its grid layer.
 */
final class ControllerPageLease
{
    private Modes stableBaseline;


    /** Apply one complete desired page transition. */
    void apply (final DesiredControllerWorkspace previous, final DesiredControllerWorkspace next, final ModeManager modes)
    {
        final Page previousPage = page (previous);
        final Page nextPage = page (next);
        if (previousPage == nextPage)
            return;

        if (previousPage == Page.STABLE)
            this.stableBaseline = baselineBefore (nextPage, modes);

        switch (nextPage)
        {
            case WORKSPACE -> modes.setActive (Modes.WORKSPACE);
            case MASTER -> {
                // The permanent Master binding enters the inherited mode before its authoritative
                // layout read-back selects the core Master page. Do not replay that command here.
            }
            case STABLE -> {
                modes.setActive (this.stableBaseline);
                this.stableBaseline = null;
            }
        }
    }


    /** Validate that at most one stable page adapter is requested. */
    static void validate (final DesiredControllerWorkspace workspace)
    {
        page (workspace);
    }


    private static Modes baselineBefore (final Page nextPage, final ModeManager modes)
    {
        final Modes candidate = nextPage == Page.MASTER && Modes.isMasterMode (modes.getActiveID ()) ? modes.getPreviousID () : modes.getActiveIDIgnoreTemporary ();
        return candidate == Modes.WORKSPACE || Modes.isMasterMode (candidate) ? null : candidate;
    }


    private static Page page (final DesiredControllerWorkspace workspace)
    {
        final DesiredControllerWorkspace checked = Objects.requireNonNull (workspace, "workspace");
        final boolean master = checked.facets ().contains (ControllerViewFacet.MASTER_CONTROLS);
        final boolean workspaceMode = checked.facets ().contains (ControllerViewFacet.PROJECT_MACRO_CONTROLS) || checked.facets ().contains (ControllerViewFacet.TRACK_SELECTION_STRIP);
        if (master && workspaceMode)
            throw new IllegalArgumentException ("Master and workspace-mode page facets cannot be active together");
        return master ? Page.MASTER : workspaceMode ? Page.WORKSPACE : Page.STABLE;
    }


    private enum Page
    {
        STABLE,
        WORKSPACE,
        MASTER
    }
}
