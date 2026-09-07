// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.ui.page.MixerDisplayScene;

import de.mossgrabers.pull.core.api.output.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import static de.mossgrabers.pull.core.ui.PageStyle.*;
import static de.mossgrabers.pull.core.ui.page.MixerPageStyle.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Preserved normal global mixer graphics, wholly inside the parameter region above the shared footer. */
public final class GlobalMixerPageRenderer
{
    private static final Pattern VALUE_UNIT = Pattern.compile ("^(.+?)(?:\\s*)(%|dB|kHz|Hz|ms|sec|s|st|ct|BPM|x|L|R)$");

    private GlobalMixerPageRenderer () { }

    public static PageVisuals render (final GlobalMixerPagePresentation page)
    {
        final ArrayList<DisplayCommand> commands = new ArrayList<> (100);
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, PARAMETER_HEIGHT, BLACK));
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        for (int index = 0; index < COLUMNS; index++)
        {
            final GlobalMixerPagePresentation.MenuItem item = page.menu ().get (index);
            lights.put (PushControlIds.button ("ROW2_" + (index + 1)), item.selected () || item.arrow () ? WHITE : BLACK);
            if (item.text ().isBlank ()) continue;
            if (item.selected ()) commands.add (new DisplayCommand.Rectangle (index * COLUMN, 0, COLUMN - 2, MENU_HEIGHT - 1, WHITE));
            commands.add (new DisplayCommand.TextBox (item.text (), index * COLUMN + 8, 0, COLUMN - 16, MENU_HEIGHT, DisplayTextAlignment.LEFT, item.selected () ? BLACK : WHITE, 12, 12, DisplayTextFit.CLIP));
        }
        for (final GlobalMixerPagePresentation.Control control: page.controls ())
        {
            final double left = control.column () * COLUMN;
            final RgbColor accent = control.active () ? control.color () : dim (control.color ());
            final RgbColor text = control.active () ? WHITE : DIM_WHITE;
            final RgbColor background = control.active () ? DARK : DIM_DARK;
            if (control.widget () == GlobalMixerPagePresentation.Widget.VOLUME)
            {
                value (commands, left, MENU_HEIGHT + 21, control.displayedValue (), 18, 66, text);
                volume (commands, left, control.value (), control.vuLeft (), control.vuRight (), accent, background);
            }
            else
            {
                value (commands, left, 55, control.displayedValue (), 19, 69, text);
                switch (control.widget ())
                {
                    case SEND_VOLUME -> sendVolume (commands, left, control.value (), accent, background);
                    case PAN -> pan (commands, left, control.value (), accent, background);
                    case RING -> ring (commands, left, control.value (), accent, background);
                    default -> throw new IllegalStateException ("Volume rendered above");
                }
            }
        }
        return new PageVisuals (lights, new ControllerDisplayScene (WIDTH, PARAMETER_HEIGHT, commands));
    }

    private static void sendVolume (final List<DisplayCommand> commands, final double left, final double ratio, final RgbColor accent, final RgbColor background)
    {
        // Global send pages supplied zero VU to the legacy shared name-selected Volume widget.
        commands.add (new DisplayCommand.Rectangle (left + 8, 60, 24, 70, background));
        commands.add (new DisplayCommand.Rectangle (left + 36, 60, 24, 70, background));
        final double marker = 60 + (1 - ratio) * 70;
        commands.add (new DisplayCommand.Rectangle (left + 68, marker, 13, 1, accent));
        commands.add (new DisplayCommand.Rectangle (left + 80, marker, 1, 130 - marker, accent));
    }

    private static void ring (final List<DisplayCommand> commands, final double left, final double ratio, final RgbColor accent, final RgbColor background)
    {
        commands.add (new DisplayCommand.DottedArc (left + 31, 106, 23, 220, -260, 200, 1.1, background));
        commands.add (new DisplayCommand.DottedArc (left + 31, 106, 23, 220, -260 * ratio, Math.max (2, (int) Math.ceil (200 * ratio)), 1.1, accent));
    }

    private static void volume (final List<DisplayCommand> commands, final double left, final double ratio, final double vuLeft, final double vuRight, final RgbColor accent, final RgbColor background)
    {
        final double top = MENU_HEIGHT + 28;
        final double height = 160 - 2 * MENU_HEIGHT - top - 5;
        meter (commands, left + 10, top, height, vuLeft, accent, background);
        meter (commands, left + 31, top, height, vuRight, accent, background);
        final double marker = top + (1 - ratio) * height;
        commands.add (new DisplayCommand.Rectangle (left + 63, marker, 13, 1, accent));
        commands.add (new DisplayCommand.Rectangle (left + 75, marker, 1, top + height - marker, accent));
    }

    private static void meter (final List<DisplayCommand> commands, final double left, final double top, final double height, final double value, final RgbColor accent, final RgbColor background)
    {
        commands.add (new DisplayCommand.Rectangle (left, top, 18, height, background));
        final double amount = ratio (value) * height;
        commands.add (new DisplayCommand.Rectangle (left, top + height - amount, 18, amount, accent));
    }

    private static void pan (final List<DisplayCommand> commands, final double left, final double ratio, final RgbColor accent, final RgbColor background)
    {
        final double start = left + 8;
        final double center = start + 41;
        final double marker = start + 2.5 + ratio * 77;
        commands.add (new DisplayCommand.RoundedRectangle (start, 104, 82, 4, 2, background));
        commands.add (new DisplayCommand.Rectangle (Math.min (center, marker), 104, Math.abs (marker - center), 4, accent));
        commands.add (new DisplayCommand.Rectangle (center - 1, 98, 2, 16, background));
        commands.add (new DisplayCommand.RoundedRectangle (marker - 2.5, 98, 5, 16, 2.5, accent));
    }

    private static void value (final List<DisplayCommand> commands, final double left, final double baseline, final String text, final double fontSize, final double unitLeft, final RgbColor color)
    {
        if (text.isBlank ()) return;
        final Matcher matcher = VALUE_UNIT.matcher (text.trim ());
        if (matcher.matches ())
        {
            commands.add (new DisplayCommand.TextAt (matcher.group (1).trim (), left + 8, baseline, color, fontSize));
            commands.add (new DisplayCommand.TextAt (matcher.group (2), left + unitLeft, baseline, color, 8.5));
        }
        else commands.add (new DisplayCommand.TextAt (text, left + 8, baseline, color, fontSize));
    }

    private static double ratio (final double value) { return Math.max (0, Math.min (1, value)); }
}
