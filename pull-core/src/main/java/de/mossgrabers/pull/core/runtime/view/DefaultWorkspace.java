// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.view.CompiledWorkspace;

import java.util.List;


/**
 * Existing Pull behavior expressed as one fixed-footprint workspace.
 */
public final class DefaultWorkspace
{
    private DefaultWorkspace ()
    {
        // Utility class
    }


    /**
     * Create a fresh default workspace for one core generation.
     *
     * @return Compiled workspace
     */
    public static CompiledWorkspace create (final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator)
    {
        return CompiledWorkspace.compile ("Pull", ControllerLevelViews.compose (selection, playbackCoordinator, List.of (
            new StableParameterControlsView ())));
    }


    /** Create the default workspace while the authoritative drum layout owns its rate pads. */
    public static CompiledWorkspace createDrum (final WorkspaceSelection selection, final ProjectPlaybackCoordinator playbackCoordinator)
    {
        return CompiledWorkspace.compile ("Pull Drum", ControllerLevelViews.compose (selection, playbackCoordinator, List.of (
            new StableParameterControlsView (),
            new DrumPlayPadView (),
            new DrumFillView (),
            new DrumControlPadView (),
            new DrumRateView ())));
    }
}
