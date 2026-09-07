// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.component;

import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;
import static de.mossgrabers.pull.core.ui.PageStyle.*;

/** A value-only choice with matching display and button feedback. Unavailable choices stay blank. */
public record ChoiceCell (String label, boolean available, boolean selected)
{
    /** Geometry is local to the cell; the consuming page chooses its position and physical button. */
    public record Style (double width, double height, double insetX, double insetY, double fontSize,
                         double minimumFontSize, RgbColor background) { }

    public RgbColor lightColor ()
    {
        if (!this.visible ()) return BLACK;
        return this.selected ? WHITE : GREY;
    }

    public void append (final List<DisplayCommand> commands, final double left, final double top, final Style style)
    {
        if (!this.visible ()) return;
        commands.add (new DisplayCommand.Rectangle (left, top, style.width (), style.height (), this.selected ? WHITE : style.background ()));
        commands.add (new DisplayCommand.TextBox (this.label, left + style.insetX (), top + style.insetY (),
            style.width () - 2 * style.insetX (), style.height () - 2 * style.insetY (), DisplayTextAlignment.CENTER,
            this.selected ? BLACK : WHITE, style.fontSize (), style.minimumFontSize (), DisplayTextFit.SHRINK_ELLIPSIS));
    }

    private boolean visible ()
    {
        return this.available && this.label != null && !this.label.isEmpty ();
    }
}
