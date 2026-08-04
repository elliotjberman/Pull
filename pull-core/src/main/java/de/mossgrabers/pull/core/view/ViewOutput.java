// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Map;
import java.util.Objects;


/**
 * Complete replayable state contributed by one view.
 *
 * @param lights Hardware lights owned by the view
 * @param clipBindings Clip targets owned by the view's shell interaction bridge
 */
public record ViewOutput (Map<ControlId, RgbColor> lights, Map<ControlId, ClipTargetId> clipBindings)
{
    private static final ViewOutput EMPTY = new ViewOutput (Map.of (), Map.of ());


    /**
     * Validate and copy output.
     */
    public ViewOutput
    {
        lights = Map.copyOf (Objects.requireNonNull (lights, "lights"));
        clipBindings = Map.copyOf (Objects.requireNonNull (clipBindings, "clipBindings"));
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
