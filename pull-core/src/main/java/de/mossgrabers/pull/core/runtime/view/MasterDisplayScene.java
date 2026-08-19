// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.MasterSnapshot;
import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.MixerControlRole;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayIcon;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;


/** Reloadable composition of the complete Push 2 Master display. */
final class MasterDisplayScene
{
    private static final int     WIDTH                   = 960;
    private static final int     HEIGHT                  = 160;
    private static final double  COLUMN_WIDTH            = WIDTH / 8.0;
    private static final double  CONTENT_LEFT            = 8.0;
    private static final double  MENU_HEIGHT             = HEIGHT / 12.0 + 4.0;
    private static final double  LABEL_BASELINE          = 34.0;
    private static final double  LABEL_FONT_SIZE         = 12.5;
    private static final double  STATUS_VALUE_TOP        = 35.0;
    private static final double  STATUS_VALUE_HEIGHT     = 25.0;
    private static final double  STATUS_MAX_FONT_SIZE    = 19.0;
    private static final double  STATUS_MIN_FONT_SIZE    = 12.0;
    private static final double  RING_CENTER_Y           = 106.0;
    private static final double  TOGGLE_WIDTH            = 66.0;
    private static final double  TOGGLE_HEIGHT           = 32.0;
    private static final double  TOGGLE_THUMB_RADIUS     = 10.0;
    private static final double  TOGGLE_THUMB_GAP        = 5.0;
    private static final double  TOGGLE_INSET            = 1.4;
    private static final double  FOOTER_TOP              = 143.0;
    private static final double  PARAMETER_UPPER_BOUND   = 1024.0;

    private static final RgbColor BLACK       = new RgbColor (0, 0, 0);
    private static final RgbColor WHITE       = new RgbColor (255, 255, 255);
    private static final RgbColor DIM_WHITE   = new RgbColor (102, 102, 102);
    private static final RgbColor GRAY        = new RgbColor (128, 128, 128);
    private static final RgbColor FOOTER_GRAY = new RgbColor (30, 30, 30);
    private static final RgbColor TOGGLE_ON   = new RgbColor (55, 185, 64);

    private MasterDisplayScene ()
    {
        // Utility class.
    }


    /** Compose one complete authoritative scene. */
    static ControllerDisplayScene render (final MasterSnapshot master, final Map<ParameterSlot, ParameterTargetSnapshot> parameters)
    {
        final MasterSnapshot state = Objects.requireNonNull (master, "master");
        final Map<ParameterSlot, ParameterTargetSnapshot> values = Objects.requireNonNull (parameters, "parameters");
        final ArrayList<DisplayCommand> commands = new ArrayList<> (64);
        commands.add (new DisplayCommand.Rectangle (0, 0, WIDTH, HEIGHT, BLACK));

        final ParameterTargetSnapshot volume = values.get (ParameterSlot.MASTER_MIX_VOLUME);
        if (volume != null)
            MixerDisplayScene.append (commands, mixerControl (0, MixerControlKind.VOLUME, "Volume", volume, state.trackActive (), state.trackColor (), state.vuLeft (), state.vuRight ()));
        drawFooter (commands, 0, state.trackName (), state.cursorPinned () ? DisplayIcon.PIN : DisplayIcon.MASTER, state.trackColor (), state.trackSelected (), state.trackActive ());

        final ParameterTargetSnapshot pan = values.get (ParameterSlot.MASTER_MIX_PAN);
        if (pan != null)
            MixerDisplayScene.append (commands, mixerControl (1, MixerControlKind.PAN, "Pan", pan, state.trackActive (), state.trackColor (), 0, 0));

        final ParameterTargetSnapshot cueVolume = values.get (ParameterSlot.CUE_VOLUME);
        if (cueVolume != null)
            MixerDisplayScene.append (commands, mixerControl (2, MixerControlKind.KNOB, "Cue Volume", cueVolume, true, state.trackColor (), 0, 0));
        drawFooter (commands, 2, "Cue", null, FOOTER_GRAY, false, true);

        final ParameterTargetSnapshot cueMix = values.get (ParameterSlot.CUE_MIX);
        if (cueMix != null)
            MixerDisplayScene.append (commands, mixerControl (3, MixerControlKind.KNOB, "Cue Mix", cueMix, true, state.trackColor (), 0, 0));

        drawLabel (commands, 4, "Audio Engine");
        drawToggle (commands, 4, state.engineActive ());
        drawLabel (commands, 5, "Project");
        drawStatusValue (commands, 5, state.projectName ());

        drawHeader (commands, 6, "Previous", state.canPrevious ());
        drawFooter (commands, 6, "Load", null, WHITE, false, true);
        drawHeader (commands, 7, "Next", state.canNext ());
        drawFooter (commands, 7, "Save", null, WHITE, false, true);
        return new ControllerDisplayScene (WIDTH, HEIGHT, commands);
    }


