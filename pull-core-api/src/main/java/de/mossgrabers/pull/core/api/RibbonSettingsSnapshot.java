// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

/** Observed global strip preferences; this value does not own the physical strip. */
public record RibbonSettingsSnapshot (boolean available, int function, int cc, int noteRepeat)
{
    public RibbonSettingsSnapshot
    {
        if (function < 0 || function > 5 || cc < 0 || cc > 127 || noteRepeat < 0 || noteRepeat > 2 || !available && (function != 0 || cc != 0 || noteRepeat != 0))
            throw new IllegalArgumentException ("Invalid ribbon preferences");
    }

    public static RibbonSettingsSnapshot empty () { return new RibbonSettingsSnapshot (false, 0, 0, 0); }
}
