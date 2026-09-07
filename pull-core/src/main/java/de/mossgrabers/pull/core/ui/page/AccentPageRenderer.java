// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.ui.component.ParameterValue;
import de.mossgrabers.pull.core.ui.component.RingMeter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static de.mossgrabers.pull.core.ui.PageStyle.*;
import static de.mossgrabers.pull.core.ui.page.MacroPageStyle.*;

/** Fixed-velocity graphics; the complete scene is derived from observed presentation values. */
public final class AccentPageRenderer
{
    private AccentPageRenderer () { }

    public static PageVisuals render (final AccentPagePresentation state)
    {
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        for (int index = 1; index <= COLUMNS; index++)
        {
            lights.put (PushControlIds.button ("ROW1_" + index), BLACK);
            lights.put (PushControlIds.button ("ROW2_" + index), BLACK);
        }
        final List<DisplayCommand> commands = new ArrayList<> ();
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK));
        if (state.available ())
        {
            final double left = 7 * COLUMN_WIDTH + CONTENT_LEFT;
            final RgbColor text = brightness (METER_TEXT, state.touched ());
            commands.add (new DisplayCommand.TextBox ("Accent", left, LABEL_TOP, CONTENT_WIDTH, LABEL_HEIGHT, DisplayTextAlignment.LEFT, text, LABEL_FONT, LABEL_MIN_FONT, DisplayTextFit.SHRINK_ELLIPSIS));
            ParameterValue.append (commands, new ParameterValue.Content (Integer.toString (state.velocity ()), ""), left, VALUE_TOP, text, VALUE);
            RingMeter.append (commands, left + RING.radius (), RING_CENTER_Y, state.position (), brightness (METER_OFF, state.touched ()), brightness (METER_ON, state.touched ()), RING);
        }
        return new PageVisuals (lights, new ControllerDisplayScene (WIDTH, HEIGHT, commands));
    }
}
