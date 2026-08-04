// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.graphics.canvas.component.ParameterComponent.MeterColors;


/** Eight-step navy-to-white palette for Project Macro controls. */
final class ProjectMacroColors
{
    private static final MeterColors [] COLORS =
    {
        colors (8, 21, 40, 38, 82, 148, 92, 134, 188),
        colors (11, 30, 47, 65, 120, 179, 120, 163, 205),
        colors (15, 40, 54, 92, 157, 209, 148, 192, 222),
        colors (18, 49, 61, 119, 195, 240, 176, 221, 239),
        colors (25, 56, 66, 147, 219, 255, 199, 238, 248),
        colors (34, 61, 68, 177, 229, 255, 217, 244, 250),
        colors (43, 66, 71, 208, 239, 255, 234, 249, 253),
        colors (52, 70, 73, 238, 249, 255, 252, 254, 255)
    };


    private ProjectMacroColors ()
    {
        // Utility class
    }


    static MeterColors at (final int index)
    {
        if (index < 0 || index >= COLORS.length)
            throw new IllegalArgumentException ("Project Macro index must be between 0 and 7");
        return COLORS[index];
    }


    private static MeterColors colors (final int offRed, final int offGreen, final int offBlue, final int onRed, final int onGreen, final int onBlue, final int textRed, final int textGreen, final int textBlue)
    {
        return new MeterColors (
            ColorEx.fromRGB (offRed, offGreen, offBlue),
            ColorEx.fromRGB (onRed, onGreen, onBlue),
            ColorEx.fromRGB (textRed, textGreen, textBlue));
    }
}
