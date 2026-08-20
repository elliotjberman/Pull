// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/** Core-owned parameter region for the eight VS Live project macros. */
final class ProjectMacroDisplayScene
{
    static final int WIDTH = 960;
    static final int HEIGHT = 143;

    private static final double PARAMETER_UPPER_BOUND = 1024.0;
    private static final double COLUMN_WIDTH = WIDTH / (double) ParameterSlot.BANK_SIZE;
    private static final double CONTENT_LEFT = 8.0;
    private static final double LABEL_BASELINE = 31.0;
    private static final double LABEL_FONT_SIZE = 12.5;
    private static final double VALUE_BASELINE = 64.0;
    private static final double VALUE_FONT_SIZE = 30.0;
    private static final double UNIT_FONT_SIZE = 14.0;
    private static final double VALUE_FIELD_WIDTH = 64.0;
    private static final double VALUE_UNIT_GAP = 2.0;
    private static final double RING_CENTER_Y = 105.0;
    private static final double RING_RADIUS = 25.0;
    private static final double RING_DOT_RADIUS = 1.1;
    private static final double RING_START = 220.0;
    private static final double RING_SWEEP = -260.0;
    private static final int RING_STEPS = 220;
    private static final double TOGGLE_WIDTH = 66.0;
    private static final double TOGGLE_HEIGHT = 32.0;
    private static final double TOGGLE_INSET = 1.4;
    private static final double TOGGLE_THUMB_GAP = 5.0;
    private static final double TOGGLE_THUMB_RADIUS = 10.0;

    private static final Pattern VALUE_UNIT_PATTERN = Pattern.compile ("^(.+?)(?:\\s*)(%|dB|kHz|Hz|ms|sec|s|st|ct|BPM|x)$");
    private static final RgbColor BLACK = new RgbColor (0, 0, 0);
    private static final RgbColor METER_OFF = new RgbColor (20, 54, 65);
    private static final RgbColor METER_ON = new RgbColor (132, 214, 255);
    private static final RgbColor METER_TEXT = new RgbColor (190, 235, 247);


    private ProjectMacroDisplayScene ()
    {
        // Utility class.
    }


    /** Render all available project-remote targets into the fixed parameter region. */
    static ControllerDisplayScene render (final Map<ParameterSlot, ParameterTargetSnapshot> parameters, final Set<ControlId> touchedControls)
    {
        final ArrayList<DisplayCommand> commands = new ArrayList<> (64);
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK));
        for (int index = 0; index < ParameterSlot.BANK_SIZE; index++)
        {
            final ParameterTargetSnapshot target = parameters.get (ParameterSlot.projectRemote (index));
            if (target == null || target.name ().isBlank ())
                continue;
            append (commands, index, target, touchedControls.contains (PushControlIds.continuous ("KNOB" + (index + 1))));
        }
        return new ControllerDisplayScene (WIDTH, HEIGHT, commands);
    }


    private static void append (final ArrayList<DisplayCommand> commands, final int column, final ParameterTargetSnapshot target, final boolean touched)
    {
        final double left = column * COLUMN_WIDTH;
        final double intensity = touched ? 1.0 : 0.5;
        final RgbColor meterOff = dim (METER_OFF, intensity);
        final RgbColor meterOn = dim (METER_ON, intensity);
        final RgbColor meterText = dim (METER_TEXT, intensity);
        commands.add (new DisplayCommand.TextAt (target.name (), left + CONTENT_LEFT, LABEL_BASELINE, meterText, LABEL_FONT_SIZE));
        drawValue (commands, left, target.displayedValue (), meterText);
        if (isButtonValue (target.displayedValue ()))
        {
            drawToggle (commands, left, isButtonOn (target.displayedValue ()), meterOn);
            return;
        }

        final double value = target.modulatedValue () == -1 ? target.value () : target.modulatedValue ();
        commands.add (arc (left, RING_SWEEP, meterOff));
        commands.add (arc (left, RING_SWEEP * normalize (value), meterOn));
    }


    private static void drawValue (final ArrayList<DisplayCommand> commands, final double left, final String displayedValue, final RgbColor color)
    {
        if (displayedValue == null || displayedValue.isBlank ())
            return;
        final Matcher matcher = VALUE_UNIT_PATTERN.matcher (displayedValue.trim ());
        if (matcher.matches ())
        {
            commands.add (new DisplayCommand.TextAt (matcher.group (1).trim (), left + CONTENT_LEFT, VALUE_BASELINE, color, VALUE_FONT_SIZE));
            commands.add (new DisplayCommand.TextAt (matcher.group (2), left + CONTENT_LEFT + VALUE_FIELD_WIDTH + VALUE_UNIT_GAP, VALUE_BASELINE, color, UNIT_FONT_SIZE));
            return;
        }
        commands.add (new DisplayCommand.TextAt (displayedValue, left + CONTENT_LEFT, VALUE_BASELINE, color, VALUE_FONT_SIZE));
    }


    private static void drawToggle (final ArrayList<DisplayCommand> commands, final double columnLeft, final boolean on, final RgbColor color)
    {
        final double left = columnLeft + CONTENT_LEFT;
        final double top = RING_CENTER_Y - TOGGLE_HEIGHT / 2.0;
        final double radius = TOGGLE_HEIGHT / 2.0;
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


    private static DisplayCommand.DottedArc arc (final double columnLeft, final double sweep, final RgbColor color)
    {
        final int steps = Math.max (2, (int) Math.ceil (RING_STEPS * Math.abs (sweep) / Math.abs (RING_SWEEP)));
        return new DisplayCommand.DottedArc (columnLeft + CONTENT_LEFT + RING_RADIUS, RING_CENTER_Y, RING_RADIUS, RING_START, sweep, steps, RING_DOT_RADIUS, color);
    }


    private static boolean isButtonValue (final String text)
    {
        return text != null && ("On".equalsIgnoreCase (text.trim ()) || "Off".equalsIgnoreCase (text.trim ()));
    }


    private static boolean isButtonOn (final String text)
    {
        return text != null && "On".equalsIgnoreCase (text.trim ());
    }


    private static double normalize (final double value)
    {
        return Math.max (0, Math.min (1, value / PARAMETER_UPPER_BOUND));
    }


    private static RgbColor dim (final RgbColor color, final double intensity)
    {
        return new RgbColor (
            (int) Math.round (color.red () * intensity),
            (int) Math.round (color.green () * intensity),
            (int) Math.round (color.blue () * intensity));
    }
}
