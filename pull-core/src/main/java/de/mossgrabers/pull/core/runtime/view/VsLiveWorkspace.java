// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.view.CompiledWorkspace;


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
    public static CompiledWorkspace create (final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator)
    {
        return CompiledWorkspace.compile (
            NAME,
            SESSION_BANK,
            ControllerLevelViews.compose (selection, playbackCoordinator,
                new ProjectMacroControlsView (),
                new TrackSelectionStripView (),
                new SessionNavigationView (),
                new SessionClipGridView (true),
                new DrumControllerView (true)));
    }
}
