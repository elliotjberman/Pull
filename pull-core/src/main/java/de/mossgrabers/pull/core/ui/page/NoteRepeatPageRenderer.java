// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.ui.component.ParameterValue;
import de.mossgrabers.pull.core.ui.component.RingMeter;
import de.mossgrabers.pull.core.ui.component.TextList;
import de.mossgrabers.pull.core.ui.component.TextContent;
import java.util.List;
import static de.mossgrabers.pull.core.ui.PageStyle.*;
import static de.mossgrabers.pull.core.ui.page.OptionPageLayout.*;

public final class NoteRepeatPageRenderer
{
    private static final RingMeter.Style RING = new RingMeter.Style (18, 220, -260, 160, 1);
    private static final ParameterValue.Style VALUE = new ParameterValue.Style (104, 27, 24, 10, 64, 2, 12, 23, DisplayTextFit.SHRINK_ELLIPSIS);
    private NoteRepeatPageRenderer () { }
    public static ControllerDisplayScene render (final NoteRepeatPagePresentation state)
    {
        final List<DisplayCommand> commands = background ();
        if (state.available ())
        {
            heading (commands, "Period", 0, 1, false);
            TextList.append (commands, TextList.window (state.resolutions (), state.period (), 6, WHITE), COLUMN_WIDTH, 0, COLUMN_WIDTH, HEIGHT, 6);
            heading (commands, "Length", 2, 1, false);
            TextList.append (commands, TextList.window (state.resolutions (), state.length (), 6, WHITE), 3 * COLUMN_WIDTH, 0, COLUMN_WIDTH, HEIGHT, 6);
            column (commands, 4, blank (), choice ("Latch", state.latch ()));
            column (commands, 5, blank (), choice ("Use Pressure", state.pressure ()));
            column (commands, 6, blank (), choice ("Sync", state.synced ()));
            column (commands, 7, choice (state.grooveLabel (), state.grooveEnabled ()), choice ("Shuffle", state.shuffle ()));
            parameter (commands, 5, state.mode ()); parameter (commands, 6, state.octaves ()); parameter (commands, 7, state.shuffleAmount ());
        }
        return scene (commands);
    }
    private static void parameter (final List<DisplayCommand> commands, final int column, final NoteRepeatPagePresentation.Parameter value)
    {
        final double left = column * COLUMN_WIDTH + 8;
        final RgbColor text = MacroPageStyle.brightness (MacroPageStyle.METER_TEXT, value.touched ());
        commands.add (new DisplayCommand.TextBox (TextContent.candidate (value.name (), 20), left, 37, 104, 19, DisplayTextAlignment.LEFT, text, 13, 9, DisplayTextFit.SHRINK_ELLIPSIS));
        ParameterValue.append (commands, new ParameterValue.Content (TextContent.candidate (value.text (), 24), ""), left, 57, text, VALUE);
        RingMeter.append (commands, left + RING.radius (), 104, Math.max (0, Math.min (1, value.value ())), MacroPageStyle.brightness (MacroPageStyle.METER_OFF, value.touched ()), MacroPageStyle.brightness (MacroPageStyle.METER_ON, value.touched ()), RING);
    }
}
