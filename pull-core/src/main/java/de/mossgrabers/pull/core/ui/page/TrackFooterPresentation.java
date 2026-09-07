// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.ui.PageStyle;

import de.mossgrabers.pull.core.api.output.DisplayIcon;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;
import java.util.Objects;

/** Visible footer cells after bank selection, label policy and pin/group state have been resolved. */
public record TrackFooterPresentation (List<Cell> cells)
{
    public TrackFooterPresentation
    {
        cells = List.copyOf (cells);
        if (cells.size () > PageStyle.COLUMNS || cells.stream ().map (Cell::column).distinct ().count () != cells.size ())
            throw new IllegalArgumentException ("Footer columns must be unique and bounded");
    }

    public record Cell (int column, String name, DisplayIcon icon, RgbColor color, boolean selected, boolean active, boolean armed)
    {
        public Cell
        {
            if (column < 0 || column >= PageStyle.COLUMNS) throw new IllegalArgumentException ("Footer column outside display");
            Objects.requireNonNull (name, "name");
            Objects.requireNonNull (color, "color");
        }
    }
}
