// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;

import java.util.Objects;


/**
 * Temporary complete scene composed above the current Push display page.
 *
 * <p>This is an exceptional full-surface transition plane, currently used by the cross-project
 * Play wave. Ordinary view content must claim the complete display or fixed display regions and
 * compose through the normal base scene.</p>
 */
public record ControllerDisplayOverlay (boolean active, ControllerDisplayScene scene)
{
    private static final ControllerDisplayOverlay INACTIVE = new ControllerDisplayOverlay (false, ControllerDisplayScene.empty ());


    /** Validate the replayable overlay. */
    public ControllerDisplayOverlay
    {
        scene = Objects.requireNonNull (scene, "scene");
        if (active != scene.isPresent ())
            throw new IllegalArgumentException ("An active display overlay requires a scene and an inactive overlay must be empty");
    }


    /** Get the inactive overlay. */
    public static ControllerDisplayOverlay inactive ()
    {
        return INACTIVE;
    }
}
