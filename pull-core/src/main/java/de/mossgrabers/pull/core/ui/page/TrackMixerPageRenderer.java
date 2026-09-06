// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import static de.mossgrabers.pull.core.ui.page.PageStyle.*;
import static de.mossgrabers.pull.core.ui.page.MixerPageStyle.PARAMETER_HEIGHT;
import static de.mossgrabers.pull.core.ui.page.MixerPageStyle.DIM_WHITE;

/** Pure selected-track mixer renderer; its input already contains aligned, normalized controls. */
public final class TrackMixerPageRenderer
{
    private static final int MENU_HEIGHT = 17;
    private TrackMixerPageRenderer () { }

    public static PageVisuals render (final TrackMixerPagePresentation page)
    {
        final ArrayList<DisplayCommand> commands = new ArrayList<> (96);
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, PARAMETER_HEIGHT, BLACK));
        drawMenu (commands, 0, "Mix", !page.inputOutput (), page.menuAccent ().orElse (MixerPageStyle.SELECTED_MENU));
        drawMenu (commands, 1, "Input & Output", page.inputOutput (), page.menuAccent ().orElse (MixerPageStyle.SELECTED_MENU));
        if (page.sendsLeft ()) drawMenu (commands, 6, "<", false, page.menuAccent ().orElse (MixerPageStyle.SELECTED_MENU));
        else if (page.sendsRight ()) drawMenu (commands, 7, ">", false, page.menuAccent ().orElse (MixerPageStyle.SELECTED_MENU));
        for (final TrackMixerPagePresentation.Metadata item: page.metadata ())
        {
            final double left = item.column () * COLUMN_WIDTH + 8;
            final RgbColor color = item.active () ? WHITE : DIM_WHITE;
            commands.add (new DisplayCommand.TextAt (item.label (), left, 34, color, 12.5));
            commands.add (new DisplayCommand.TextAt (item.value (), left, 55, color, 19));
        }
        page.controls ().forEach (control -> MixerDisplayScene.append (commands, control));
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        for (int index = 0; index < COLUMNS; index++) lights.put (upper (index), BLACK);
        lights.put (upper (page.inputOutput () ? 1 : 0), WHITE);
        if (page.sendsLeft ()) lights.put (upper (6), WHITE);
        else if (page.sendsRight ()) lights.put (upper (7), WHITE);
        return new PageVisuals (lights, new ControllerDisplayScene (WIDTH, PARAMETER_HEIGHT, commands));
    }

    private static void drawMenu (final ArrayList<DisplayCommand> commands, final int column, final String text, final boolean selected, final RgbColor menuColor)
    {
        final double left = column * COLUMN_WIDTH;
        if (selected) commands.add (new DisplayCommand.Rectangle (left, 0, COLUMN_WIDTH - 2, MENU_HEIGHT, menuColor));
        commands.add (new DisplayCommand.TextBox (text, left + 7, 0, COLUMN_WIDTH - 14, MENU_HEIGHT,
            DisplayTextAlignment.LEFT, selected ? BLACK : WHITE, 12, 10, DisplayTextFit.SHRINK_ELLIPSIS));
    }

    private static ControlId upper (final int column) { return PushControlIds.button ("ROW2_" + (column + 1)); }
}
