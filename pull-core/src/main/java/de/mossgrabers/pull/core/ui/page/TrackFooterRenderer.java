// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayIcon;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import static de.mossgrabers.pull.core.ui.page.PageStyle.*;
import static de.mossgrabers.pull.core.ui.page.TrackFooterStyle.*;
import static de.mossgrabers.pull.core.ui.page.MixerPageStyle.dim;


/** Pure footer drawing and lights shared by current-bank and Session selection views. */
public final class TrackFooterRenderer
{
    private TrackFooterRenderer ()
    {
        // Utility class.
    }


    /** Render one bounded footer with lights derived from the same observed cell state. */
    public static PageVisuals render (final TrackFooterPresentation page)
    {
        final ArrayList<DisplayCommand> commands = new ArrayList<> (24);
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, TrackFooterStyle.HEIGHT, BLACK));
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        for (int column = 0; column < COLUMNS; column++) lights.put (button (column), BLACK);
        for (final TrackFooterPresentation.Cell cell: page.cells ())
        {
            lights.put (button (cell.column ()), !cell.active () ? BLACK : cell.armed () ? ARMED : cell.color ());
            append (commands, cell.column (), 0, cell.name (), cell.icon (), cell.color (), cell.selected (), cell.active ());
        }
        return new PageVisuals (lights, new ControllerDisplayScene (WIDTH, (int) TrackFooterStyle.HEIGHT, commands));
    }

    private static ControlId button (final int column) { return PushControlIds.button ("ROW1_" + (column + 1)); }


    /** Append one explicitly described footer cell. */
    public static void append (final List<DisplayCommand> commands, final int column, final double top, final String text, final DisplayIcon icon, final RgbColor color, final boolean selected, final boolean active)
    {
        if (text == null || text.isEmpty ())
            return;
        final double left = column * PageStyle.COLUMN_WIDTH;
        final RgbColor footerColor = active ? color : dim (color);
        final RgbColor contentColor = selected ? contrast (footerColor) : footerColor;
        commands.add (new DisplayCommand.Rectangle (left, top, PageStyle.COLUMN_WIDTH - 2, TrackFooterStyle.HEIGHT, selected ? footerColor : BLACK));
        double textLeft = left + INSET;
        if (icon != null)
        {
            final double iconWidth = iconWidth (icon);
            commands.add (new DisplayCommand.Icon (icon, textLeft, top, iconWidth, TrackFooterStyle.HEIGHT, contentColor));
            textLeft += iconWidth + INSET;
        }
        commands.add (new DisplayCommand.TextBox (text, textLeft, top, left + PageStyle.COLUMN_WIDTH - textLeft - INSET, TrackFooterStyle.HEIGHT, DisplayTextAlignment.LEFT, contentColor, FONT_SIZE, FONT_SIZE, DisplayTextFit.CLIP));
    }


    private static double iconWidth (final DisplayIcon icon)
    {
        return icon == DisplayIcon.GROUP_TRACK_OPEN ? 16.0 : 15.0;
    }
}
