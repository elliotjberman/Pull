// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import static de.mossgrabers.pull.core.ui.page.MacroPageStyle.*;
import static de.mossgrabers.pull.core.ui.page.PageStyle.BLACK;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/** Core-owned parameter region for the eight VS Live project macros. */
public final class MacroPageRenderer
{
    static final int WIDTH = 960;
    static final int HEIGHT = 143;

    private static final Pattern VALUE_UNIT_PATTERN = Pattern.compile ("^(.+?)(?:\\s*)(%|dB|kHz|Hz|ms|sec|s|st|ct|BPM|x)$");


    private MacroPageRenderer ()
    {
        // Utility class.
    }


    /** Render normalized macro presentation values into the parameter region. */
    public static ControllerDisplayScene render (final MacroPagePresentation page)
    {
        final ArrayList<DisplayCommand> commands = new ArrayList<> (64);
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK));
        for (final MacroPagePresentation.Control control: page.controls ())
            append (commands, control);
        return new ControllerDisplayScene (WIDTH, HEIGHT, commands);
    }

    private static void append (final ArrayList<DisplayCommand> commands, final MacroPagePresentation.Control control)
    {
        final double left = control.column () * PageStyle.COLUMN_WIDTH;
        final RgbColor meterOff = brightness (METER_OFF, control.touched ());
        final RgbColor meterOn = brightness (METER_ON, control.touched ());
        final RgbColor meterText = brightness (METER_TEXT, control.touched ());
        commands.add (new DisplayCommand.TextAt (control.label (), left + CONTENT_LEFT, LABEL_BASELINE, meterText, LABEL_FONT));
        drawValue (commands, left, control.displayedValue (), meterText);
        if (control.widget () != MacroPagePresentation.Widget.RING)
        {
            ToggleRenderer.append (commands, left + CONTENT_LEFT, RING_CENTER_Y, control.widget () == MacroPagePresentation.Widget.TOGGLE_ON, meterOn);
            return;
        }
        commands.add (arc (left, RING_SWEEP, meterOff));
        commands.add (arc (left, RING_SWEEP * control.value (), meterOn));
    }


    private static void drawValue (final ArrayList<DisplayCommand> commands, final double left, final String displayedValue, final RgbColor color)
    {
        if (displayedValue == null || displayedValue.isBlank ())
            return;
        final Matcher matcher = VALUE_UNIT_PATTERN.matcher (displayedValue.trim ());
        if (matcher.matches ())
        {
            commands.add (new DisplayCommand.TextAt (matcher.group (1).trim (), left + CONTENT_LEFT, VALUE_BASELINE, color, VALUE_FONT));
            commands.add (new DisplayCommand.TextAt (matcher.group (2), left + CONTENT_LEFT + VALUE_FIELD_WIDTH + VALUE_UNIT_GAP, VALUE_BASELINE, color, UNIT_FONT_SIZE));
            return;
        }
        commands.add (new DisplayCommand.TextAt (displayedValue, left + CONTENT_LEFT, VALUE_BASELINE, color, VALUE_FONT));
    }



    private static DisplayCommand.DottedArc arc (final double columnLeft, final double sweep, final RgbColor color)
    {
        final int steps = Math.max (2, (int) Math.ceil (RING_STEPS * Math.abs (sweep) / Math.abs (RING_SWEEP)));
        return new DisplayCommand.DottedArc (columnLeft + CONTENT_LEFT + RING_RADIUS, RING_CENTER_Y, RING_RADIUS, RING_START, sweep, steps, RING_DOT_RADIUS, color);
    }


}
