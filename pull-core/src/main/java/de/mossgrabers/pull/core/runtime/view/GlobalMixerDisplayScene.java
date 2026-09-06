// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CurrentTrackSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.output.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Preserved normal global mixer graphics, wholly inside the parameter region above the shared footer. */
final class GlobalMixerDisplayScene
{
    private static final double COLUMN = 120;
    private static final double MENU_HEIGHT = 160.0 / 12 + 4;
    private static final RgbColor BLACK = new RgbColor (0, 0, 0);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);
    private static final RgbColor DIM_WHITE = new RgbColor (102, 102, 102);
    private static final RgbColor DARK = new RgbColor (63, 63, 63);
    private static final RgbColor DIM_DARK = new RgbColor (25, 25, 25);
    private static final Pattern VALUE_UNIT = Pattern.compile ("^(.+?)(?:\\s*)(%|dB|kHz|Hz|ms|sec|s|st|ct|BPM|x|L|R)$");

    private GlobalMixerDisplayScene () { }

    static ControllerDisplayScene render (final ControllerSnapshot snapshot, final GlobalMixerControlsView.Role role, final int sendIndex, final List<GlobalMixerMenu.Entry> menu)
    {
        final ArrayList<DisplayCommand> commands = new ArrayList<> (100);
        commands.add (new DisplayCommand.Rectangle (0, 0, 960, 143, BLACK));
        for (int index = 0; index < 8; index++)
        {
            final GlobalMixerMenu.Entry item = menu.get (index);
            if (item.text ().isBlank ()) continue;
            if (item.selected ()) commands.add (new DisplayCommand.Rectangle (index * COLUMN, 0, COLUMN - 2, MENU_HEIGHT - 1, WHITE));
            commands.add (new DisplayCommand.TextBox (item.text (), index * COLUMN + 8, 0, COLUMN - 16, MENU_HEIGHT, DisplayTextAlignment.LEFT, item.selected () ? BLACK : WHITE, 12, 12, DisplayTextFit.CLIP));
        }
        if (!snapshot.bridge ().encoderConfiguration ().available ()) return new ControllerDisplayScene (960, 143, commands);
        final double upper = snapshot.bridge ().encoderConfiguration ().valueUpperBound ();
        final List<CurrentTrackSnapshot> tracks = snapshot.bridge ().currentTrackBank ().tracks ();
        for (int index = 0; index < tracks.size (); index++)
        {
            final CurrentTrackSnapshot track = tracks.get (index);
            final ParameterTargetSnapshot parameter = GlobalMixerControlsView.alignedTarget (snapshot, role, sendIndex, index);
            if (!track.track ().exists () || parameter == null) continue;
            if (role == GlobalMixerControlsView.Role.SEND && parameter.name ().isBlank ()) continue;
            final boolean active = track.track ().activated () && (role != GlobalMixerControlsView.Role.SEND || parameter.enabled ().orElse (Boolean.FALSE).booleanValue ());
            final RgbColor accent = active ? track.track ().color () : dim (track.track ().color ());
            final RgbColor text = active ? WHITE : DIM_WHITE;
            final RgbColor background = active ? DARK : DIM_DARK;
            final double ratio = ratio ((parameter.modulatedValue () == -1 ? parameter.value () : parameter.modulatedValue ()) / upper);
            if (role == GlobalMixerControlsView.Role.VOLUME)
            {
                value (commands, index * COLUMN, MENU_HEIGHT + 21, parameter.displayedValue (), 18, 66, text);
                volume (commands, index * COLUMN, ratio, snapshot.bridge ().controllerSettings ().vuMetersEnabled () ? track.vuLeft () * (upper - 1) / upper : 0, snapshot.bridge ().controllerSettings ().vuMetersEnabled () ? track.vuRight () * (upper - 1) / upper : 0, accent, background);
            }
            else if (role == GlobalMixerControlsView.Role.PAN)
            {
                value (commands, index * COLUMN, 55, formatPan (parameter.value () / (upper - 1)), 19, 69, text);
                pan (commands, index * COLUMN, ratio, accent, background);
            }
            else
            {
                final String displayed = parameter.displayedValue ();
                value (commands, index * COLUMN, 55, displayed.substring (0, Math.min (8, displayed.length ())), 19, 69, text);
                // The inherited global Send renderer chooses its widget from the parameter name.
                // Keep this quirk until an explicit visual policy change replaces it.
                if (parameter.name ().contains ("Volume")) sendVolume (commands, index * COLUMN, ratio, accent, background);
                else if ("Pan".equals (parameter.name ()) || parameter.name ().contains ("Panning")) pan (commands, index * COLUMN, ratio, accent, background);
                else ring (commands, index * COLUMN, ratio, accent, background);
            }
        }
        return new ControllerDisplayScene (960, 143, commands);
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

    private static String formatPan (final double normalized)
    {
        final double bipolar = 2 * normalized - 1;
        final int amount = (int) Math.round (100 * Math.abs (bipolar));
        return amount == 0 ? "C" : (bipolar < 0 ? "L " : "R ") + amount;
    }

    private static double ratio (final double value) { return Math.max (0, Math.min (1, value)); }
    private static RgbColor dim (final RgbColor color)
    {
        final int gray = (int) Math.round ((color.red () + color.green () + color.blue ()) / 3.0 * 0.4);
        return new RgbColor (gray, gray, gray);
    }
}
