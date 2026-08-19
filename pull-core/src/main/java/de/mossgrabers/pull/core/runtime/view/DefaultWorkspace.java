// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.RetainedControllerView;

import java.util.ArrayList;
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
    public static CompiledWorkspace create (final ControllerLevelViews controllerViews)
    {
        return CompiledWorkspace.compile ("Pull", controllerViews.compose (List.of (
            new StableParameterControlsView ())));
    }


    /** Create the default workspace while the authoritative drum layout owns its rate pads. */
    public static CompiledWorkspace createDrum (final ControllerLevelViews controllerViews, final List<? extends ControllerView> drumViews)
    {
        final List<ControllerView> views = new ArrayList<> ();
        views.add (new StableParameterControlsView ());
        views.addAll (drumViews);
        return CompiledWorkspace.compile ("Pull Drum", controllerViews.compose (views));
    }


    /** Create the retained Drum slice shared with Master. */
    public static List<ControllerView> retainedDrumViews ()
    {
        return List.of (
            new RetainedControllerView (new DrumPlayPadView ()),
            new RetainedControllerView (new DrumFillView ()),
            new RetainedControllerView (new DrumControlPadView ()),
            new RetainedControllerView (new DrumRateView ()));
    }
}
