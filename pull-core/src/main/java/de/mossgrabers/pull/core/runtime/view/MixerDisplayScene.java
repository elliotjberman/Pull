// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.MixerControlsSnapshot;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.MixerControlDisplay;
import de.mossgrabers.pull.core.api.output.MixerControlsDisplay;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/** One hot-reloadable mixer-control renderer shared by Master and stable-data Mix adapters. */
public final class MixerDisplayScene
{
    private static final double  COLUMN_WIDTH           = MixerControlDisplay.WIDTH;
    private static final double  CONTENT_LEFT           = 8.0;
    private static final double  LABEL_BASELINE         = 34.0;
    private static final double  LABEL_FONT_SIZE        = 15.0;
    private static final double  VALUE_BASELINE         = 55.0;
    private static final double  VALUE_FONT_SIZE        = 19.0;
    private static final double  UNIT_FONT_SIZE         = 8.5;
    private static final double  VALUE_FIELD_WIDTH      = 58.0;
    private static final double  VALUE_UNIT_GAP         = 3.0;
    private static final double  PAN_VALUE_BASELINE     = 64.0;
    private static final double  PAN_VALUE_FONT_SIZE    = 30.0;
    private static final double  PAN_UNIT_FONT_SIZE     = 14.0;
    private static final double  PAN_VALUE_FIELD_WIDTH  = 64.0;
    private static final double  CONTROL_CENTER_Y       = 106.0;
    private static final double  PAN_SLIDER_WIDTH       = 82.0;
    private static final double  PAN_RAIL_HEIGHT        = 4.0;
    private static final double  PAN_MARKER_WIDTH       = 3.0;
    private static final double  PAN_MARKER_HEIGHT      = 16.0;
    private static final double  KNOB_RING_RADIUS       = 25.0;
    private static final double  KNOB_DOT_RADIUS        = 1.1;
    private static final double  KNOB_START             = 220.0;
    private static final double  KNOB_SWEEP             = -260.0;
    private static final int     KNOB_STEPS             = 200;
    private static final double  FADER_TOP              = 60.0;
    private static final double  FADER_HEIGHT           = 80.0;
    private static final double  METER_WIDTH            = 24.0;
    private static final double  METER_GAP              = 4.0;
    private static final double  FADER_RAIL_LEFT        = 69.0;
    private static final double  FADER_LINE_WIDTH       = 2.0;
    private static final double  FADER_MARKER_WIDTH     = 6.0;
    private static final double  METER_ORANGE_START     = 0.75;
    private static final double  METER_RED_START        = 0.90;

    private static final RgbColor WHITE       = new RgbColor (255, 255, 255);
    private static final RgbColor DIM_WHITE   = new RgbColor (102, 102, 102);
    private static final RgbColor DARKER_GRAY = new RgbColor (63, 63, 63);
    private static final RgbColor GREEN       = new RgbColor (0, 255, 0);
    private static final RgbColor ORANGE      = new RgbColor (255, 80, 0);
    private static final RgbColor RED         = new RgbColor (255, 0, 0);

    private static final Pattern VALUE_UNIT_PATTERN = Pattern.compile ("^(.+?)(?:\\s*)(%|dB|kHz|Hz|ms|sec|s|st|ct|BPM|x|L|R)$");
    private static final Pattern PAN_VALUE = Pattern.compile ("[-+]?\\d+(?:[.,]\\d+)?");


    private MixerDisplayScene ()
    {
        // Utility class.
    }


    /** Render structurally contained column-local scenes for stable mixer-data adapters. */
    public static MixerControlsDisplay render (final MixerControlsSnapshot snapshot)
    {
        if (snapshot.controls ().isEmpty ())
            return MixerControlsDisplay.empty ();
        return new MixerControlsDisplay (snapshot.controls ().stream ().map (MixerDisplayScene::renderControl).toList ());
    }


    /** Append one control to a larger core-owned scene. */
    static void append (final List<DisplayCommand> commands, final MixerControlSnapshot control)
    {
        final MixerControlDisplay display = renderControl (control);
        final double offsetX = control.column () * COLUMN_WIDTH;
        display.scene ().commands ().stream ().map (command -> translate (command, offsetX, MixerControlDisplay.TOP)).forEach (commands::add);
    }


    private static MixerControlDisplay renderControl (final MixerControlSnapshot control)
    {
        final ArrayList<DisplayCommand> commands = new ArrayList<> (12);
        appendLocal (commands, control);
        return new MixerControlDisplay (control.column (), control.kind (), new ControllerDisplayScene (MixerControlDisplay.WIDTH, MixerControlDisplay.HEIGHT, commands));
    }


