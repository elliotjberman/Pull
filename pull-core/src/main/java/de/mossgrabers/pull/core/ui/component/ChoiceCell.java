// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.component;

import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;
import static de.mossgrabers.pull.core.ui.PageStyle.*;

/** A left-aligned choice with a full-height selection marker. Unavailable choices stay blank. */
public record ChoiceCell (String label, boolean available, boolean selected)
{
    private static final double MARKER_WIDTH = 3;
    private static final RgbColor UNSELECTED = new RgbColor (128, 128, 128);

    /** Geometry is local to the cell; the consuming page chooses its position and physical button. */
    public record Style (double width, double height, double insetX, double insetY, double fontSize,
                         double minimumFontSize, DisplayTextFit fit)
    {
        public Style (final double width, final double height, final double insetX, final double insetY,
                      final double fontSize, final double minimumFontSize)
        { this (width, height, insetX, insetY, fontSize, minimumFontSize, DisplayTextFit.SHRINK_ELLIPSIS); }
    }

    public RgbColor lightColor ()
    {
        if (!this.visible ()) return BLACK;
        return this.selected ? WHITE : GREY;
    }

    public void append (final List<DisplayCommand> commands, final double left, final double top, final Style style)
    {
        this.append (commands, left, top, style, WHITE, UNSELECTED);
    }

    /** Caller-supplied colors retain the shared marker and fitting geometry. */
    public void append (final List<DisplayCommand> commands, final double left, final double top, final Style style, final RgbColor selectedColor, final RgbColor unselectedColor)
    {
        if (!this.visible ()) return;
        final RgbColor color = this.selected ? selectedColor : unselectedColor;
        commands.add (new DisplayCommand.Rectangle (left, top, MARKER_WIDTH, style.height (), color));
        commands.add (new DisplayCommand.TextBox (this.label, left + MARKER_WIDTH + style.insetX (), top + style.insetY (),
            style.width () - MARKER_WIDTH - 2 * style.insetX (), style.height () - 2 * style.insetY (), DisplayTextAlignment.LEFT,
            color, style.fontSize (), style.minimumFontSize (), style.fit ()));
    }

    private boolean visible ()
    {
        return this.available && this.label != null && !this.label.isEmpty ();
    }
}
