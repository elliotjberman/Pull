// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.*;
import java.util.List;
import static de.mossgrabers.pull.core.ui.page.OptionPageLayout.*;

public final class ScaleLayoutPageRenderer
{
    private ScaleLayoutPageRenderer () { }
    public static ControllerDisplayScene render (final ScaleLayoutPagePresentation state)
    {
        final List<DisplayCommand> commands = background ();
        for (int index = 0; index < state.layouts ().size (); index++) column (commands, index, blank (), choice (state.layouts ().get (index), index == state.selected ()));
        column (commands, 7, blank (), choice (state.vertical () ? "Vertical" : "Horizontal", false));
        heading (commands, "Scale layout", 0, 6, true);
        return scene (commands);
    }
}
