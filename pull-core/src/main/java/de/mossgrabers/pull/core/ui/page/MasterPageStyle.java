// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.ui.PageStyle;

import de.mossgrabers.pull.core.api.output.RgbColor;

/** Geometry and visual treatment of the full-width Master page. */
public final class MasterPageStyle
{
    public static final int     WIDTH                   = PageStyle.WIDTH;
    public static final int     HEIGHT                  = PageStyle.HEIGHT;
    public static final double  COLUMN_WIDTH            = WIDTH / 8.0;
    public static final double  CONTENT_LEFT            = 8.0;
    public static final double  MENU_HEIGHT             = HEIGHT / 12.0 + 4.0;
    public static final double  LABEL_BASELINE          = 34.0;
    public static final double  LABEL_FONT_SIZE         = 12.5;
    public static final double  STATUS_VALUE_TOP        = 35.0;
    public static final double  STATUS_VALUE_HEIGHT     = 25.0;
    public static final double  STATUS_MAX_FONT_SIZE    = 19.0;
    public static final double  STATUS_MIN_FONT_SIZE    = 12.0;
    public static final double  RING_CENTER_Y           = 106.0;
    public static final double  FOOTER_TOP              = 143.0;

    public static final RgbColor BLACK       = PageStyle.BLACK;
    public static final RgbColor WHITE       = PageStyle.WHITE;
    public static final RgbColor DIM_WHITE   = MixerPageStyle.DIM_WHITE;
    public static final RgbColor GRAY        = new RgbColor (128, 128, 128);
    public static final RgbColor FOOTER_GRAY = PageStyle.GREY;
    public static final RgbColor TOGGLE_ON   = new RgbColor (55, 185, 64);

    private MasterPageStyle () { }
}
