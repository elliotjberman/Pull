// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import java.util.List;
import java.util.Map;
import static de.mossgrabers.pull.core.ui.PageStyle.*;

/** The shared Info/Setup menu and its owned row feedback, independent of navigation behavior. */
final class ConfigurationTabs
{
    private static final ChoiceCell.Style CHOICE = new ChoiceCell.Style (COLUMN_WIDTH - 2, 34, 4, 2, 17, 10, DARK);

    private ConfigurationTabs () { }

    static void append (final List<DisplayCommand> commands, final Map<ControlId, RgbColor> lights, final boolean setupSelected)
    {
        for (int index = 0; index < COLUMNS; index++)
        {
            final ChoiceCell choice = new ChoiceCell (index == 0 ? "Info" : index == 1 ? "Setup" : "", true, index == (setupSelected ? 1 : 0));
            choice.append (commands, index * COLUMN_WIDTH, 0, CHOICE);
            lights.put (PushControlIds.button ("ROW1_" + (index + 1)), BLACK);
            lights.put (PushControlIds.button ("ROW2_" + (index + 1)), choice.lightColor ());
        }
    }
}
