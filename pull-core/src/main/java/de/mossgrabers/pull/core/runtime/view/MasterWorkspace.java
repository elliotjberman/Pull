// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.view.CompiledWorkspace;


/** Reloadable behavior for the stable Master-mode mechanical adapter. */
public final class MasterWorkspace
{
    private MasterWorkspace ()
    {
        // Utility class.
    }


    /**
     * Create a fresh Master workspace for one core generation.
     *
     * @param selection Shared workspace selection
     * @return Compiled Master workspace
     */
    public static CompiledWorkspace create (final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator)
    {
        return CompiledWorkspace.compile ("Master", ControllerLevelViews.compose (selection, playbackCoordinator, new MasterControlView ()));
    }
}
