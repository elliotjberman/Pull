// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.ui.component.TextList;
import java.util.List;
import static de.mossgrabers.pull.core.ui.PageStyle.*;
import static de.mossgrabers.pull.core.ui.page.OptionPageLayout.*;

public final class ScalesPageRenderer
{
    private ScalesPageRenderer () { }
    public static ControllerDisplayScene render (final ScalesPagePresentation state)
    {
        final List<DisplayCommand> commands = background ();
        TextList.append (commands, TextList.window (state.scales (), state.selectedScale (), 6, WHITE), 0, 0, COLUMN_WIDTH, HEIGHT, 6);
        for (int index = 0; index < 6; index++) column (commands, index + 1, choice (state.roots ().get (index + 6), state.selectedRoot () == index + 6), choice (state.roots ().get (index), state.selectedRoot () == index));
        column (commands, 7, choice (state.chromatic () ? "Chromatic" : "In Key", state.chromatic ()), blank ());
        heading (commands, "Note range: " + state.range (), 4, 4, false);
        return scene (commands);
    }
}
