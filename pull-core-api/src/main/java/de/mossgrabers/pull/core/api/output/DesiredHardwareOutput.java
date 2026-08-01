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
 * @param absoluteValues Desired normalized absolute output values by stable control identifier
 */
public record DesiredHardwareOutput (Map<ControlId, RgbColor> lights, Map<ControlId, Double> absoluteValues)
{
    private static final DesiredHardwareOutput EMPTY = new DesiredHardwareOutput (Map.of (), Map.of ());


    /**
     * Validate and copy the output map.
     */
    public DesiredHardwareOutput
    {
        lights = Map.copyOf (Objects.requireNonNull (lights, "lights"));
        absoluteValues = Map.copyOf (Objects.requireNonNull (absoluteValues, "absoluteValues"));
        for (final Map.Entry<ControlId, Double> output: absoluteValues.entrySet ())
        {
            Objects.requireNonNull (output.getKey (), "absolute output control");
            final double value = Objects.requireNonNull (output.getValue (), "absolute output value").doubleValue ();
            if (!Double.isFinite (value) || value < 0 || value > 1)
                throw new IllegalArgumentException ("absolute output values must be finite and in [0, 1]");
        }
    }


    /**
     * Construct output containing only light colors.
     *
     * @param lights Desired light colors by stable control identifier
     */
    public DesiredHardwareOutput (final Map<ControlId, RgbColor> lights)
    {
        this (lights, Map.of ());
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
