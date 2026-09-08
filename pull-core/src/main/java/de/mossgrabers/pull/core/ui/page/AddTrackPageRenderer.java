// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.*;
import java.util.List;
import static de.mossgrabers.pull.core.ui.page.OptionPageLayout.*;

public final class AddTrackPageRenderer
{
    private AddTrackPageRenderer () { }
    public static ControllerDisplayScene render (final AddTrackPagePresentation state)
    {
        final List<DisplayCommand> commands = background ();
        if (state.available ())
        {
            for (final var kind: state.kinds ()) column (commands, kind.column (), choice (kind.label (), false), blank (), kind.color (), null);
            column (commands, 0, blank (), choice (state.primaryAction (), false), null, state.selectedColor ());
            for (int index = 0; index < state.shortcuts ().size (); index++) column (commands, index + 1, blank (), choice (state.shortcuts ().get (index), false));
            heading (commands, "Add Track", 0, 4, false);
            heading (commands, "Add Device", 4, 4, false);
            heading (commands, state.selectedKind (), 0, 8, true);
        }
        return scene (commands);
    }
}
