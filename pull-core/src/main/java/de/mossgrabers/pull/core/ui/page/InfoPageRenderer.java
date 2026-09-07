// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.output.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static de.mossgrabers.pull.core.ui.PageStyle.*;

/** Pure hardware-identity output. The Info/Setup menu remains available before identity arrives. */
public final class InfoPageRenderer
{
    private InfoPageRenderer () { }

    public static PageVisuals render (final InfoPagePresentation state)
    {
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        final List<DisplayCommand> commands = new ArrayList<> ();
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK));
        ConfigurationTabs.append (commands, lights, false);
        if (state.hardwareAvailable ())
        {
            field (commands, 0, 3, "Firmware", state.firmware ());
            field (commands, 3, 2, "Board Revision", state.boardRevision ());
            field (commands, 5, 3, "Serial Number", state.serialNumber ());
        }
        else
            commands.add (new DisplayCommand.TextBox ("Waiting for Push hardware information", InfoPageStyle.INSET, InfoPageStyle.STATUS_TOP,
                WIDTH - 2 * InfoPageStyle.INSET, InfoPageStyle.STATUS_HEIGHT, DisplayTextAlignment.LEFT, WHITE,
                InfoPageStyle.STATUS_FONT, InfoPageStyle.MINIMUM_FONT, DisplayTextFit.SHRINK_ELLIPSIS));
        return new PageVisuals (lights, new ControllerDisplayScene (WIDTH, HEIGHT, commands));
    }

    private static void field (final List<DisplayCommand> commands, final int column, final int span, final String label, final String value)
    {
        final double left = column * COLUMN_WIDTH + InfoPageStyle.INSET;
        final double width = span * COLUMN_WIDTH - 2 * InfoPageStyle.INSET;
        commands.add (new DisplayCommand.TextBox (label, left, InfoPageStyle.LABEL_TOP, width, InfoPageStyle.LABEL_HEIGHT,
            DisplayTextAlignment.LEFT, WHITE, InfoPageStyle.LABEL_FONT, InfoPageStyle.MINIMUM_FONT, DisplayTextFit.SHRINK_ELLIPSIS));
        commands.add (new DisplayCommand.TextBox (value, left, InfoPageStyle.VALUE_TOP, width, InfoPageStyle.VALUE_HEIGHT,
            DisplayTextAlignment.LEFT, WHITE, InfoPageStyle.VALUE_FONT, InfoPageStyle.MINIMUM_FONT, DisplayTextFit.SHRINK_ELLIPSIS));
    }
}
