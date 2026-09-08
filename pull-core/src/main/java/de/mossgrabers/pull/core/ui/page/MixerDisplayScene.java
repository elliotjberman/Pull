// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.MixerControlRole;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.MixerControlsSnapshot;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.MixerControlDisplay;
import de.mossgrabers.pull.core.api.output.MixerControlsDisplay;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.ui.component.ParameterValue;
import de.mossgrabers.pull.core.ui.component.RingMeter;
import de.mossgrabers.pull.core.ui.component.VerticalMeter;
import de.mossgrabers.pull.core.ui.component.FaderMarker;
import de.mossgrabers.pull.core.ui.component.BipolarSlider;

import java.util.ArrayList;
import static de.mossgrabers.pull.core.ui.page.MixerControlStyle.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/** One hot-reloadable mixer-control renderer shared by Master and stable-data Mix adapters. */
public final class MixerDisplayScene
{
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
    public static void append (final List<DisplayCommand> commands, final MixerControlSnapshot control)
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
        final boolean active = control.enabled () && (control.role () != MixerControlRole.PROJECT_MACRO || control.touched ());
        final RgbColor baseAccent = control.role () == MixerControlRole.PROJECT_MACRO ? PROJECT_MACRO : control.hostAccentColor ().orElseThrow ();
        final RgbColor textColor = active ? WHITE : DIM_WHITE;
        final RgbColor accent = active ? baseAccent : dimToGray (baseAccent);
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
            drawVolumeAt (commands, 0, -MixerControlDisplay.TOP, currentValue (control), accent, control.vuLeft (), control.vuRight (), active);
            return;
        }

        if (control.kind () == MixerControlKind.PAN)
        {
            drawValueAt (commands, 0, -MixerControlDisplay.TOP, formatPan (control.displayedValue (), currentValue (control)), true, textColor);
            drawPanAt (commands, 0, -MixerControlDisplay.TOP, currentValue (control), accent, active);
            return;
        }

        drawValueAt (commands, 0, -MixerControlDisplay.TOP, control.displayedValue (), true, textColor);
        drawKnobAt (commands, 0, -MixerControlDisplay.TOP, currentValue (control), accent, active);
    }


    private static void drawLabelAt (final List<DisplayCommand> commands, final double left, final double top, final String text, final RgbColor color)
    {
        if (text == null || text.isBlank ())
            return;
        commands.add (new DisplayCommand.TextBox (text.trim (), left + CONTENT_LEFT, top + LABEL_TOP, COLUMN_WIDTH - 2 * CONTENT_LEFT, LABEL_HEIGHT, DisplayTextAlignment.LEFT, color, LABEL_FONT_SIZE, LABEL_MIN_FONT_SIZE, DisplayTextFit.SHRINK_ELLIPSIS));
    }


    private static void drawValueAt (final List<DisplayCommand> commands, final double left, final double top, final String text, final boolean large, final RgbColor color)
    {
        if (text == null || text.isBlank ())
            return;
        final Matcher matcher = VALUE_UNIT_PATTERN.matcher (text.trim ());
        final ParameterValue.Content value = matcher.matches () ? new ParameterValue.Content (normalizePositiveSign (matcher.group (1)), matcher.group (2)) : new ParameterValue.Content (normalizePositiveSign (text), "");
        ParameterValue.append (commands, value, left + CONTENT_LEFT, top + (large ? PAN_VALUE_TOP : VALUE_TOP), color, large ? LARGE_VALUE : VALUE);
    }


    private static String normalizePositiveSign (final String text)
    {
        final String value = text.trim ();
        if (value.length () < 2 || value.charAt (0) != '+')
            return value;
        final char firstValueCharacter = value.charAt (1);
        final String unsigned = value.substring (1);
        if (Character.isDigit (firstValueCharacter) || firstValueCharacter == '.' || firstValueCharacter == ',' || "Inf".equalsIgnoreCase (unsigned) || "Infinity".equalsIgnoreCase (unsigned))
            return value.substring (1);
        return value;
    }


    private static void drawVolumeAt (final List<DisplayCommand> commands, final double left, final double top, final double value, final RgbColor accent, final double vuLeft, final double vuRight, final boolean active)
    {
        drawMeter (commands, left + CONTENT_LEFT, top, vuLeft, active);
        drawMeter (commands, left + CONTENT_LEFT + METER_WIDTH + METER_GAP, top, vuRight, active);
        FaderMarker.append (commands, left + FADER_RAIL_LEFT, top + FADER_TOP, value, accent, VOLUME_FADER);
    }


    private static void drawMeter (final List<DisplayCommand> commands, final double left, final double top, final double ratio, final boolean active)
    {
        final RgbColor background = active ? DARKER_GRAY : dimToGray (DARKER_GRAY);
        final List<VerticalMeter.Band> bands = List.of (
            new VerticalMeter.Band (0, METER_ORANGE_START, active ? GREEN : dimToGray (GREEN)),
            new VerticalMeter.Band (METER_ORANGE_START, METER_RED_START, active ? ORANGE : dimToGray (ORANGE)),
            new VerticalMeter.Band (METER_RED_START, 1, active ? RED : dimToGray (RED)));
        VerticalMeter.append (commands, left, top + FADER_TOP, ratio, background, bands, LEVEL_METER);
    }


    private static void drawPanAt (final List<DisplayCommand> commands, final double left, final double top, final double value, final RgbColor accent, final boolean active)
    {
        final RgbColor background = active ? DARKER_GRAY : dimToGray (DARKER_GRAY);
        BipolarSlider.append (commands, left + CONTENT_LEFT, top + CONTROL_CENTER_Y, value, accent, background, PAN_SLIDER);
    }


    private static void drawKnobAt (final List<DisplayCommand> commands, final double left, final double top, final double value, final RgbColor accent, final boolean active)
    {
        final double centerX = left + CONTENT_LEFT + KNOB_RING.radius ();
        final double centerY = top + CONTROL_CENTER_Y;
        final RgbColor background = active ? DARKER_GRAY : dimToGray (DARKER_GRAY);
        RingMeter.append (commands, centerX, centerY, value, background, accent, KNOB_RING);
    }


    private static DisplayCommand translate (final DisplayCommand command, final double x, final double y)
    {
        return switch (command)
        {
            case final DisplayCommand.Rectangle rectangle -> new DisplayCommand.Rectangle (rectangle.x () + x, rectangle.y () + y, rectangle.width (), rectangle.height (), rectangle.color ());
            case final DisplayCommand.TextAt text -> new DisplayCommand.TextAt (text.text (), text.x () + x, text.baselineY () + y, text.color (), text.fontSize ());
            case final DisplayCommand.TextBox text -> new DisplayCommand.TextBox (text.text (), text.x () + x, text.y () + y, text.width (), text.height (), text.alignment (), text.color (), text.maximumFontSize (), text.minimumFontSize (), text.fit ());
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


    private static RgbColor dimToGray (final RgbColor color)
    {
        final int average = (color.red () + color.green () + color.blue ()) / 3;
        final int dimmed = (int) Math.round (average * 0.4);
        return new RgbColor (dimmed, dimmed, dimmed);
    }
}
