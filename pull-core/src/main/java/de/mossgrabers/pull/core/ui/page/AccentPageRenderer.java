// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static de.mossgrabers.pull.core.ui.page.PageStyle.*;
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
            commands.add (new DisplayCommand.TextAt ("Accent", left, LABEL_BASELINE, text, LABEL_FONT));
            commands.add (new DisplayCommand.TextAt (Integer.toString (state.velocity ()), left, VALUE_BASELINE, text, VALUE_FONT));
            commands.add (new DisplayCommand.DottedArc (left + RING_RADIUS, RING_CENTER_Y, RING_RADIUS, RING_START, RING_SWEEP, RING_STEPS, RING_DOT_RADIUS, brightness (METER_OFF, state.touched ())));
            commands.add (new DisplayCommand.DottedArc (left + RING_RADIUS, RING_CENTER_Y, RING_RADIUS, RING_START, RING_SWEEP * state.position (), Math.max (2, (int) Math.ceil (RING_STEPS * state.position ())), RING_DOT_RADIUS, brightness (METER_ON, state.touched ())));
        }
        return new PageVisuals (lights, new ControllerDisplayScene (WIDTH, HEIGHT, commands));
    }
}
