// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;

import java.util.Objects;

/** Alternate light colour and hardware blink rate; phase is determined by the output transport. */
public record LightBlink (RgbColor alternateColor, boolean fast)
{
    public LightBlink
    {
        alternateColor = Objects.requireNonNull (alternateColor, "alternateColor");
    }
}
