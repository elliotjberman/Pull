// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.output.*;
import de.mossgrabers.pull.core.ui.component.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static de.mossgrabers.pull.core.ui.PageStyle.*;

/** Clip, Note, Quantize and Groove share the production choice/parameter/footer components. */
public final class EditingPageRenderer
{
    private static final ChoiceCell.Style MENU = new ChoiceCell.Style (COLUMN_WIDTH - 2, 17, 5, 0, 13, 9);
    private EditingPageRenderer () { }
    public static ControllerDisplayScene render (final EditingPageState state) { return render (EditingPageProjector.project (state)); }
    public static ControllerDisplayScene render (final EditingPagePresentation page)
    {
        final List<DisplayCommand> commands = new ArrayList<> (List.of (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK)));
        for (int index = 0; index < page.upper ().size (); index++) page.upper ().get (index).append (commands, index * COLUMN_WIDTH, 0, MENU);
        for (int index = 0; index < page.lower ().size (); index++) page.lower ().get (index).append (commands, index * COLUMN_WIDTH, HEIGHT - 17, MENU);
        for (final var control: page.controls ())
        {
            if (control.kind () == EditingPagePresentation.Kind.RING)
            {
                MixerDisplayScene.append (commands, new MixerControlSnapshot (control.column (), MixerControlKind.KNOB, TextContent.candidate (control.name (), 32), control.position (), -1, TextContent.candidate (control.value (), 32), MixerControlRole.HOST_COLORED, true, control.touched (), Optional.of (WHITE), 0, 0));
                continue;
            }
            final double left = control.column () * COLUMN_WIDTH + MixerControlStyle.CONTENT_LEFT;
            text (commands, control.name (), left, MixerControlStyle.LABEL_TOP, COLUMN_WIDTH - 16, MixerControlStyle.LABEL_HEIGHT, MixerControlStyle.LABEL_FONT_SIZE);
            ParameterValue.append (commands, new ParameterValue.Content (TextContent.candidate (control.value (), 32), ""), left, MixerControlStyle.VALUE_TOP, WHITE, MixerControlStyle.VALUE);
            if (control.kind () == EditingPagePresentation.Kind.TOGGLE) Toggle.append (commands, left, 91, control.position () > 0, WHITE);
        }
        for (final var footer: page.footer ().cells ()) TrackFooterRenderer.append (commands, footer.column (), HEIGHT - TrackFooterStyle.HEIGHT, footer.name (), footer.icon (), footer.color (), footer.selected (), footer.active ());
        for (final var label: page.labels ()) text (commands, label.text (), label.column () * COLUMN_WIDTH + 8, label.bottom () ? HEIGHT - 18 : 40, COLUMN_WIDTH - 16, 18, 12);
        if (!page.message ().isBlank ()) text (commands, page.message (), 24, 62, WIDTH - 48, 35, 23);
        return new ControllerDisplayScene (WIDTH, HEIGHT, commands);
    }
    private static void text (final List<DisplayCommand> commands, final String value, final double left, final double top, final double width, final double height, final double size)
    {
        if (!value.isBlank ()) commands.add (new DisplayCommand.TextBox (TextContent.candidate (value, width > 200 ? 128 : 32), left, top, width, height, DisplayTextAlignment.LEFT, WHITE, size, Math.min (9, size), DisplayTextFit.SHRINK_ELLIPSIS));
    }
}
