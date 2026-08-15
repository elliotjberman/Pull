// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;

import de.mossgrabers.pull.core.api.ControlId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;


/**
 * Hardware-independent controller light with an optional tempo-clocked alternate color.
 *
 * @param color Base color
 * @param blinkColor Alternate color; equal to {@code color} when steady
 * @param blinkRate Tempo-clocked blink rate
 */
public record ControllerLight (RgbColor color, RgbColor blinkColor, LightBlinkRate blinkRate)
{
    /** Validate one complete light. */
    public ControllerLight
    {
        color = Objects.requireNonNull (color, "color");
        blinkColor = Objects.requireNonNull (blinkColor, "blinkColor");
        blinkRate = Objects.requireNonNull (blinkRate, "blinkRate");
        if (blinkRate == LightBlinkRate.NONE && !color.equals (blinkColor))
            throw new IllegalArgumentException ("steady controller light must repeat its base color");
    }


    /** Create a steady controller light. */
    public static ControllerLight steady (final RgbColor color)
    {
        final RgbColor checked = Objects.requireNonNull (color, "color");
        return new ControllerLight (checked, checked, LightBlinkRate.NONE);
    }


    /** Create the slow tempo-clocked alternation used by playing Session clips. */
    public static ControllerLight playing (final RgbColor color, final RgbColor playingColor)
    {
        return new ControllerLight (color, playingColor, LightBlinkRate.SLOW);
    }


    /** Lift a complete steady RGB map into the common controller-light representation. */
    public static Map<ControlId, ControllerLight> steadyLights (final Map<ControlId, RgbColor> colors)
    {
        final Map<ControlId, ControllerLight> lights = new LinkedHashMap<> ();
        Objects.requireNonNull (colors, "colors").forEach ( (control, color) -> lights.put (
            Objects.requireNonNull (control, "light control"),
            steady (Objects.requireNonNull (color, "light color"))));
        return Map.copyOf (lights);
    }
}
