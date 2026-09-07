// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.component;

import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;
import static de.mossgrabers.pull.core.ui.PageStyle.*;

/** Shared toggle geometry; each page supplies its position and resolved color. */
public final class Toggle
{
    private Toggle () { }

    public static void append (final List<DisplayCommand> commands, final double left, final double centerY, final boolean on, final RgbColor color)
    {
        final double top = centerY - TOGGLE_HEIGHT / 2.0;
        final double radius = TOGGLE_HEIGHT / 2.0;
        final double thumbX = on ? left + TOGGLE_WIDTH - TOGGLE_THUMB_GAP - TOGGLE_THUMB_RADIUS : left + TOGGLE_THUMB_GAP + TOGGLE_THUMB_RADIUS;
        commands.add (new DisplayCommand.RoundedRectangle (left, top, TOGGLE_WIDTH, TOGGLE_HEIGHT, radius, color));
        if (on)
        {
            commands.add (new DisplayCommand.Circle (thumbX, centerY, TOGGLE_THUMB_RADIUS, BLACK));
            return;
        }
        commands.add (new DisplayCommand.RoundedRectangle (left + TOGGLE_INSET, top + TOGGLE_INSET, TOGGLE_WIDTH - 2 * TOGGLE_INSET, TOGGLE_HEIGHT - 2 * TOGGLE_INSET, radius - TOGGLE_INSET, BLACK));
        commands.add (new DisplayCommand.Circle (thumbX, centerY, TOGGLE_THUMB_RADIUS, color));
        commands.add (new DisplayCommand.Circle (thumbX, centerY, TOGGLE_THUMB_RADIUS - TOGGLE_INSET, BLACK));
    }
}
