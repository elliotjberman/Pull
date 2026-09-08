// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.component;

import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;

/** A pair of option rows. The page supplies choices, resolved colors and local geometry. */
public record OptionColumn (ChoiceCell upper, ChoiceCell lower, RgbColor upperColor, RgbColor lowerColor)
{
    public void append (final List<DisplayCommand> commands, final double left, final double top, final double height, final ChoiceCell.Style style)
    {
        appendChoice (commands, upper, left, top, style, upperColor);
        appendChoice (commands, lower, left, top + height - style.height (), style, lowerColor);
    }

    private static void appendChoice (final List<DisplayCommand> commands, final ChoiceCell choice, final double left, final double top, final ChoiceCell.Style style, final RgbColor color)
    {
        final ChoiceCell bounded = new ChoiceCell (TextContent.candidate (choice.label (), 20), choice.available (), choice.selected ());
        if (color == null) bounded.append (commands, left, top, style);
        else bounded.append (commands, left, top, style, color, color);
    }
}
