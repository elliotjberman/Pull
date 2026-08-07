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
    /** Apply one complete desired page transition. */
    void apply (final DesiredControllerWorkspace previous, final DesiredControllerWorkspace next, final ModeManager modes)
    {
        final Page previousPage = page (previous);
        final Page nextPage = page (next);
        if (previousPage == nextPage)
            return;

        if (nextPage == Page.STABLE)
            releaseToStableMode (modes);
        else
            reconcile (next, modes);
    }


    /** Reassert an explicit desired page until controller-layout read-back acknowledges it. */
    void reconcile (final DesiredControllerWorkspace workspace, final ModeManager modes)
    {
        switch (page (workspace))
        {
            case WORKSPACE -> modes.setActive (Modes.WORKSPACE);
            case MASTER -> modes.setActive (Modes.MASTER);
            case TRACK_MIXER -> modes.setActive (Modes.TRACK);
            case STABLE -> {
                // Stable owns the page after an explicit destination lease has been acknowledged.
            }
        }
    }


    /** Validate that at most one stable page adapter is requested. */
    static void validate (final DesiredControllerWorkspace workspace)
    {
        page (workspace);
    }


    private static void releaseToStableMode (final ModeManager modes)
    {
        // Fault/invalidation fallback only. Normal exits select an explicit destination page first.
        if (modes.isActive (Modes.WORKSPACE, Modes.MASTER, Modes.MASTER_TEMP))
            modes.setActive (Modes.TRACK);
    }


    private static Page page (final DesiredControllerWorkspace workspace)
    {
        final DesiredControllerWorkspace checked = Objects.requireNonNull (workspace, "workspace");
        final boolean master = checked.facets ().contains (ControllerViewFacet.MASTER_CONTROLS);
        final boolean trackMixer = checked.facets ().contains (ControllerViewFacet.TRACK_MIXER_PAGE);
        final boolean workspaceMode = checked.facets ().contains (ControllerViewFacet.PROJECT_MACRO_CONTROLS) || checked.facets ().contains (ControllerViewFacet.TRACK_SELECTION_STRIP);
        final int pageCount = (master ? 1 : 0) + (trackMixer ? 1 : 0) + (workspaceMode ? 1 : 0);
        if (pageCount > 1)
            throw new IllegalArgumentException ("Only one controller page view can be active");
        return master ? Page.MASTER : trackMixer ? Page.TRACK_MIXER : workspaceMode ? Page.WORKSPACE : Page.STABLE;
    }


    private enum Page
    {
        STABLE,
        WORKSPACE,
        MASTER,
        TRACK_MIXER
    }
}