    private static void appendLocal (final List<DisplayCommand> commands, final MixerControlSnapshot control)
    {
        final RgbColor textColor = control.active () ? WHITE : DIM_WHITE;
        final RgbColor accent = control.active () ? control.accentColor () : dimToGray (control.accentColor ());
        final String label = switch (control.kind ())
        {
            case VOLUME -> "Volume";
            case PAN -> "Pan";
            case KNOB -> control.label ();
        };
        drawLabelAt (commands, 0, -MixerControlDisplay.TOP, label, textColor);
        if (control.kind () == MixerControlKind.VOLUME)
        {
            drawValueAt (commands, 0, -MixerControlDisplay.TOP, control.displayedValue (), false, textColor);
            drawVolumeAt (commands, 0, -MixerControlDisplay.TOP, currentValue (control), accent, control.vuLeft (), control.vuRight (), control.active ());
            return;
        }

        if (control.kind () == MixerControlKind.PAN)
        {
            drawValueAt (commands, 0, -MixerControlDisplay.TOP, formatPan (control.displayedValue (), currentValue (control)), true, textColor);
            drawPanAt (commands, 0, -MixerControlDisplay.TOP, currentValue (control), accent, control.active ());
            return;
        }

        drawValueAt (commands, 0, -MixerControlDisplay.TOP, control.displayedValue (), true, textColor);
        drawKnobAt (commands, 0, -MixerControlDisplay.TOP, currentValue (control), accent, control.active ());
    }


    private static void drawLabelAt (final List<DisplayCommand> commands, final double left, final double top, final String text, final RgbColor color)
    {
        commands.add (new DisplayCommand.TextAt (text, left + CONTENT_LEFT, top + LABEL_BASELINE, color, LABEL_FONT_SIZE));
    }


    private static void drawValueAt (final List<DisplayCommand> commands, final double left, final double top, final String text, final boolean large, final RgbColor color)
    {
        if (text == null || text.isBlank ())
            return;
        final Matcher matcher = VALUE_UNIT_PATTERN.matcher (text.trim ());
        if (matcher.matches ())
        {
            commands.add (new DisplayCommand.TextAt (matcher.group (1).trim (), left + CONTENT_LEFT, top + (large ? PAN_VALUE_BASELINE : VALUE_BASELINE), color, large ? PAN_VALUE_FONT_SIZE : VALUE_FONT_SIZE));
            commands.add (new DisplayCommand.TextAt (matcher.group (2), left + CONTENT_LEFT + (large ? PAN_VALUE_FIELD_WIDTH : VALUE_FIELD_WIDTH) + VALUE_UNIT_GAP, top + (large ? PAN_VALUE_BASELINE : VALUE_BASELINE), color, large ? PAN_UNIT_FONT_SIZE : UNIT_FONT_SIZE));
            return;
        }
        commands.add (new DisplayCommand.TextAt (text, left + CONTENT_LEFT, top + (large ? PAN_VALUE_BASELINE : VALUE_BASELINE), color, large ? PAN_VALUE_FONT_SIZE : VALUE_FONT_SIZE));
    }


    private static void drawVolumeAt (final List<DisplayCommand> commands, final double left, final double top, final double value, final RgbColor accent, final double vuLeft, final double vuRight, final boolean active)
    {
        drawMeter (commands, left + CONTENT_LEFT, top, vuLeft, active);
        drawMeter (commands, left + CONTENT_LEFT + METER_WIDTH + METER_GAP, top, vuRight, active);
        final double markerY = top + FADER_TOP + (1 - value) * FADER_HEIGHT;
        final double railX = left + FADER_RAIL_LEFT;
        commands.add (new DisplayCommand.Rectangle (railX - FADER_MARKER_WIDTH, markerY, FADER_MARKER_WIDTH, FADER_LINE_WIDTH, accent));
        commands.add (new DisplayCommand.Rectangle (railX, markerY, FADER_LINE_WIDTH, top + FADER_TOP + FADER_HEIGHT - markerY, accent));
    }


    private static void drawMeter (final List<DisplayCommand> commands, final double left, final double top, final double ratio, final boolean active)
    {
        commands.add (new DisplayCommand.Rectangle (left, top + FADER_TOP, METER_WIDTH, FADER_HEIGHT, active ? DARKER_GRAY : dimToGray (DARKER_GRAY)));
        drawMeterBand (commands, left, top, ratio, 0, METER_ORANGE_START, active ? GREEN : dimToGray (GREEN));
        drawMeterBand (commands, left, top, ratio, METER_ORANGE_START, METER_RED_START, active ? ORANGE : dimToGray (ORANGE));
        drawMeterBand (commands, left, top, ratio, METER_RED_START, 1, active ? RED : dimToGray (RED));
    }


    private static void drawMeterBand (final List<DisplayCommand> commands, final double left, final double top, final double ratio, final double start, final double end, final RgbColor color)
    {
        final double filledEnd = Math.min (ratio, end);
        if (filledEnd <= start)
            return;
        final double height = (filledEnd - start) * FADER_HEIGHT;
        commands.add (new DisplayCommand.Rectangle (left, top + FADER_TOP + FADER_HEIGHT * (1 - filledEnd), METER_WIDTH, height, color));
    }


