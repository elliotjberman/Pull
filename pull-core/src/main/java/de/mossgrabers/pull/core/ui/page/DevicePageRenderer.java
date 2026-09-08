// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.DevicePageState;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import de.mossgrabers.pull.core.ui.component.TextContent;
import de.mossgrabers.pull.core.ui.component.Toggle;
import java.util.ArrayList;
import java.util.List;
import static de.mossgrabers.pull.core.ui.PageStyle.*;

/** Complete core-owned image for frozen Device/User/channel pages, shared with the offline catalog. */
public final class DevicePageRenderer
{
    private static final ChoiceCell.Style MENU = new ChoiceCell.Style (COLUMN_WIDTH - 2, 17, 5, 0, 13, 9, DisplayTextFit.SHRINK);
    private DevicePageRenderer () { }

    public static ControllerDisplayScene render (final DevicePageState state) { return render (DevicePageProjector.project (state)); }

    public static ControllerDisplayScene render (final DevicePagePresentation page)
    {
        final List<DisplayCommand> commands = new ArrayList<> (List.of (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK)));
        for (int index = 0; index < page.upper ().size (); index++) page.upper ().get (index).append (commands, index * COLUMN_WIDTH, 0, MENU);
        for (final var control: page.controls ()) MixerDisplayScene.append (commands, control);
        for (int index = 0; index < page.lower ().size (); index++) page.lower ().get (index).append (commands, index * COLUMN_WIDTH, HEIGHT - 17, MENU);
        for (final var cell: page.footer ().cells ()) TrackFooterRenderer.append (commands, cell.column (), HEIGHT - TrackFooterStyle.HEIGHT,
            cell.name (), cell.icon (), cell.color (), cell.selected (), cell.active ());
        for (final var toggle: page.toggles ()) Toggle.append (commands, toggle.column () * COLUMN_WIDTH + 12, 90, toggle.on (), WHITE);
        if (!page.heading ().isEmpty ()) text (commands, page.heading (), 12, 30, WIDTH - 24, 29, 22);
        if (!page.message ().isEmpty ()) text (commands, page.message (), 24, 58, WIDTH - 48, 45, 25);
        return new ControllerDisplayScene (WIDTH, HEIGHT, commands);
    }

    private static void text (final List<DisplayCommand> commands, final String text, final double left, final double top, final double width, final double height, final double size)
    {
        commands.add (new DisplayCommand.TextBox (TextContent.candidate (text, 96), left, top, width, height, DisplayTextAlignment.LEFT, WHITE, size, 12, DisplayTextFit.SHRINK_ELLIPSIS));
    }
}
