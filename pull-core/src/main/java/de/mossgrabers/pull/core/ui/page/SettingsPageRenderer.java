// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static de.mossgrabers.pull.core.ui.PageStyle.*;

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
            final ChoiceCell choice = choice (state, index);
            lights.put (PushControlIds.button ("ROW1_" + (index + 1)), choice.lightColor ());
            if (state.available () && index < 4)
            {
                final String heading = automation ? "Automation Mode" : "Pre-roll";
                text (commands, index == 0 ? heading : "", index, SettingsPageStyle.HEADING_TOP, SettingsPageStyle.HEADING_FONT);
            }
            if (state.available () && !automation && index == 5)
                text (commands, "during Pre-Roll?", index, SettingsPageStyle.HEADING_TOP, SettingsPageStyle.HEADING_FONT);
            choice.append (commands, index * COLUMN_WIDTH + SettingsPageStyle.CONTENT_LEFT, SettingsPageStyle.OPTION_TOP, SettingsPageStyle.CHOICE);
        }
        if (state.available () && state instanceof final SettingsPagePresentation.Metronome metronome)
        {
            text (commands, "Play Metronome", 5, SettingsPageStyle.METRONOME_HEADING_TOP, SettingsPageStyle.METRONOME_HEADING_FONT);
            metronome.volume ().ifPresent (volume -> MixerDisplayScene.append (commands, new MixerControlSnapshot (7, MixerControlKind.KNOB, "Volume", volume.value (), volume.modulatedValue (), volume.displayedValue (), MixerControlRole.HOST_COLORED, true, false, Optional.of (WHITE), 0, 0)));
        }
        return new PageVisuals (lights, new ControllerDisplayScene (WIDTH, HEIGHT, commands));
    }

    private static ChoiceCell choice (final SettingsPagePresentation state, final int index)
    {
        if (state instanceof final SettingsPagePresentation.Automation automation && index < 4)
        {
            final boolean selected = index == 0 ? !automation.writingEnabled () : automation.writingEnabled () && automation.mode () == AutomationWriteMode.values ()[index];
            return new ChoiceCell (AUTOMATION_LABELS.get (index), state.available (), selected);
        }
        if (state instanceof final SettingsPagePresentation.Metronome metronome)
        {
            if (index < 4)
                return new ChoiceCell (PRE_ROLL_LABELS.get (index), state.available (), metronome.preRoll () == PreRoll.values ()[index]);
            if (index == 5)
                return new ChoiceCell (metronome.duringPreRoll () ? "Yes" : "No", state.available (), metronome.duringPreRoll ());
        }
        return new ChoiceCell ("", false, false);
    }

    private static void text (final List<DisplayCommand> commands, final String text, final int column, final double y, final double size)
    {
        commands.add (new DisplayCommand.TextBox (text, column * COLUMN_WIDTH + SettingsPageStyle.CONTENT_LEFT, y, SettingsPageStyle.CONTENT_WIDTH, SettingsPageStyle.TEXT_HEIGHT, DisplayTextAlignment.LEFT, WHITE, size, SettingsPageStyle.TEXT_MIN_FONT, DisplayTextFit.SHRINK_ELLIPSIS));
    }
}