    private static void drawPanAt (final List<DisplayCommand> commands, final double left, final double top, final double value, final RgbColor accent, final boolean active)
    {
        final double sliderLeft = left + CONTENT_LEFT;
        final double centerX = sliderLeft + PAN_SLIDER_WIDTH / 2.0;
        final double markerX = sliderLeft + PAN_MARKER_WIDTH / 2.0 + value * (PAN_SLIDER_WIDTH - PAN_MARKER_WIDTH);
        final double centerY = top + CONTROL_CENTER_Y;
        final double railTop = centerY - PAN_RAIL_HEIGHT / 2.0;
        final RgbColor background = active ? DARKER_GRAY : dimToGray (DARKER_GRAY);
        commands.add (new DisplayCommand.Rectangle (sliderLeft, railTop, PAN_SLIDER_WIDTH, PAN_RAIL_HEIGHT, background));
        commands.add (new DisplayCommand.Rectangle (Math.min (centerX, markerX), railTop, Math.abs (markerX - centerX), PAN_RAIL_HEIGHT, accent));
        commands.add (new DisplayCommand.Rectangle (centerX - 1, centerY - PAN_MARKER_HEIGHT / 2.0, 2, PAN_MARKER_HEIGHT, background));
        commands.add (new DisplayCommand.Rectangle (markerX - PAN_MARKER_WIDTH / 2.0, centerY - PAN_MARKER_HEIGHT / 2.0, PAN_MARKER_WIDTH, PAN_MARKER_HEIGHT, accent));
    }


    private static void drawKnobAt (final List<DisplayCommand> commands, final double left, final double top, final double value, final RgbColor accent, final boolean active)
    {
        final double centerX = left + CONTENT_LEFT + KNOB_RING_RADIUS;
        final double centerY = top + CONTROL_CENTER_Y;
        final RgbColor background = active ? DARKER_GRAY : dimToGray (DARKER_GRAY);
        commands.add (arc (centerX, centerY, KNOB_SWEEP, background));
        commands.add (arc (centerX, centerY, KNOB_SWEEP * value, accent));
    }


    private static DisplayCommand.DottedArc arc (final double centerX, final double centerY, final double sweep, final RgbColor color)
    {
        final int steps = Math.max (2, (int) Math.ceil (KNOB_STEPS * Math.abs (sweep) / Math.abs (KNOB_SWEEP)));
        return new DisplayCommand.DottedArc (centerX, centerY, KNOB_RING_RADIUS, KNOB_START, sweep, steps, KNOB_DOT_RADIUS, color);
    }


    private static DisplayCommand translate (final DisplayCommand command, final double x, final double y)
    {
        return switch (command)
        {
            case final DisplayCommand.Rectangle rectangle -> new DisplayCommand.Rectangle (rectangle.x () + x, rectangle.y () + y, rectangle.width (), rectangle.height (), rectangle.color ());
            case final DisplayCommand.TextAt text -> new DisplayCommand.TextAt (text.text (), text.x () + x, text.baselineY () + y, text.color (), text.fontSize ());
            case final DisplayCommand.DottedArc arc -> new DisplayCommand.DottedArc (arc.centerX () + x, arc.centerY () + y, arc.radius (), arc.startDegrees (), arc.sweepDegrees (), arc.steps (), arc.dotRadius (), arc.color ());
            default -> throw new IllegalStateException ("Validated mixer scenes contain only mixer-control primitives");
        };
    }


    private static String formatPan (final String displayedValue, final double normalized)
    {
        final String text = displayedValue == null ? "" : displayedValue.trim ();
        if (text.equalsIgnoreCase ("C") || text.equalsIgnoreCase ("Center"))
            return "C";
        final Matcher matcher = PAN_VALUE.matcher (text);
        if (matcher.find ())
        {
            try
            {
                final double amount = Double.parseDouble (matcher.group ().replace (',', '.'));
                if (Math.abs (amount) < 0.005)
                    return "C";
                final String upper = text.toUpperCase ();
                final String direction = upper.contains ("L") ? "L" : upper.contains ("R") ? "R" : amount < 0 ? "L" : "R";
                return Math.round (Math.abs (amount)) + " " + direction;
            }
            catch (final NumberFormatException ignored)
            {
                // Fall through to the normalized read-back.
            }
        }
        final double bipolar = 2 * normalized - 1;
        final long amount = Math.round (100 * Math.abs (bipolar));
        return amount == 0 ? "C" : amount + (bipolar < 0 ? " L" : " R");
    }


    private static double currentValue (final MixerControlSnapshot control)
    {
        return control.modulatedValue () == -1 ? control.value () : control.modulatedValue ();
    }


    private static double left (final int column)
    {
        return column * COLUMN_WIDTH;
    }


    private static RgbColor dimToGray (final RgbColor color)
    {
        final int average = (color.red () + color.green () + color.blue ()) / 3;
        final int dimmed = (int) Math.round (average * 0.4);
        return new RgbColor (dimmed, dimmed, dimmed);
    }
}
