// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.component;

import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;

/** A dotted parameter ring with separately resolved track and value colors. */
public final class RingMeter
{
    /** Family geometry; the component does not choose parameter roles or touch colors. */
    public record Style (double radius, double startDegrees, double sweepDegrees, int steps, double dotRadius) { }

    private RingMeter () { }

    public static void append (final List<DisplayCommand> commands, final double centerX, final double centerY, final double value, final RgbColor trackColor, final RgbColor valueColor, final Style style)
    {
        commands.add (arc (centerX, centerY, 1, trackColor, style));
        commands.add (arc (centerX, centerY, value, valueColor, style));
    }

    private static DisplayCommand.DottedArc arc (final double centerX, final double centerY, final double value, final RgbColor color, final Style style)
    {
        final int steps = Math.max (2, (int) Math.ceil (style.steps () * value));
        return new DisplayCommand.DottedArc (centerX, centerY, style.radius (), style.startDegrees (), style.sweepDegrees () * value, steps, style.dotRadius (), color);
    }
}
