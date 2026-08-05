// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.Set;


/**
 * Track labels and direct track selection on the bottom display strip.
 */
public final class TrackSelectionStripView implements ControllerView
{
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "default",
        Set.of (
            new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT)),
        Set.of (ControllerViewFacet.TRACK_SELECTION_STRIP));


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "track-selection-strip";
    }


    /** {@inheritDoc} */
    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }
}
