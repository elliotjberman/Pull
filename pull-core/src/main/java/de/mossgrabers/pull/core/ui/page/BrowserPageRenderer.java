// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import de.mossgrabers.pull.core.ui.component.TextList;
import java.util.List;
import static de.mossgrabers.pull.core.ui.PageStyle.*;
import static de.mossgrabers.pull.core.ui.page.OptionPageLayout.*;

public final class BrowserPageRenderer
{
    private BrowserPageRenderer () { }
    public static ControllerDisplayScene render (final BrowserPagePresentation state)
    {
        final List<DisplayCommand> commands = background ();
        if (!state.available ()) return scene (commands);
        if (state.mode () == BrowserPagePresentation.Mode.OVERVIEW)
        {
            for (int index = 0; index < state.columns ().size (); index++)
            {
                final var value = state.columns ().get (index);
                column (commands, index, new ChoiceCell (value.name (), value.available (), state.selectedColumn () == index),
                    new ChoiceCell (value.selectedValue (), value.available (), value.filtered ()));
            }
            column (commands, 7, choice (state.contentType (), state.selectedColumn () == -1), choice ("Preview", false), null, state.previewColor ());
            heading (commands, state.info (), 0, 8, false);
            heading (commands, "Selection: " + (state.selection ().isBlank () ? "None" : state.selection ()), 0, 8, true);
        }
        else if (state.items ().isEmpty () && state.mode () == BrowserPagePresentation.Mode.RESULTS)
            heading (commands, "No results available...", 3, 5, false);
        else for (int column = 0; column < 8; column++)
        {
            final int start = Math.min (column * 6, state.items ().size ());
            final int end = Math.min (start + 6, state.items ().size ());
            TextList.append (commands, state.items ().subList (start, end), column * COLUMN_WIDTH, 0, COLUMN_WIDTH, HEIGHT, 6);
        }
        return scene (commands);
    }
}
