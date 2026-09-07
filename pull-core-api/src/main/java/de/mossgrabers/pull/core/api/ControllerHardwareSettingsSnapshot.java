// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import java.util.List;

/** Observed hardware preferences and the bounded velocity table used by the Push transport. */
public record ControllerHardwareSettingsSnapshot (boolean available, int displayBrightness, int ledBrightness, int sensitivity, int gain, int dynamics, List<Integer> velocityCurve)
{
    public ControllerHardwareSettingsSnapshot
    {
        velocityCurve = List.copyOf (velocityCurve);
        if (displayBrightness < 0 || displayBrightness > 100 || ledBrightness < 0 || ledBrightness > 100 || sensitivity < 0 || sensitivity > 10 || gain < 0 || gain > 10 || dynamics < 0 || dynamics > 10)
            throw new IllegalArgumentException ("Hardware preference outside its installed range");
        if (available && (velocityCurve.size () != 128 || velocityCurve.stream ().anyMatch (value -> value < 1 || value > 127)))
            throw new IllegalArgumentException ("Available hardware settings require the 128-entry velocity table");
        if (!available && (displayBrightness != 0 || ledBrightness != 0 || sensitivity != 0 || gain != 0 || dynamics != 0 || !velocityCurve.isEmpty ()))
            throw new IllegalArgumentException ("Unavailable hardware settings cannot contain observed values");
    }

    public static ControllerHardwareSettingsSnapshot empty ()
    {
        return new ControllerHardwareSettingsSnapshot (false, 0, 0, 0, 0, 0, List.of ());
    }
}
