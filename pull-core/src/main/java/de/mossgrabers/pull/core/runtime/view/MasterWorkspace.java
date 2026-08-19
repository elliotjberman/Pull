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
     * @param controllerViews Retained controller-level policy
     * @param sessionBank Exact retained Session-bank shape
     * @param backgroundViews Exact retained grid/note composition behind Master
     * @param noteController Whether the selected Note controller remains active
     * @return Compiled Master page over the selected workspace grid
     */
    public static CompiledWorkspace create (final ControllerLevelViews controllerViews, final SessionBankShape sessionBank, final List<? extends ControllerView> backgroundViews, final boolean noteController)
    {
        final List<ControllerView> views = new ArrayList<> ();
        views.add (new MasterControlView ());
        views.addAll (backgroundViews);
        return CompiledWorkspace.compile ("Master", sessionBank, noteController ? controllerViews.compose (views) : controllerViews.composeWithoutNoteController (views));
    }
}
