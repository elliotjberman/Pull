// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.view.ControllerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Controller-level policy which remains available around every page-specific workspace.
 */
final class ControllerLevelViews
{
    private ControllerLevelViews ()
    {
        // Utility class.
    }


    /**
     * Compose fresh controller-level views with one workspace's page-specific views.
     *
     * @param selection Shared workspace selection
     * @param playbackCoordinator Shared project playback policy
     * @param workspaceViews Page-specific views
     * @return Complete view list for compilation
     */
    static List<ControllerView> compose (final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator, final List<? extends ControllerView> workspaceViews)
    {
        final List<? extends ControllerView> checkedWorkspaceViews = Objects.requireNonNull (workspaceViews, "workspaceViews");
        final List<ControllerView> views = new ArrayList<> (checkedWorkspaceViews.size () + 3);
        views.add (new WorkspaceSelectionView (Objects.requireNonNull (selection, "selection")));
        views.add (new GlobalParameterControlsView ());
        views.add (new TransportControlView (Objects.requireNonNull (playbackCoordinator, "playbackCoordinator")));
        for (final ControllerView view: checkedWorkspaceViews)
            views.add (Objects.requireNonNull (view, "workspaceView"));
        return List.copyOf (views);
    }
}
