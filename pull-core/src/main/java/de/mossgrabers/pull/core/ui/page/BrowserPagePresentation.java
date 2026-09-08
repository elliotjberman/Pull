// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.ui.component.TextList;
import java.util.List;

/** Browser overview or one bounded 48-entry filter/result window. */
public record BrowserPagePresentation (boolean available, Mode mode, String info, String selection, String contentType, int selectedColumn,
                                       RgbColor previewColor, List<Column> columns, List<TextList.Item> items)
{
    public enum Mode { OVERVIEW, RESULTS, FILTER }
    public record Column (String name, String selectedValue, boolean available, boolean filtered) { }
    public BrowserPagePresentation
    {
        columns = List.copyOf (columns); items = List.copyOf (items);
        if (columns.size () > 7 || items.size () > 48) throw new IllegalArgumentException ("Browser window exceeds eight display columns");
    }
}
