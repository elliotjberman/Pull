// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.component;

import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;
import java.util.Objects;

/** Bounded parameter value and unit fields with family-specific typography. */
public final class ParameterValue
{
    /** The caller formats observed values and decides which suffix is a unit. */
    public record Content (String value, String unit)
    {
        public Content
        {
            value = Objects.requireNonNull (value, "value");
            unit = Objects.requireNonNull (unit, "unit");
        }
    }

    /** Unit baseline is relative to the top of the value field. */
    public record Style (double width, double height, double fontSize, double minimumFontSize, double valueWidth, double unitGap, double unitFontSize, double unitBaseline, DisplayTextFit fit) { }

    private ParameterValue () { }

    public static void append (final List<DisplayCommand> commands, final Content content, final double left, final double top, final RgbColor color, final Style style)
    {
        if (content.value ().isBlank ())
            return;
        final boolean hasUnit = !content.unit ().isEmpty ();
        commands.add (new DisplayCommand.TextBox (content.value (), left, top, hasUnit ? style.valueWidth () : style.width (), style.height (), DisplayTextAlignment.LEFT, color, style.fontSize (), style.minimumFontSize (), style.fit ()));
        if (hasUnit)
        {
            final double unitLeft = left + style.valueWidth () + style.unitGap ();
            final double unitTop = style.unitBaseline () - style.unitFontSize ();
            final double unitHeight = Math.min (style.height () - unitTop, 1.4 * style.unitFontSize ());
            commands.add (new DisplayCommand.TextBox (content.unit (), unitLeft, top + unitTop, style.width () - style.valueWidth () - style.unitGap (), unitHeight, DisplayTextAlignment.LEFT, color, style.unitFontSize (), Math.min (style.unitFontSize (), style.minimumFontSize ()), DisplayTextFit.SHRINK_ELLIPSIS));
        }
    }
}
