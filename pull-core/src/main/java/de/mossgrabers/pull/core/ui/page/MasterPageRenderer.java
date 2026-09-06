// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.ui.page;



import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayIcon;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import static de.mossgrabers.pull.core.ui.page.MasterPageStyle.*;
import java.util.Map;
import java.util.LinkedHashMap;


/** Reloadable composition of the complete Push 2 Master display. */
public final class MasterPageRenderer
{
    private MasterPageRenderer ()
    {
        // Utility class.
    }


    /** Compose one complete scene and its row lights from page values. */
    public static PageVisuals render (final MasterPagePresentation page)
    {
        final ArrayList<DisplayCommand> commands = new ArrayList<> (64);
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK));
        appendControl (commands, page, 0);
        final MasterPagePresentation.TrackFooter track = page.track ();
        drawFooter (commands, 0, track.name (), track.icon (), track.color (), track.selected (), track.active ());
        appendControl (commands, page, 1);
        appendControl (commands, page, 2);
        drawFooter (commands, 2, "Cue", null, FOOTER_GRAY, false, true);
        appendControl (commands, page, 3);
        drawLabel (commands, 4, "Audio Engine");
        drawToggle (commands, 4, page.engineActive ());
        drawLabel (commands, 5, "Project");
        drawStatusValue (commands, 5, page.projectName ());
        drawHeader (commands, 6, "Previous", page.canPrevious ());
        drawFooter (commands, 6, "Load", null, WHITE, false, true);
        drawHeader (commands, 7, "Next", page.canNext ());
        drawFooter (commands, 7, "Save", null, WHITE, false, true);
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        for (int row = 1; row <= 2; row++)
            for (int column = 1; column <= 8; column++) lights.put (row (row, column), BLACK);
        lights.put (row (2, 5), page.engineActive () ? MixerPageStyle.GREEN : WHITE);
        lights.put (row (2, 7), page.canPrevious () ? WHITE : FOOTER_GRAY);
        lights.put (row (2, 8), page.canNext () ? WHITE : FOOTER_GRAY);
        lights.put (row (1, 7), WHITE);
        lights.put (row (1, 8), page.projectDirty () ? MixerPageStyle.ORANGE : WHITE);
        return new PageVisuals (lights, new ControllerDisplayScene (WIDTH, HEIGHT, commands));
    }

    private static void appendControl (final ArrayList<DisplayCommand> commands, final MasterPagePresentation page, final int column)
    {
        page.controls ().stream ().filter (control -> control.column () == column).findFirst ().ifPresent (control -> MixerDisplayScene.append (commands, control));
    }

    private static ControlId row (final int row, final int column) { return PushControlIds.button ("ROW" + row + "_" + column); }


    private static void drawHeader (final ArrayList<DisplayCommand> commands, final int column, final String text, final boolean active)
    {
        commands.add (new DisplayCommand.TextBox (text, left (column) + CONTENT_LEFT, 0, COLUMN_WIDTH - 2 * CONTENT_LEFT, MENU_HEIGHT, DisplayTextAlignment.LEFT, active ? WHITE : DIM_WHITE, 12, 12, DisplayTextFit.CLIP));
    }


    private static void drawLabel (final ArrayList<DisplayCommand> commands, final int column, final String text)
    {
        commands.add (new DisplayCommand.TextAt (text, left (column) + CONTENT_LEFT, LABEL_BASELINE, WHITE, LABEL_FONT_SIZE));
    }


    private static void drawStatusValue (final ArrayList<DisplayCommand> commands, final int column, final String text)
    {
        if (text == null || text.isBlank ())
            return;
        commands.add (new DisplayCommand.TextBox (text, left (column) + CONTENT_LEFT, STATUS_VALUE_TOP, COLUMN_WIDTH - 2 * CONTENT_LEFT, STATUS_VALUE_HEIGHT, DisplayTextAlignment.LEFT, WHITE, STATUS_MAX_FONT_SIZE, STATUS_MIN_FONT_SIZE, DisplayTextFit.SHRINK_ELLIPSIS));
    }


    private static void drawToggle (final ArrayList<DisplayCommand> commands, final int column, final boolean on)
    {
        final double left = left (column) + CONTENT_LEFT;
        final double top = RING_CENTER_Y - TOGGLE_HEIGHT / 2.0;
        final double radius = TOGGLE_HEIGHT / 2.0;
        final RgbColor color = on ? TOGGLE_ON : GRAY;
        final double thumbX = on ? left + TOGGLE_WIDTH - TOGGLE_THUMB_GAP - TOGGLE_THUMB_RADIUS : left + TOGGLE_THUMB_GAP + TOGGLE_THUMB_RADIUS;
        commands.add (new DisplayCommand.RoundedRectangle (left, top, TOGGLE_WIDTH, TOGGLE_HEIGHT, radius, color));
        if (on)
        {
            commands.add (new DisplayCommand.Circle (thumbX, RING_CENTER_Y, TOGGLE_THUMB_RADIUS, BLACK));
            return;
        }
        commands.add (new DisplayCommand.RoundedRectangle (left + TOGGLE_INSET, top + TOGGLE_INSET, TOGGLE_WIDTH - 2 * TOGGLE_INSET, TOGGLE_HEIGHT - 2 * TOGGLE_INSET, radius - TOGGLE_INSET, BLACK));
        commands.add (new DisplayCommand.Circle (thumbX, RING_CENTER_Y, TOGGLE_THUMB_RADIUS, color));
        commands.add (new DisplayCommand.Circle (thumbX, RING_CENTER_Y, TOGGLE_THUMB_RADIUS - TOGGLE_INSET, BLACK));
    }


    private static void drawFooter (final ArrayList<DisplayCommand> commands, final int column, final String text, final DisplayIcon icon, final RgbColor color, final boolean selected, final boolean active)
    {
        TrackFooterRenderer.append (commands, column, FOOTER_TOP, text, icon, color, selected, active);
    }


    private static double left (final int column)
    {
        return column * COLUMN_WIDTH;
    }


}
