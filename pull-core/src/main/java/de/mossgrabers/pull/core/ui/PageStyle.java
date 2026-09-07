// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui;

import de.mossgrabers.pull.core.api.output.RgbColor;

/** Shared Push display geometry and neutral palette; family-specific choices stay with their style. */
public final class PageStyle
{
    public static final int WIDTH = 960;
    public static final int HEIGHT = 160;
    public static final int COLUMNS = 8;
    public static final int COLUMN_WIDTH = WIDTH / COLUMNS;
    public static final double TOGGLE_WIDTH = 66.0;
    public static final double TOGGLE_HEIGHT = 32.0;
    public static final double TOGGLE_INSET = 1.4;
    public static final double TOGGLE_THUMB_GAP = 5.0;
    public static final double TOGGLE_THUMB_RADIUS = 10.0;
    public static final RgbColor BLACK = new RgbColor (0, 0, 0);
    public static final RgbColor WHITE = new RgbColor (255, 255, 255);
    public static final RgbColor GREY = new RgbColor (30, 30, 30);
    public static final RgbColor DARK = new RgbColor (63, 63, 63);

    private PageStyle () { }
}
