// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.Set;

/** VS Live arrows use Session bank navigation regardless of the selected parameter page. */
public final class SessionNavigationView extends NavigationView
{
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "session",
        Set.of (
            new SurfaceClaim (SurfaceArea.NAVIGATION_ARROWS, SurfaceClaim.Kind.EXCLUSIVE_INPUT),
            new SurfaceClaim (SurfaceArea.NAVIGATION_ARROWS, SurfaceClaim.Kind.OUTPUT),
            new SurfaceClaim (SurfaceArea.SHIFT_MODIFIER, SurfaceClaim.Kind.OBSERVE_INPUT),
            new SurfaceClaim (SurfaceArea.NAVIGATION_PAGE, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
            new SurfaceClaim (SurfaceArea.NAVIGATION_PAGE, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT)),
        Set.of (ControllerViewFacet.SESSION_NAVIGATION));

    public SessionNavigationView () { super (Horizontal.SESSION); }
    @Override public String id () { return "session-navigation"; }
    @Override public ViewProfile profile () { return PROFILE; }
}
