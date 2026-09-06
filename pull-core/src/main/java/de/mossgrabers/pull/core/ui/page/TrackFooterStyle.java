// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.RgbColor;

/** Shared footer geometry and selected/armed feedback palette. */
public final class TrackFooterStyle
{
    public static final double HEIGHT = 17;
    public static final double INSET = 7.7;
    public static final double FONT_SIZE = PageStyle.HEIGHT / 12.0;
    public static final RgbColor ARMED = new RgbColor (255, 0, 0);

    private TrackFooterStyle () { }

    public static RgbColor contrast (final RgbColor color)
    {
        final double luminance = 0.2126 * color.red () + 0.7152 * color.green () + 0.0722 * color.blue ();
        return luminance > 0.179 * 255 ? PageStyle.BLACK : PageStyle.WHITE;
    }
}
