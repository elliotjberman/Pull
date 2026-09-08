// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.component;

import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** A bounded visible list whose selected rows use the same marker as an option cell. */
public final class TextList
{
    public record Item (String label, String detail, boolean selected, RgbColor color)
    {
        public Item { label = Objects.requireNonNullElse (label, ""); detail = Objects.requireNonNullElse (detail, ""); color = Objects.requireNonNull (color); }
        public Item (final String label, final boolean selected, final RgbColor color) { this (label, "", selected, color); }
    }

    private TextList () { }

    public static void append (final List<DisplayCommand> commands, final List<Item> items, final double left, final double top, final double width, final double height, final int rows)
    {
        if (rows < 1 || rows > 8 || items.size () > rows) throw new IllegalArgumentException ("A visible list has at most eight rows");
        final double rowHeight = height / rows;
        for (int index = 0; index < items.size (); index++)
        {
            final Item item = items.get (index);
            final RgbColor dim = new RgbColor ((item.color ().red () + 1) / 2, (item.color ().green () + 1) / 2, (item.color ().blue () + 1) / 2);
            final double detailWidth = item.detail ().isEmpty () ? 0 : Math.min (60, width * 0.45);
            final ChoiceCell.Style style = new ChoiceCell.Style (width - 2 - detailWidth, rowHeight, 6, 2, Math.min (16, rowHeight * 0.6), Math.min (16, rowHeight * 0.6));
            new ChoiceCell (TextContent.candidate (item.label (), 32), true, item.selected ()).append (commands, left, top + index * rowHeight, style, item.color (), dim);
            if (detailWidth > 0)
                commands.add (new DisplayCommand.TextBox (TextContent.candidate (item.detail (), 16), left + width - detailWidth - 2, top + index * rowHeight + 2,
                    detailWidth, rowHeight - 4, DisplayTextAlignment.CENTER, item.selected () ? item.color () : dim, 12, 8, DisplayTextFit.SHRINK));
        }
    }

    /** Preserve the established scrolling window while keeping host name strings intact. */
    public static List<Item> window (final List<String> names, final int selected, final int rows, final RgbColor color)
    {
        if (rows < 1 || rows > 8 || names.size () > 128) throw new IllegalArgumentException ("List window exceeds its capacity");
        final int start = Math.max (0, Math.min (selected, names.size () - rows));
        final List<Item> result = new ArrayList<> (rows);
        for (int index = start; index < Math.min (names.size (), start + rows); index++) result.add (new Item (names.get (index), index == selected, color));
        return List.copyOf (result);
    }
}
