// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.Set;

/** Explicit frozen arrow delegation while a legacy page remains selected above full Session. */
final class FrozenSessionArrowsView implements ControllerView
{
    private static final ViewProfile PROFILE = ViewProfile.fixed ("legacy-page-arrows", Set.of (
        new SurfaceClaim (SurfaceArea.NAVIGATION_ARROWS, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
        new SurfaceClaim (SurfaceArea.NAVIGATION_ARROWS, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT)), Set.of (ControllerViewFacet.SESSION_GRID_FULL));

    @Override public String id () { return "session-legacy-arrows"; }
    @Override public ViewProfile profile () { return PROFILE; }
}
