// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.RetainedControllerView;

import java.util.ArrayList;
import java.util.List;


/**
 * First post-demo composite built entirely from fixed controller views.
 */
public final class VsLiveWorkspace
{
    /** Workspace name presented across the core/shell boundary. */
    public static final String NAME = "VS Live";
    /** Fixed upper-half Session bank. */
    public static final SessionBankShape SESSION_BANK = new SessionBankShape (8, 4);


    private VsLiveWorkspace ()
    {
        // Utility class
    }


    /**
     * Compile a fresh VS Live workspace for one core generation.
     *
     * @param selection Shared workspace selection
     * @return Compiled workspace
     */
    public static CompiledWorkspace create (final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator, final ControllerView trackSelection, final List<? extends ControllerView> gridViews)
    {
        final List<ControllerView> views = new ArrayList<> ();
        views.add (new ProjectMacroControlsView ());
        views.add (trackSelection);
        views.addAll (gridViews);
        return CompiledWorkspace.compile (
            NAME,
            SESSION_BANK,
            ControllerLevelViews.composeWithoutNoteController (selection, playbackCoordinator, views));
    }


    /** Compile the retained VS Live grid with the core-owned Track/Mix page. */
    public static CompiledWorkspace createWithTrackMixerPage (final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator, final ControllerView trackSelection, final List<? extends ControllerView> gridViews)
    {
        final List<ControllerView> views = new ArrayList<> ();
        views.add (new TrackMixerControlsView ());
        views.add (trackSelection);
        views.addAll (gridViews);
        return CompiledWorkspace.compile (
            NAME + " / Track Mix",
            SESSION_BANK,
            ControllerLevelViews.composeWithoutNoteController (selection, playbackCoordinator, views));
    }


    /** Compile the VS Live grid while an independently selected stable page owns the display. */
    public static CompiledWorkspace createWithStablePage (final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator, final List<? extends ControllerView> gridViews)
    {
        return CompiledWorkspace.compile (
            NAME + " / stable page",
            SESSION_BANK,
            ControllerLevelViews.composeWithoutNoteController (selection, playbackCoordinator, gridViews));
    }


    /**
     * Create the VS Live views which own only the navigation and pad grid.
     *
     * @return Fresh grid views
     */
    public static List<ControllerView> retainedGridViews ()
    {
        return List.of (
            new RetainedControllerView (new SessionNavigationView ()),
            new RetainedControllerView (SessionView.upper (true)),
            new RetainedControllerView (new DrumPlayPadView ()),
            new RetainedControllerView (new DrumControllerView (true)),
            new RetainedControllerView (new DrumControlPadView ()),
            new RetainedControllerView (new DrumRateView ()));
    }
}
