// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.graphics.canvas.component;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.graphics.IGraphicsContext;


/**
 * Draws a centered horizontal slider for bipolar values such as panning.
 */
public final class BipolarSlider
{
    private static final double RAIL_HEIGHT   = 4.0;
    private static final double MARKER_WIDTH  = 5.0;
    private static final double MARKER_HEIGHT = 16.0;


    private BipolarSlider ()
    {
        // Utility class.
    }


    /**
     * Draw a bipolar slider.
     *
     * @param gc The graphics context
     * @param left The left edge
     * @param centerY The vertical center
     * @param width The rail width
     * @param value The normalized value in the range [0..1]
     * @param accentColor The active color
     * @param backgroundColor The inactive color
     */
    public static void draw (final IGraphicsContext gc, final double left, final double centerY, final double width, final double value, final ColorEx accentColor, final ColorEx backgroundColor)
    {
        final double ratio = Math.max (0, Math.min (1, value));
        final double centerX = left + width / 2.0;
        final double markerX = left + MARKER_WIDTH / 2.0 + ratio * (width - MARKER_WIDTH);
        final double railTop = centerY - RAIL_HEIGHT / 2.0;

        gc.fillRoundedRectangle (left, railTop, width, RAIL_HEIGHT, RAIL_HEIGHT / 2.0, backgroundColor);
        gc.fillRectangle (Math.min (centerX, markerX), railTop, Math.abs (markerX - centerX), RAIL_HEIGHT, accentColor);
        gc.fillRectangle (centerX - 1, centerY - MARKER_HEIGHT / 2.0, 2, MARKER_HEIGHT, backgroundColor);
        gc.fillRoundedRectangle (markerX - MARKER_WIDTH / 2.0, centerY - MARKER_HEIGHT / 2.0, MARKER_WIDTH, MARKER_HEIGHT, MARKER_WIDTH / 2.0, accentColor);
    }
}
