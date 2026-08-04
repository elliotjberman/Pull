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
        colors (4, 12, 30, 18, 45, 105, 58, 92, 153),
        colors (8, 23, 40, 51, 100, 166, 99, 146, 196),
        colors (13, 37, 52, 88, 156, 216, 143, 192, 226),
        colors (18, 49, 61, 119, 195, 240, 176, 221, 239),
        colors (25, 56, 66, 147, 219, 255, 199, 238, 248),
        colors (38, 63, 69, 187, 234, 255, 224, 247, 252),
        colors (49, 69, 73, 224, 246, 255, 242, 252, 254),
        colors (60, 76, 78, 255, 255, 255, 255, 255, 255)
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
