// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.Set;


/** Explicit destination view for the inherited full Session layout. */
public final class FullSessionView implements ControllerView
{
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "full-session",
        Set.of (
            stableInput (SurfaceArea.GRID_UPPER),
            stableOutput (SurfaceArea.GRID_UPPER),
            stableInput (SurfaceArea.GRID_LOWER),
            stableOutput (SurfaceArea.GRID_LOWER),
            stableInput (SurfaceArea.SCENE_KEYS_UPPER),
            stableOutput (SurfaceArea.SCENE_KEYS_UPPER),
            stableInput (SurfaceArea.SCENE_KEYS_LOWER),
            stableOutput (SurfaceArea.SCENE_KEYS_LOWER),
            stableInput (SurfaceArea.NAVIGATION_ARROWS),
            stableOutput (SurfaceArea.NAVIGATION_ARROWS),
            stableInput (SurfaceArea.NAVIGATION_PAGE),
            stableOutput (SurfaceArea.NAVIGATION_PAGE)),
        Set.of (ControllerViewFacet.SESSION_GRID_FULL));


    @Override
    public String id ()
    {
        return "full-session";
    }


    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }


    private static SurfaceClaim stableInput (final SurfaceArea area)
    {
        return new SurfaceClaim (area, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT);
    }


    private static SurfaceClaim stableOutput (final SurfaceArea area)
    {
        return new SurfaceClaim (area, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT);
    }
}
