// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;

import de.mossgrabers.pull.core.api.ControlId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;


/**
 * Hardware-independent controller light with an optional tempo-clocked alternate color.
 *
 * @param color Base color
 * @param blinkColor Alternate color; equal to {@code color} when steady
 * @param blinkRate Tempo-clocked blink rate
 * @param musicalPulse Exact musical phase when {@code blinkRate} is
 *            {@link LightBlinkRate#MUSICAL}
 */
public record ControllerLight (RgbColor color, RgbColor blinkColor, LightBlinkRate blinkRate, Optional<MusicalLightPulse> musicalPulse)
{
    /** Validate one complete light. */
    public ControllerLight
    {
        color = Objects.requireNonNull (color, "color");
        blinkColor = Objects.requireNonNull (blinkColor, "blinkColor");
        blinkRate = Objects.requireNonNull (blinkRate, "blinkRate");
        musicalPulse = Objects.requireNonNull (musicalPulse, "musicalPulse");
        if (blinkRate == LightBlinkRate.NONE && !color.equals (blinkColor))
            throw new IllegalArgumentException ("steady controller light must repeat its base color");
        if ((blinkRate == LightBlinkRate.MUSICAL) != musicalPulse.isPresent ())
            throw new IllegalArgumentException ("musical controller light rate and phase must be supplied together");
    }


    /** Source- and binary-compatible constructor for steady and native firmware blinks. */
    public ControllerLight (final RgbColor color, final RgbColor blinkColor, final LightBlinkRate blinkRate)
    {
        this (color, blinkColor, blinkRate, Optional.empty ());
    }


    /** Create a steady controller light. */
    public static ControllerLight steady (final RgbColor color)
    {
        final RgbColor checked = Objects.requireNonNull (color, "color");
        return new ControllerLight (checked, checked, LightBlinkRate.NONE, Optional.empty ());
    }


    /** Create the slow tempo-clocked alternation used by playing Session clips. */
    public static ControllerLight playing (final RgbColor color, final RgbColor playingColor)
    {
        return new ControllerLight (color, playingColor, LightBlinkRate.SLOW, Optional.empty ());
    }


    /** Create an exact, core-phased musical alternation. */
    public static ControllerLight musical (final RgbColor color, final RgbColor alternateColor, final MusicalLightPulse pulse)
    {
        return new ControllerLight (color, alternateColor, LightBlinkRate.MUSICAL, Optional.of (Objects.requireNonNull (pulse, "pulse")));
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
