// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import java.util.List;
import java.util.Set;

/** Observed Push settings and velocity response, independent of configuration objects and effects. */
public record SetupPagePresentation (boolean available, int displayBrightness, int ledBrightness, int sensitivity, int gain, int dynamics,
                                     Set<Integer> touched, List<Integer> velocityCurve)
{
    public SetupPagePresentation
    {
        touched = Set.copyOf (touched);
        velocityCurve = List.copyOf (velocityCurve);
        if (touched.stream ().anyMatch (index -> index < 0 || index >= 8))
            throw new IllegalArgumentException ("Touched encoders must be in the physical range 0–7");
        if (available && (displayBrightness < 0 || displayBrightness > 100 || ledBrightness < 0 || ledBrightness > 100 ||
            sensitivity < 0 || sensitivity > 10 || gain < 0 || gain > 10 || dynamics < 0 || dynamics > 10 ||
            velocityCurve.size () != 128 || velocityCurve.stream ().anyMatch (value -> value < 1 || value > 127)))
            throw new IllegalArgumentException ("Available Setup state requires bounded settings and 128 velocity samples in 1–127");
    }
}
