// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;

import de.mossgrabers.pull.core.api.ControlId;

import java.util.Map;
import java.util.Objects;

/**
 * Complete replayable hardware state currently owned by the core.
 *
 * @param lights Desired light colors by stable control identifier
 */
public record DesiredHardwareOutput (Map<ControlId, RgbColor> lights)
{
    private static final DesiredHardwareOutput EMPTY = new DesiredHardwareOutput (Map.of ());


    /**
     * Validate and copy the output map.
     */
    public DesiredHardwareOutput
    {
        lights = Map.copyOf (Objects.requireNonNull (lights, "lights"));
    }


    /**
     * Get empty desired hardware output.
     *
     * @return Empty output
     */
    public static DesiredHardwareOutput empty ()
    {
        return EMPTY;
    }
}