    private static void drawHeader (final ArrayList<DisplayCommand> commands, final int column, final String text, final boolean active)
    {
        commands.add (new DisplayCommand.TextBox (text, left (column) + CONTENT_LEFT, 0, COLUMN_WIDTH - 2 * CONTENT_LEFT, MENU_HEIGHT, DisplayTextAlignment.LEFT, active ? WHITE : DIM_WHITE, 12, 12, DisplayTextFit.CLIP));
    }


    private static void drawLabel (final ArrayList<DisplayCommand> commands, final int column, final String text)
    {
        commands.add (new DisplayCommand.TextAt (text, left (column) + CONTENT_LEFT, LABEL_BASELINE, WHITE, LABEL_FONT_SIZE));
    }


    private static void drawStatusValue (final ArrayList<DisplayCommand> commands, final int column, final String text)
    {
        if (text == null || text.isBlank ())
            return;
        commands.add (new DisplayCommand.TextBox (text, left (column) + CONTENT_LEFT, STATUS_VALUE_TOP, COLUMN_WIDTH - 2 * CONTENT_LEFT, STATUS_VALUE_HEIGHT, DisplayTextAlignment.LEFT, WHITE, STATUS_MAX_FONT_SIZE, STATUS_MIN_FONT_SIZE, DisplayTextFit.SHRINK_ELLIPSIS));
    }


    private static void drawToggle (final ArrayList<DisplayCommand> commands, final int column, final boolean on)
    {
        final double left = left (column) + CONTENT_LEFT;
        final double top = RING_CENTER_Y - TOGGLE_HEIGHT / 2.0;
        final double radius = TOGGLE_HEIGHT / 2.0;
        final RgbColor color = on ? TOGGLE_ON : GRAY;
        final double thumbX = on ? left + TOGGLE_WIDTH - TOGGLE_THUMB_GAP - TOGGLE_THUMB_RADIUS : left + TOGGLE_THUMB_GAP + TOGGLE_THUMB_RADIUS;
        commands.add (new DisplayCommand.RoundedRectangle (left, top, TOGGLE_WIDTH, TOGGLE_HEIGHT, radius, color));
        if (on)
        {
            commands.add (new DisplayCommand.Circle (thumbX, RING_CENTER_Y, TOGGLE_THUMB_RADIUS, BLACK));
            return;
        }
        commands.add (new DisplayCommand.RoundedRectangle (left + TOGGLE_INSET, top + TOGGLE_INSET, TOGGLE_WIDTH - 2 * TOGGLE_INSET, TOGGLE_HEIGHT - 2 * TOGGLE_INSET, radius - TOGGLE_INSET, BLACK));
        commands.add (new DisplayCommand.Circle (thumbX, RING_CENTER_Y, TOGGLE_THUMB_RADIUS, color));
        commands.add (new DisplayCommand.Circle (thumbX, RING_CENTER_Y, TOGGLE_THUMB_RADIUS - TOGGLE_INSET, BLACK));
    }


    private static void drawFooter (final ArrayList<DisplayCommand> commands, final int column, final String text, final DisplayIcon icon, final RgbColor color, final boolean selected, final boolean active)
    {
        TrackFooterDisplayScene.append (commands, column, FOOTER_TOP, text, icon, color, selected, active);
    }


    private static MixerControlSnapshot mixerControl (final int column, final MixerControlKind kind, final String label, final ParameterTargetSnapshot parameter, final boolean active, final RgbColor accent, final int vuLeft, final int vuRight)
    {
        return new MixerControlSnapshot (
            column,
            kind,
            label,
            ratio (parameter.value ()),
            parameter.modulatedValue () == -1 ? -1 : ratio (parameter.modulatedValue ()),
            parameter.displayedValue (),
            MixerControlRole.HOST_COLORED,
            active,
            false,
            Optional.of (accent),
            ratio (vuLeft),
            ratio (vuRight));
    }


    private static double left (final int column)
    {
        return column * COLUMN_WIDTH;
    }


    private static double ratio (final double value)
    {
        return Math.max (0, Math.min (1, value / PARAMETER_UPPER_BOUND));
    }


}
