// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.MixerControlRole;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.SelectedTrackSnapshot;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;


/** Core-owned 960x143 selected-track Mix region. */
final class TrackMixerDisplayScene
{
    private static final int WIDTH = 960;
    private static final int HEIGHT = 143;
    private static final int COLUMN_WIDTH = WIDTH / ParameterSlot.BANK_SIZE;
    private static final int MENU_HEIGHT = 17;
    private static final double PARAMETER_UPPER_BOUND = 1024.0;

    private static final RgbColor BLACK = new RgbColor (0, 0, 0);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);
    private static final RgbColor SELECTED_MENU = new RgbColor (190, 190, 190);


    private TrackMixerDisplayScene ()
    {
        // Utility class.
    }


    /** Render the active compatibility parameter window with authoritative selected-track state. */
    static ControllerDisplayScene render (final SelectedTrackSnapshot selected, final Map<ParameterSlot, ParameterTargetSnapshot> parameters)
    {
        final ArrayList<DisplayCommand> commands = new ArrayList<> (96);
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK));
        drawMenu (commands, 0, "Mix", true);
        drawMenu (commands, 1, "Input & Output", false);

        if (!selected.exists ())
            return new ControllerDisplayScene (WIDTH, HEIGHT, commands);

        for (int index = 0; index < ParameterSlot.BANK_SIZE; index++)
        {
            final ParameterTargetSnapshot parameter = parameters.get (ParameterSlot.active (index));
            if (parameter == null)
                continue;
            final MixerControlKind kind = index == 0 ? MixerControlKind.VOLUME : index == 1 ? MixerControlKind.PAN : MixerControlKind.KNOB;
            final String label = kind == MixerControlKind.KNOB ? nonBlank (parameter.name (), "Send " + (index - 1)) : "";
            final double value = index == 0 ? selected.volume () : index == 1 ? selected.pan () : ratio (parameter.value ());
            final double modulated = parameter.modulatedValue () == -1 ? -1 : ratio (parameter.modulatedValue ());
            MixerDisplayScene.append (commands, new MixerControlSnapshot (
                index,
                kind,
                label,
                value,
                modulated,
                parameter.displayedValue (),
                MixerControlRole.HOST_COLORED,
                selected.activated (),
                false,
                Optional.of (selected.color ()),
                0,
                0));
        }
        return new ControllerDisplayScene (WIDTH, HEIGHT, commands);
    }


    private static void drawMenu (final ArrayList<DisplayCommand> commands, final int column, final String text, final boolean selected)
    {
        final double left = column * COLUMN_WIDTH;
        if (selected)
            commands.add (new DisplayCommand.Rectangle (left, 0, COLUMN_WIDTH - 2, MENU_HEIGHT, SELECTED_MENU));
        commands.add (new DisplayCommand.TextBox (
            text,
            left + 7,
            0,
            COLUMN_WIDTH - 14,
            MENU_HEIGHT,
            DisplayTextAlignment.LEFT,
            selected ? BLACK : WHITE,
            12,
            10,
            DisplayTextFit.SHRINK_ELLIPSIS));
    }


    private static String nonBlank (final String value, final String fallback)
    {
        return value == null || value.isBlank () ? fallback : value;
    }


    private static double ratio (final double value)
    {
        return Math.max (0, Math.min (1, value / PARAMETER_UPPER_BOUND));
    }
}
