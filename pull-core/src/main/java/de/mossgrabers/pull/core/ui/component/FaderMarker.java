// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.component;

import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;

/** A horizontal value marker and its vertical rail, independent of any parameter target. */
public final class FaderMarker
{
    /** Overlap describes how far the horizontal marker crosses the rail's left edge. */
    public record Style (double height, double markerWidth, double lineWidth, double overlap) { }

    private FaderMarker () { }

    public static void append (final List<DisplayCommand> commands, final double railX, final double top, final double value, final RgbColor color, final Style style)
    {
        final double markerY = top + (1 - value) * style.height ();
        commands.add (new DisplayCommand.Rectangle (railX - style.markerWidth () + style.overlap (), markerY, style.markerWidth (), style.lineWidth (), color));
        commands.add (new DisplayCommand.Rectangle (railX, markerY, style.lineWidth (), top + style.height () - markerY, color));
    }
}
