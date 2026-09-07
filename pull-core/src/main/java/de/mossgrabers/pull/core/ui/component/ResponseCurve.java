// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.component;

import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;

/** Bounded, evenly sampled response data. Values and colors are supplied by the consuming page. */
public final class ResponseCurve
{
    public static final int MAX_SAMPLES = 128;

    /** The complete stroke stays inside these local bounds; no component-owned clip is needed. */
    public record Style (double width, double height, double lineWidth)
    {
        public Style
        {
            if (!Double.isFinite (width) || !Double.isFinite (height) || !Double.isFinite (lineWidth) || lineWidth <= 0 || width <= lineWidth || height <= lineWidth)
                throw new IllegalArgumentException ("Curve bounds must contain a finite positive stroke");
        }
    }

    private ResponseCurve () { }

    /** Empty samples draw nothing. A present curve contains two to 128 finite values in [0, 1]. */
    public static void append (final List<DisplayCommand> commands, final List<Double> samples, final double left, final double top, final RgbColor color, final Style style)
    {
        if (samples.isEmpty ()) return;
        if (samples.size () < 2 || samples.size () > MAX_SAMPLES || samples.stream ().anyMatch (value -> value == null || !Double.isFinite (value) || value < 0 || value > 1))
            throw new IllegalArgumentException ("A response curve requires two to 128 normalized samples");
        final double inset = style.lineWidth () / 2;
        final double width = style.width () - style.lineWidth ();
        final double height = style.height () - style.lineWidth ();
        double previousX = left + inset;
        double previousY = top + inset + (1 - samples.get (0)) * height;
        for (int index = 1; index < samples.size (); index++)
        {
            final double x = left + inset + index * width / (samples.size () - 1);
            final double y = top + inset + (1 - samples.get (index)) * height;
            commands.add (new DisplayCommand.Line (previousX, previousY, x, y, style.lineWidth (), color));
            previousX = x;
            previousY = y;
        }
    }
}
