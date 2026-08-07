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
    public static CompiledWorkspace session (final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator)
    {
        return create ("Session destination", SESSION_BANK, selection, playbackCoordinator, List.of (new TrackMixerPageView (), new FullSessionView ()));
    }


    /** Create the explicit Track/Mix page destination used around the stable Note view command. */
    public static CompiledWorkspace note (final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator)
    {
        return create ("Note destination", SessionBankShape.empty (), selection, playbackCoordinator, List.of (new TrackMixerPageView ()));
    }


    private static CompiledWorkspace create (final String name, final SessionBankShape sessionBank, final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator, final List<? extends ControllerView> destinationViews)
    {
        return CompiledWorkspace.compile (name, sessionBank, ControllerLevelViews.compose (selection, playbackCoordinator, destinationViews));
    }
}
