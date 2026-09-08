// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.ui.component.BipolarSlider;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import de.mossgrabers.pull.core.ui.component.FaderMarker;
import de.mossgrabers.pull.core.ui.component.ParameterValue;
import de.mossgrabers.pull.core.ui.component.RingMeter;
import de.mossgrabers.pull.core.ui.component.TextContent;
import de.mossgrabers.pull.core.ui.component.VerticalMeter;

import de.mossgrabers.pull.core.api.output.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import static de.mossgrabers.pull.core.ui.PageStyle.*;
import static de.mossgrabers.pull.core.ui.page.MixerPageStyle.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Global-bank controls assembled from shared components above the independently composed footer. */
public final class GlobalMixerPageRenderer
{
    private static final Pattern VALUE_UNIT = Pattern.compile ("^(.+?)(?:\\s*)(%|dB|kHz|Hz|ms|sec|s|st|ct|BPM|x|L|R)$");

    private GlobalMixerPageRenderer () { }

    public static PageVisuals render (final GlobalMixerPagePresentation page)
    {
        final ArrayList<DisplayCommand> commands = new ArrayList<> (100);
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, PARAMETER_HEIGHT, BLACK));
        final Map<ControlId, RgbColor> lights = new LinkedHashMap<> ();
        for (int index = 0; index < COLUMNS; index++)
        {
            final GlobalMixerPagePresentation.MenuItem item = page.menu ().get (index);
            lights.put (PushControlIds.button ("ROW2_" + (index + 1)), item.selected () || item.arrow () ? WHITE : BLACK);
            new ChoiceCell (TextContent.candidate (item.text (), 24), !item.text ().isBlank (), item.selected ()).append (commands, index * COLUMN, 0, GlobalMixerPageStyle.MENU);
        }
        for (final GlobalMixerPagePresentation.Control control: page.controls ())
        {
            final double left = control.column () * COLUMN;
            final RgbColor accent = control.active () ? control.color () : dim (control.color ());
            final RgbColor text = control.active () ? WHITE : DIM_WHITE;
            final RgbColor background = control.active () ? DARK : DIM_DARK;
            if (control.widget () == GlobalMixerPagePresentation.Widget.VOLUME)
            {
                value (commands, left, MENU_HEIGHT + 3, control.displayedValue (), text, GlobalMixerPageStyle.VOLUME_VALUE);
                VerticalMeter.append (commands, left + 10, GlobalMixerPageStyle.VOLUME_TOP, control.vuLeft (), accent, background, GlobalMixerPageStyle.VOLUME_METER);
                VerticalMeter.append (commands, left + 31, GlobalMixerPageStyle.VOLUME_TOP, control.vuRight (), accent, background, GlobalMixerPageStyle.VOLUME_METER);
                FaderMarker.append (commands, left + 75, GlobalMixerPageStyle.VOLUME_TOP, control.value (), accent, GlobalMixerPageStyle.VOLUME_FADER);
            }
            else
            {
                value (commands, left, 36, control.displayedValue (), text, GlobalMixerPageStyle.VALUE);
                switch (control.widget ())
                {
                    case SEND_VOLUME -> {
                        VerticalMeter.append (commands, left + 8, 60, 0, accent, background, GlobalMixerPageStyle.SEND_METER);
                        VerticalMeter.append (commands, left + 36, 60, 0, accent, background, GlobalMixerPageStyle.SEND_METER);
                        FaderMarker.append (commands, left + 80, 60, control.value (), accent, GlobalMixerPageStyle.SEND_FADER);
                    }
                    case PAN -> BipolarSlider.append (commands, left + 8, 106, control.value (), accent, background, GlobalMixerPageStyle.PAN);
                    case RING -> RingMeter.append (commands, left + 31, 106, control.value (), background, accent, GlobalMixerPageStyle.RING);
                    default -> throw new IllegalStateException ("Volume rendered above");
                }
            }
        }
        return new PageVisuals (lights, new ControllerDisplayScene (WIDTH, PARAMETER_HEIGHT, commands));
    }

    private static void value (final List<DisplayCommand> commands, final double left, final double top, final String text, final RgbColor color, final ParameterValue.Style style)
    {
        if (text.isBlank ()) return;
        final Matcher matcher = VALUE_UNIT.matcher (text.trim ());
        final ParameterValue.Content value = matcher.matches () ? new ParameterValue.Content (TextContent.candidate (matcher.group (1).trim (), 48), matcher.group (2)) : new ParameterValue.Content (TextContent.candidate (text, 48), "");
        ParameterValue.append (commands, value, left + 8, top, color, style);
    }
}
