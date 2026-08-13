// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.Set;


/** Explicit destination view for the inherited per-track Mix page. */
public final class TrackMixerPageView implements ControllerView
{
    private static final ViewProfile PROFILE = ViewProfile.fixed (
        "track-mixer",
        Set.of (
            new SurfaceClaim (SurfaceArea.ENCODER_TURNS, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
            new SurfaceClaim (SurfaceArea.ENCODER_TOUCHES, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
            new SurfaceClaim (SurfaceArea.DISPLAY_PARAMETERS, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT),
            new SurfaceClaim (SurfaceArea.DISPLAY_BOTTOM_STRIP, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_UPPER, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
            new SurfaceClaim (SurfaceArea.SOFT_KEYS_LOWER, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT)),
        Set.of (ControllerViewFacet.TRACK_MIXER_PAGE));


    @Override
    public String id ()
    {
        return "track-mixer-page";
    }


    @Override
    public ViewProfile profile ()
    {
        return PROFILE;
    }
}
