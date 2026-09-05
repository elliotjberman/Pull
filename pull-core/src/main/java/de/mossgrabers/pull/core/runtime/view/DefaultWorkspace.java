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
        return create (controllerViews, List.of (new StableParameterControlsView ()));
    }


    /** Compose an independently selected page with the ordinary Note controller. */
    public static CompiledWorkspace create (final ControllerLevelViews controllerViews, final List<? extends ControllerView> pageViews)
    {
        return CompiledWorkspace.compile ("Pull", controllerViews.compose (pageViews));
    }


    /** Create the default workspace while the authoritative drum layout owns its rate pads. */
    public static CompiledWorkspace createDrum (final ControllerLevelViews controllerViews, final List<? extends ControllerView> drumViews, final boolean rawPitchBend)
    {
        return createDrum (controllerViews, drumViews, rawPitchBend, List.of (new StableParameterControlsView ()));
    }


    /** Keep the same retained Drum views while replacing only their parameter page. */
    public static CompiledWorkspace createDrum (final ControllerLevelViews controllerViews, final List<? extends ControllerView> drumViews, final boolean rawPitchBend, final List<? extends ControllerView> pageViews)
    {
        final List<ControllerView> views = new ArrayList<> ();
        views.addAll (pageViews);
        views.addAll (drumViews);
        return CompiledWorkspace.compile ("Pull Drum", rawPitchBend ? controllerViews.composeWithRawPitchBend (views, true) : controllerViews.compose (views));
    }


    /** Create the retained Drum slice shared with Master. */
    public static List<ControllerView> retainedDrumViews (final ControllerView drumControlPadView)
    {
        return List.of (
            new RetainedControllerView (new DrumPlayPadView ()),
            new RetainedControllerView (new DrumOctaveView ()),
            new RetainedControllerView (new DrumFillView ()),
            drumControlPadView,
            new RetainedControllerView (new DrumRateView ()));
    }
}
