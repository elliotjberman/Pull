// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static de.mossgrabers.pull.core.ui.PageStyle.*;

/** Renders Frame's fixed layout from observed presentation state only. */
public final class FramePageRenderer
{
    private static final List<FramePagePresentation.Layout> LAYOUTS = List.of (FramePagePresentation.Layout.ARRANGE, FramePagePresentation.Layout.MIX, FramePagePresentation.Layout.EDIT);
    private static final List<String> LOWER = List.of ("Arrange", "Mix", "Edit", "Notes", "Automate", "Device", "Mixer", "Inspector");
    private static final List<String> ARRANGE = List.of ("Clip Launcher", "I/O", "Markers", "Timeline", "FX Tracks", "Follow", "Track Height", "Fullscreen");
    private static final List<String> MIX = List.of ("Clip Launcher", "I/O", "Crossfader", "Device", "Meters", "Sends", "", "Fullscreen");
    private static final List<String> EMPTY = Collections.nCopies (COLUMNS, "");

    private FramePageRenderer () { }

    public static PageVisuals render (final FramePagePresentation state)
    {
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        final List<DisplayCommand> commands = new ArrayList<> ();
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK));
        final List<String> upper = state.available () ? switch (state.layout ())
        {
            case ARRANGE -> ARRANGE;
            case MIX -> MIX;
            default -> EMPTY;
        } : EMPTY;
        for (int index = 0; index < COLUMNS; index++)
        {
            final boolean lowerSelected = state.available () && index < 3 && state.layout () == LAYOUTS.get (index);
            final boolean upperSelected = state.available () && state.upperSelected ().get (index).booleanValue ();
            final ChoiceCell lowerChoice = new ChoiceCell (LOWER.get (index), state.available (), lowerSelected);
            final ChoiceCell upperChoice = new ChoiceCell (upper.get (index), state.available (), upperSelected);
            lights.put (PushControlIds.button ("ROW1_" + (index + 1)), lowerChoice.lightColor ());
            lights.put (PushControlIds.button ("ROW2_" + (index + 1)), upperChoice.lightColor ());
            if (!state.available ()) continue;
            upperChoice.append (commands, index * COLUMN_WIDTH, 0, FramePageStyle.CHOICE);
            lowerChoice.append (commands, index * COLUMN_WIDTH, FramePageStyle.LOWER_TOP, FramePageStyle.CHOICE);
            if (index == 0 && !upper.get (index).isEmpty ())
                heading (commands, index, FramePageStyle.OPTIONS_HEADING_TOP, state.layout () == FramePagePresentation.Layout.ARRANGE ? "Arranger" : "Mixer");
            if (index == 0 || index == 3)
                heading (commands, index, FramePageStyle.LOWER_HEADING_TOP, index == 0 ? "Layouts" : "Panels");
        }
        return new PageVisuals (lights, new ControllerDisplayScene (WIDTH, HEIGHT, commands));
    }

    private static void heading (final List<DisplayCommand> commands, final int column, final double top, final String text)
    {
        commands.add (new DisplayCommand.TextBox (text, column * COLUMN_WIDTH, top, FramePageStyle.HEADING_WIDTH, FramePageStyle.HEADING_HEIGHT, DisplayTextAlignment.LEFT, WHITE, FramePageStyle.HEADING_FONT, FramePageStyle.HEADING_MIN_FONT, DisplayTextFit.CLIP));
    }
}
