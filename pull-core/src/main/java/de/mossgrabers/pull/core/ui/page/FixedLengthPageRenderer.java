// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.*;
import java.util.List;
import static de.mossgrabers.pull.core.ui.page.OptionPageLayout.*;

public final class FixedLengthPageRenderer
{
    private FixedLengthPageRenderer () { }
    public static ControllerDisplayScene render (final FixedLengthPagePresentation state)
    {
        final List<DisplayCommand> commands = background ();
        for (int index = 0; index < state.lengths ().size (); index++) column (commands, index, choice (state.lengths ().get (index), false), choice (state.lengths ().get (index), index == state.selected ()));
        heading (commands, "Create Clip (length not stored)", 0, 8, false);
        heading (commands, "New Clip Length", 0, 8, true);
        return scene (commands);
    }
}
