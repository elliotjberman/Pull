// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerViewFacet;
import de.mossgrabers.pull.core.view.ControllerView;
import de.mossgrabers.pull.core.view.SurfaceArea;
import de.mossgrabers.pull.core.view.SurfaceClaim;
import de.mossgrabers.pull.core.view.ViewFacet;
import de.mossgrabers.pull.core.view.ViewProfile;

import java.util.Map;
import java.util.Set;


/**
 * Session clip launcher on the upper grid half.
 */
public final class SessionClipGridView implements ControllerView
{
    /** Upper scene-launch facet identifier. */
    public static final String SCENE_LAUNCH = "scene-launch";

    private static final ViewFacet SCENE_LAUNCH_FACET = new ViewFacet (
        SCENE_LAUNCH,
        Set.of (
            new SurfaceClaim (SurfaceArea.SCENE_KEYS_UPPER, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
            new SurfaceClaim (SurfaceArea.SCENE_KEYS_UPPER, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT)),
        Set.of (ControllerViewFacet.SESSION_SCENE_KEYS_UPPER));

    private final ViewProfile profile;


    /**
     * Constructor.
     *
     * @param sceneLaunchEnabled Whether the fixed upper scene-key facet is selected
     */
    public SessionClipGridView (final boolean sceneLaunchEnabled)
    {
        this.profile = new ViewProfile (
            "upper",
            Set.of (
                new SurfaceClaim (SurfaceArea.GRID_UPPER, SurfaceClaim.Kind.STABLE_ADAPTER_INPUT),
                new SurfaceClaim (SurfaceArea.GRID_UPPER, SurfaceClaim.Kind.STABLE_ADAPTER_OUTPUT)),
            Set.of (ControllerViewFacet.SESSION_CLIP_GRID_UPPER),
            Map.of (SCENE_LAUNCH, SCENE_LAUNCH_FACET),
            sceneLaunchEnabled ? Set.of (SCENE_LAUNCH) : Set.of ());
    }


    /** {@inheritDoc} */
    @Override
    public String id ()
    {
        return "session-clip-grid";
    }


    /** {@inheritDoc} */
    @Override
    public ViewProfile profile ()
    {
        return this.profile;
    }
}
