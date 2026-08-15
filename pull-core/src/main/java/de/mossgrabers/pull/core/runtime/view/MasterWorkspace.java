// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ControllerView;

import java.util.ArrayList;
import java.util.List;


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
     * @param background Workspace whose grid ownership remains active behind Master
     * @return Compiled Master page over the selected workspace grid
     */
    public static CompiledWorkspace create (final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator, final WorkspaceSelection.Id background)
    {
        final List<ControllerView> views = new ArrayList<> ();
        views.add (new MasterControlView ());
        final SessionBankShape sessionBank;
        if (background == WorkspaceSelection.Id.VS_LIVE)
        {
            views.addAll (VsLiveWorkspace.gridViews ());
            sessionBank = VsLiveWorkspace.SESSION_BANK;
        }
        else
            sessionBank = SessionBankShape.empty ();
        return CompiledWorkspace.compile ("Master", sessionBank, ControllerLevelViews.composeWithoutNoteController (selection, playbackCoordinator, views));
    }
}
