// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.component;

import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;

/** Bottom-up level meter with supplied geometry and optional threshold colors. */
public final class VerticalMeter
{
    public record Style (double width, double height) { }
    public record Band (double start, double end, RgbColor color) { }

    private VerticalMeter () { }

    public static void append (final List<DisplayCommand> commands, final double left, final double top, final double value, final RgbColor accent, final RgbColor background, final Style style)
    {
        append (commands, left, top, value, background, List.of (new Band (0, 1, accent)), style);
    }

    public static void append (final List<DisplayCommand> commands, final double left, final double top, final double value, final RgbColor background, final List<Band> bands, final Style style)
    {
        commands.add (new DisplayCommand.Rectangle (left, top, style.width (), style.height (), background));
        for (final Band band: bands)
        {
            final double end = Math.min (value, band.end ());
            if (end > band.start ())
                commands.add (new DisplayCommand.Rectangle (left, top + style.height () * (1 - end), style.width (), style.height () * (end - band.start ()), band.color ()));
        }
    }
}
