// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static de.mossgrabers.pull.core.ui.PageStyle.*;

/** Ribbon settings use the shared large option rows, with page-specific physical light meaning. */
public final class RibbonPageRenderer
{
    private static final List<String> UPPER = List.of ("", "Modulation", "Expression", "Volume", "Sustain", "Off", "Period", "Length");
    private static final List<String> LOWER = List.of ("Pitchbend", "CC", "CC/Pitch", "Pitch/CC", "Fader", "Last Touched", "", "");

    private RibbonPageRenderer () { }

    public static PageVisuals render (final RibbonPagePresentation state)
    {
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        final List<DisplayCommand> commands = new ArrayList<> ();
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK));
        for (int index = 0; index < COLUMNS; index++)
        {
            final ChoiceCell upper = new ChoiceCell (index == 0 ? Integer.toString (state.cc ()) : UPPER.get (index), state.available (),
                index == 0 || index >= 5 && state.noteRepeat () == index - 5);
            final ChoiceCell lower = new ChoiceCell (LOWER.get (index), state.available (), state.function () == index);
            upper.append (commands, index * COLUMN_WIDTH, 0, FramePageStyle.CHOICE);
            lower.append (commands, index * COLUMN_WIDTH, FramePageStyle.LOWER_TOP, FramePageStyle.CHOICE);
            // The numeric CC cell identifies the encoder; its row button is inert. Presets are actions.
            lights.put (PushControlIds.button ("ROW2_" + (index + 1)), index == 0 ? BLACK : upper.lightColor ());
            lights.put (PushControlIds.button ("ROW1_" + (index + 1)), lower.lightColor ());
        }
        if (state.available ())
        {
            heading (commands, "CC", 0, 1, FramePageStyle.OPTIONS_HEADING_TOP);
            heading (commands, "Quick Select", 1, 4, FramePageStyle.OPTIONS_HEADING_TOP);
            heading (commands, "Note Repeat", 5, 3, FramePageStyle.OPTIONS_HEADING_TOP);
            heading (commands, "Function", 0, 6, FramePageStyle.LOWER_HEADING_TOP);
        }
        return new PageVisuals (lights, new ControllerDisplayScene (WIDTH, HEIGHT, commands));
    }

    private static void heading (final List<DisplayCommand> commands, final String text, final int column, final int span, final double top)
    {
        commands.add (new DisplayCommand.TextBox (text, column * COLUMN_WIDTH + 4, top, span * COLUMN_WIDTH - 8, FramePageStyle.HEADING_HEIGHT,
            DisplayTextAlignment.LEFT, WHITE, FramePageStyle.HEADING_FONT, FramePageStyle.HEADING_MIN_FONT, DisplayTextFit.SHRINK_ELLIPSIS));
    }
}
