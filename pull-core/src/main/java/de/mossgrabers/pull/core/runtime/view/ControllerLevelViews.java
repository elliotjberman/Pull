// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.RetainedControllerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Controller-level policy which remains available around every page-specific workspace.
 */
public final class ControllerLevelViews
{
    private final ControllerView workspaceSelection;
    private final ControllerView noteViewController;
    private final ControllerView globalParameters;
    private final ControllerView transport;
    private final ControllerView selectedTrackMuteSolo;


    /** Construct one retained controller-level policy set for a core generation. */
    public ControllerLevelViews (final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator)
    {
        final WorkspaceSelection checkedSelection = Objects.requireNonNull (selection, "selection");
        final SelectedTrackBooleanToggles selectedTrackToggles = new SelectedTrackBooleanToggles ();
        this.workspaceSelection = retained (new WorkspaceSelectionView (checkedSelection));
        this.noteViewController = retained (new NoteViewControllerView (checkedSelection));
        this.globalParameters = retained (new GlobalParameterControlsView ());
        this.transport = retained (new TransportControlView (
            Objects.requireNonNull (playbackCoordinator, "playbackCoordinator"),
            selectedTrackToggles));
        this.selectedTrackMuteSolo = retained (new SelectedTrackMuteSoloView (selectedTrackToggles));
    }


    /**
     * Compose fresh controller-level views with one workspace's page-specific views.
     *
     * @param workspaceViews Page-specific views
     * @return Complete view list for compilation
     */
    public List<ControllerView> compose (final List<? extends ControllerView> workspaceViews)
    {
        return this.compose (workspaceViews, true);
    }


    /** Compose controller-level views where a fixed non-note layout does not sample note policy. */
    public List<ControllerView> composeWithoutNoteController (final List<? extends ControllerView> workspaceViews)
    {
        return this.compose (workspaceViews, false);
    }


    private List<ControllerView> compose (final List<? extends ControllerView> workspaceViews, final boolean noteController)
    {
        final List<? extends ControllerView> checkedWorkspaceViews = Objects.requireNonNull (workspaceViews, "workspaceViews");
        final List<ControllerView> views = new ArrayList<> (checkedWorkspaceViews.size () + 5);
        views.add (this.workspaceSelection);
        if (noteController)
            views.add (this.noteViewController);
        views.add (this.globalParameters);
        views.add (this.transport);
        views.add (this.selectedTrackMuteSolo);
        for (final ControllerView view: checkedWorkspaceViews)
            views.add (Objects.requireNonNull (view, "workspaceView"));
        return List.copyOf (views);
    }


    private static ControllerView retained (final ControllerView view)
    {
        return new RetainedControllerView (view);
    }
}
