// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.output.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static de.mossgrabers.pull.core.ui.page.PageStyle.*;

/** Fixed settings-page graphics and row lights, without host or gesture state access. */
public final class SettingsPageRenderer
{
    private static final List<String> PRE_ROLL_LABELS = List.of ("None", "1 Bar", "2 Bars", "4 Bars");
    private static final List<String> AUTOMATION_LABELS = List.of ("Read", "Latch", "Touch", "Write");

    private SettingsPageRenderer () { }

    public static PageVisuals render (final SettingsPagePresentation state)
    {
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        final List<DisplayCommand> commands = new ArrayList<> ();
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK));
        final boolean automation = state instanceof SettingsPagePresentation.Automation;
        for (int index = 0; index < COLUMNS; index++)
        {
            lights.put (PushControlIds.button ("ROW2_" + (index + 1)), BLACK);
            final boolean selected = selected (state, index);
            final boolean enabled = state.available () && (index < 4 || !automation && index == 5);
            final RgbColor color = selected ? WHITE : GREY;
            lights.put (PushControlIds.button ("ROW1_" + (index + 1)), enabled ? color : BLACK);
            if (state.available () && index < 4)
            {
                final String heading = automation ? "Automation Mode" : "Pre-roll";
                option (commands, index, index == 0 ? heading : "", automation ? AUTOMATION_LABELS.get (index) : PRE_ROLL_LABELS.get (index), selected);
            }
        }
        if (state.available () && state instanceof final SettingsPagePresentation.Metronome metronome)
        {
            option (commands, 5, "during Pre-Roll?", metronome.duringPreRoll () ? "Yes" : "No", metronome.duringPreRoll ());
            text (commands, "Play Metronome", 5, SettingsPageStyle.METRONOME_HEADING_TOP, SettingsPageStyle.METRONOME_HEADING_FONT);
            metronome.volume ().ifPresent (volume -> MixerDisplayScene.append (commands, new MixerControlSnapshot (7, MixerControlKind.KNOB, "Volume", volume.value (), volume.modulatedValue (), volume.displayedValue (), MixerControlRole.HOST_COLORED, true, false, Optional.of (WHITE), 0, 0)));
        }
        return new PageVisuals (lights, new ControllerDisplayScene (WIDTH, HEIGHT, commands));
    }

    private static boolean selected (final SettingsPagePresentation state, final int index)
    {
        return switch (state)
        {
            case final SettingsPagePresentation.Automation automation -> index == 0 ? !automation.writingEnabled () : index < 4 && automation.writingEnabled () && automation.mode () == AutomationWriteMode.values ()[index];
            case final SettingsPagePresentation.Metronome metronome -> index < 4 ? metronome.preRoll () == PreRoll.values ()[index] : index == 5 && metronome.duringPreRoll ();
        };
    }

    private static void option (final List<DisplayCommand> commands, final int column, final String heading, final String label, final boolean selected)
    {
        text (commands, heading, column, SettingsPageStyle.HEADING_TOP, SettingsPageStyle.HEADING_FONT);
        commands.add (new DisplayCommand.Rectangle (column * COLUMN_WIDTH + SettingsPageStyle.CONTENT_LEFT, SettingsPageStyle.OPTION_TOP, SettingsPageStyle.CONTENT_WIDTH, SettingsPageStyle.OPTION_HEIGHT, selected ? WHITE : GREY));
        commands.add (new DisplayCommand.TextBox (label, column * COLUMN_WIDTH + SettingsPageStyle.LABEL_LEFT, SettingsPageStyle.LABEL_TOP, SettingsPageStyle.LABEL_WIDTH, SettingsPageStyle.LABEL_HEIGHT, DisplayTextAlignment.CENTER, selected ? BLACK : WHITE, SettingsPageStyle.LABEL_FONT, SettingsPageStyle.LABEL_MIN_FONT, DisplayTextFit.SHRINK_ELLIPSIS));
    }

    private static void text (final List<DisplayCommand> commands, final String text, final int column, final double y, final double size)
    {
        commands.add (new DisplayCommand.TextBox (text, column * COLUMN_WIDTH + SettingsPageStyle.CONTENT_LEFT, y, SettingsPageStyle.CONTENT_WIDTH, SettingsPageStyle.TEXT_HEIGHT, DisplayTextAlignment.LEFT, WHITE, size, SettingsPageStyle.TEXT_MIN_FONT, DisplayTextFit.SHRINK_ELLIPSIS));
    }
}
