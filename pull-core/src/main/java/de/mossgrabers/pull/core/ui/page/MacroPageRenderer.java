// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.ui.PageStyle;

import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.ui.component.ParameterValue;
import de.mossgrabers.pull.core.ui.component.RingMeter;
import de.mossgrabers.pull.core.ui.component.Toggle;

import java.util.ArrayList;
import static de.mossgrabers.pull.core.ui.page.MacroPageStyle.*;
import static de.mossgrabers.pull.core.ui.PageStyle.BLACK;
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
        commands.add (new DisplayCommand.TextBox (control.label (), left + CONTENT_LEFT, LABEL_TOP, CONTENT_WIDTH, LABEL_HEIGHT, DisplayTextAlignment.LEFT, meterText, LABEL_FONT, LABEL_MIN_FONT, DisplayTextFit.SHRINK_ELLIPSIS));
        drawValue (commands, left, control.displayedValue (), meterText);
        if (control.widget () != MacroPagePresentation.Widget.RING)
        {
            Toggle.append (commands, left + CONTENT_LEFT, RING_CENTER_Y, control.widget () == MacroPagePresentation.Widget.TOGGLE_ON, meterOn);
            return;
        }
        RingMeter.append (commands, left + CONTENT_LEFT + RING.radius (), RING_CENTER_Y, control.value (), meterOff, meterOn, RING);
    }


    private static void drawValue (final ArrayList<DisplayCommand> commands, final double left, final String displayedValue, final RgbColor color)
    {
        if (displayedValue == null || displayedValue.isBlank ())
            return;
        final Matcher matcher = VALUE_UNIT_PATTERN.matcher (displayedValue.trim ());
        final ParameterValue.Content value = matcher.matches () ? new ParameterValue.Content (matcher.group (1).trim (), matcher.group (2)) : new ParameterValue.Content (displayedValue, "");
        ParameterValue.append (commands, value, left + CONTENT_LEFT, VALUE_TOP, color, VALUE);
    }


}
