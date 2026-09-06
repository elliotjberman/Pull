// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.RgbColor;

/** Shared mixer geometry, palette and disabled-state styling. */
public final class MixerPageStyle
{
    public static final int PARAMETER_HEIGHT = 143;
    public static final double COLUMN = PageStyle.COLUMN_WIDTH;
    public static final double MENU_HEIGHT = PageStyle.HEIGHT / 12.0 + 4;
    public static final RgbColor SELECTED_MENU = new RgbColor (190, 190, 190);
    public static final RgbColor DIM_WHITE = new RgbColor (102, 102, 102);
    public static final RgbColor DIM_DARK = new RgbColor (25, 25, 25);
    public static final RgbColor GREEN = new RgbColor (0, 255, 0);
    public static final RgbColor ORANGE = new RgbColor (255, 84, 0);

    private MixerPageStyle () { }

    public static RgbColor dim (final RgbColor color)
    {
        final int gray = (int) Math.round ((color.red () + color.green () + color.blue ()) / 3.0 * 0.4);
        return new RgbColor (gray, gray, gray);
    }
}
