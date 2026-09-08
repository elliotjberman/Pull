// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.component;

import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;

/** A bounded horizontal slider with a center reference and normalized value. */
public final class BipolarSlider
{
    public record Style (double width, double railHeight, double markerWidth, double markerHeight, double railRadius, double markerRadius) { }

    private BipolarSlider () { }

    public static void append (final List<DisplayCommand> commands, final double left, final double centerY, final double value, final RgbColor accent, final RgbColor background, final Style style)
    {
        final double centerX = left + style.width () / 2;
        final double markerX = left + style.markerWidth () / 2 + value * (style.width () - style.markerWidth ());
        final double railTop = centerY - style.railHeight () / 2;
        commands.add (rectangle (left, railTop, style.width (), style.railHeight (), style.railRadius (), background));
        commands.add (new DisplayCommand.Rectangle (Math.min (centerX, markerX), railTop, Math.abs (markerX - centerX), style.railHeight (), accent));
        commands.add (new DisplayCommand.Rectangle (centerX - 1, centerY - style.markerHeight () / 2, 2, style.markerHeight (), background));
        commands.add (rectangle (markerX - style.markerWidth () / 2, centerY - style.markerHeight () / 2, style.markerWidth (), style.markerHeight (), style.markerRadius (), accent));
    }

    private static DisplayCommand rectangle (final double left, final double top, final double width, final double height, final double radius, final RgbColor color)
    {
        return radius == 0 ? new DisplayCommand.Rectangle (left, top, width, height, color) : new DisplayCommand.RoundedRectangle (left, top, width, height, radius, color);
    }
}
