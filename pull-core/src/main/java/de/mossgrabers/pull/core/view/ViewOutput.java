// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Map;
import java.util.Objects;


/**
 * Complete replayable state contributed by one view.
 *
 * @param lights Hardware lights owned by the view
 * @param clipBindings Clip targets owned by the view's shell interaction bridge
 * @param controllerWorkspace Fixed-facet workspace selected by the view
 */
public record ViewOutput (Map<ControlId, RgbColor> lights, Map<ControlId, ClipTargetId> clipBindings, DesiredControllerWorkspace controllerWorkspace)
{
    private static final ViewOutput EMPTY = new ViewOutput (Map.of (), Map.of (), DesiredControllerWorkspace.empty ());


    /**
     * Validate and copy output.
     */
    public ViewOutput
    {
        lights = Map.copyOf (Objects.requireNonNull (lights, "lights"));
        clipBindings = Map.copyOf (Objects.requireNonNull (clipBindings, "clipBindings"));
        controllerWorkspace = Objects.requireNonNull (controllerWorkspace, "controllerWorkspace");
    }


    /**
     * Construct output without a controller-workspace override.
     *
     * @param lights Hardware lights owned by the view
     * @param clipBindings Clip targets owned by the view
     */
    public ViewOutput (final Map<ControlId, RgbColor> lights, final Map<ControlId, ClipTargetId> clipBindings)
    {
        this (lights, clipBindings, DesiredControllerWorkspace.empty ());
    }


    /**
     * Get empty view output.
     *
     * @return Empty output
     */
    public static ViewOutput empty ()
    {
        return EMPTY;
    }
}
