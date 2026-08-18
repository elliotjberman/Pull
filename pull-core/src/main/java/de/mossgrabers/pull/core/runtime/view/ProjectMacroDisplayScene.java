// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.Map;


/** Core-owned parameter region for the eight VS Live project macros. */
final class ProjectMacroDisplayScene
{
    static final int WIDTH = 960;
    static final int HEIGHT = 143;

    private static final int PARAMETER_UPPER_BOUND = 1024;
    private static final int COLUMN_WIDTH = WIDTH / ParameterSlot.BANK_SIZE;
    private static final RgbColor BLACK = new RgbColor (0, 0, 0);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);


    private ProjectMacroDisplayScene ()
    {
        // Utility class.
    }


    /** Render all available project-remote targets into the fixed parameter region. */
    static ControllerDisplayScene render (final Map<ParameterSlot, ParameterTargetSnapshot> parameters)
    {
        final ArrayList<DisplayCommand> commands = new ArrayList<> (64);
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK));
        commands.add (new DisplayCommand.TextBox ("Project", 8, 0, COLUMN_WIDTH - 16, 17, DisplayTextAlignment.LEFT, WHITE, 12, 12, DisplayTextFit.CLIP));
        for (int index = 0; index < ParameterSlot.BANK_SIZE; index++)
        {
            final ParameterTargetSnapshot target = parameters.get (ParameterSlot.projectRemote (index));
            if (target == null)
                continue;
            MixerDisplayScene.append (commands, new MixerControlSnapshot (
                index,
                MixerControlKind.KNOB,
                target.name ().isBlank () ? "Macro " + (index + 1) : target.name (),
                normalize (target.value ()),
                normalize (target.modulatedValue ()),
                target.displayedValue (),
                true,
                WHITE,
                0,
                0));
        }
        return new ControllerDisplayScene (WIDTH, HEIGHT, commands);
    }


    private static double normalize (final double value)
    {
        return Math.max (0, Math.min (1, value / PARAMETER_UPPER_BOUND));
    }
}
