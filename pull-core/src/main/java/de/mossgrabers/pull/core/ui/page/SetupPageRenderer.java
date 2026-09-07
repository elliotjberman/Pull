// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.ui.component.ParameterValue;
import de.mossgrabers.pull.core.ui.component.ResponseCurve;
import de.mossgrabers.pull.core.ui.component.RingMeter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static de.mossgrabers.pull.core.ui.PageStyle.*;

/** Pure Setup output from observed settings; touch changes brightness, never the displayed value. */
public final class SetupPageRenderer
{
    private SetupPageRenderer () { }

    public static PageVisuals render (final SetupPagePresentation state)
    {
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        final List<DisplayCommand> commands = new ArrayList<> ();
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK));
        ConfigurationTabs.append (commands, lights, true);
        if (state.available ())
        {
            label (commands, 0, "Brightness", WHITE);
            parameter (commands, state, 1, "Display", state.displayBrightness (), 100, "%");
            parameter (commands, state, 2, "LEDs", state.ledBrightness (), 100, "%");
            label (commands, 3, "Pads", WHITE);
            parameter (commands, state, 4, "Sensitivity", state.sensitivity (), 10, "");
            parameter (commands, state, 5, "Gain", state.gain (), 10, "");
            parameter (commands, state, 6, "Dynamics", state.dynamics (), 10, "");
            commands.add (new DisplayCommand.Rectangle (SetupPageStyle.CURVE_LEFT, SetupPageStyle.CURVE_TOP, SetupPageStyle.CURVE.width (), SetupPageStyle.CURVE.height (), DARK));
            ResponseCurve.append (commands, state.velocityCurve ().stream ().map (value -> value / 128.0).toList (), SetupPageStyle.CURVE_LEFT, SetupPageStyle.CURVE_TOP, WHITE, SetupPageStyle.CURVE);
        }
        else
            commands.add (new DisplayCommand.TextBox ("Waiting for Push settings", 12, 70, WIDTH - 24, 42,
                DisplayTextAlignment.LEFT, WHITE, 20, 12, DisplayTextFit.SHRINK_ELLIPSIS));
        return new PageVisuals (lights, new ControllerDisplayScene (WIDTH, HEIGHT, commands));
    }

    private static void parameter (final List<DisplayCommand> commands, final SetupPagePresentation state, final int column, final String name, final int value, final int maximum, final String unit)
    {
        final boolean touched = state.touched ().contains (column);
        final double left = column * COLUMN_WIDTH + MacroPageStyle.CONTENT_LEFT;
        final RgbColor text = MacroPageStyle.brightness (MacroPageStyle.METER_TEXT, touched);
        label (commands, column, name, text);
        ParameterValue.append (commands, new ParameterValue.Content (Integer.toString (value), unit), left, SetupPageStyle.VALUE_TOP, text, SetupPageStyle.VALUE);
        RingMeter.append (commands, left + MacroPageStyle.RING.radius (), SetupPageStyle.RING_CENTER_Y, value / (double) maximum,
            MacroPageStyle.brightness (MacroPageStyle.METER_OFF, touched), MacroPageStyle.brightness (MacroPageStyle.METER_ON, touched), MacroPageStyle.RING);
    }

    private static void label (final List<DisplayCommand> commands, final int column, final String text, final RgbColor color)
    {
        commands.add (new DisplayCommand.TextBox (text, column * COLUMN_WIDTH + MacroPageStyle.CONTENT_LEFT, SetupPageStyle.LABEL_TOP, MacroPageStyle.CONTENT_WIDTH,
            SetupPageStyle.LABEL_HEIGHT, DisplayTextAlignment.LEFT, color, 14, 10, DisplayTextFit.SHRINK_ELLIPSIS));
    }
}
