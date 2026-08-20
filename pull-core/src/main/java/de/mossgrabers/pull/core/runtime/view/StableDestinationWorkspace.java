// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ControllerView;

import java.util.List;


/** Compiled, read-back-acknowledged handoff to an inherited stable destination. */
public final class StableDestinationWorkspace
{
    /** Bounded full Session bank already installed by the stable shell. */
    public static final SessionBankShape SESSION_BANK = new SessionBankShape (8, 8);


    private StableDestinationWorkspace ()
    {
        // Utility class.
    }


    /** Create the explicit Track/Mix plus full Session destination. */
    public static CompiledWorkspace session (final WorkspaceSelection selection, final ControllerLevelViews controllerViews, final ControllerView sessionView)
    {
        return CompiledWorkspace.compile (
            "Session destination",
            SESSION_BANK,
            controllerViews.composeWithoutNoteController (List.of (new SessionTemporarySelectionView (selection), new TrackMixerPageView (), sessionView)));
    }


    /** Keep the semantic Session view selected after its default Track/Mix page is acknowledged. */
    public static CompiledWorkspace selectedSession (final ControllerLevelViews controllerViews, final ControllerView sessionView)
    {
        return CompiledWorkspace.compile (
            "Session",
            SESSION_BANK,
            controllerViews.compose (List.of (sessionView)));
    }


    /** Create the explicit Track/Mix page destination used around the stable Note view command. */
    public static CompiledWorkspace note (final ControllerLevelViews controllerViews)
    {
        return CompiledWorkspace.compile ("Note destination", controllerViews.compose (List.of (new TrackMixerPageView ())));
    }
}
