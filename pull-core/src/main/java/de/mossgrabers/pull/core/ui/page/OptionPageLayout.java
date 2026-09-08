// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import de.mossgrabers.pull.core.ui.component.OptionColumn;
import de.mossgrabers.pull.core.ui.component.TextContent;
import java.util.ArrayList;
import java.util.List;
import static de.mossgrabers.pull.core.ui.PageStyle.*;

/** Fixed option-page geometry shared by the original two-row families. */
final class OptionPageLayout
{
    private OptionPageLayout () { }
    static List<DisplayCommand> background () { return new ArrayList<> (List.of (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK))); }
    static ControllerDisplayScene scene (final List<DisplayCommand> commands) { return new ControllerDisplayScene (WIDTH, HEIGHT, commands); }
    static void column (final List<DisplayCommand> commands, final int index, final ChoiceCell upper, final ChoiceCell lower)
    { column (commands, index, upper, lower, null, null); }
    static void column (final List<DisplayCommand> commands, final int index, final ChoiceCell upper, final ChoiceCell lower, final RgbColor upperColor, final RgbColor lowerColor)
    { new OptionColumn (upper, lower, upperColor, lowerColor).append (commands, index * COLUMN_WIDTH, 0, HEIGHT, FramePageStyle.CHOICE); }
    static ChoiceCell choice (final String label, final boolean selected) { return new ChoiceCell (label, true, selected); }
    static ChoiceCell blank () { return choice ("", false); }
    static void heading (final List<DisplayCommand> commands, final String text, final int column, final int span, final boolean lower)
    {
        if (text.isBlank ()) return;
        commands.add (new DisplayCommand.TextBox (TextContent.candidate (text, 96), column * COLUMN_WIDTH + 6, lower ? FramePageStyle.LOWER_HEADING_TOP : FramePageStyle.OPTIONS_HEADING_TOP,
            span * COLUMN_WIDTH - 12, FramePageStyle.HEADING_HEIGHT, DisplayTextAlignment.LEFT, WHITE, FramePageStyle.HEADING_FONT, FramePageStyle.HEADING_FONT, DisplayTextFit.SHRINK_ELLIPSIS));
    }
}
