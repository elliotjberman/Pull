// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.SessionTrackSnapshot;
import de.mossgrabers.pull.core.api.SessionTrackType;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayIcon;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.List;


/** Shared core-owned projection of the legacy Push track footer. */
final class TrackFooterDisplayScene
{
    static final double HEIGHT = 17.0;

    private static final double COLUMN_WIDTH = 120.0;
    private static final double INSET = 7.7;
    private static final RgbColor BLACK = new RgbColor (0, 0, 0);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);


    private TrackFooterDisplayScene ()
    {
        // Utility class.
    }


    /** Append one authoritative Session track. */
    static void append (final List<DisplayCommand> commands, final int column, final double top, final SessionTrackSnapshot track)
    {
        append (commands, column, top, track.name (), icon (track.type ()), track.color (), track.selected (), track.activated ());
    }


    /** Append one explicitly described footer cell. */
    static void append (final List<DisplayCommand> commands, final int column, final double top, final String text, final DisplayIcon icon, final RgbColor color, final boolean selected, final boolean active)
    {
        if (text == null || text.isEmpty ())
            return;
        final double left = column * COLUMN_WIDTH;
        final RgbColor footerColor = active ? color : dimToGray (color);
        final RgbColor contentColor = selected ? contrast (footerColor) : footerColor;
        commands.add (new DisplayCommand.Rectangle (left, top, COLUMN_WIDTH - 2, HEIGHT, selected ? footerColor : BLACK));
        double textLeft = left + INSET;
        if (icon != null)
        {
            final double iconWidth = iconWidth (icon);
            commands.add (new DisplayCommand.Icon (icon, textLeft, top, iconWidth, HEIGHT, contentColor));
            textLeft += iconWidth + INSET;
        }
        commands.add (new DisplayCommand.TextBox (text, textLeft, top, left + COLUMN_WIDTH - textLeft - INSET, HEIGHT, DisplayTextAlignment.LEFT, contentColor, 160.0 / 12.0, 160.0 / 12.0, DisplayTextFit.CLIP));
    }


    private static DisplayIcon icon (final SessionTrackType type)
    {
        return switch (type)
        {
            case AUDIO -> DisplayIcon.AUDIO_TRACK;
            case INSTRUMENT -> DisplayIcon.INSTRUMENT_TRACK;
            case HYBRID -> DisplayIcon.HYBRID_TRACK;
            case GROUP -> DisplayIcon.GROUP_TRACK;
            case GROUP_OPEN -> DisplayIcon.GROUP_TRACK_OPEN;
            case EFFECT -> DisplayIcon.RETURN_TRACK;
            case MASTER -> DisplayIcon.MASTER;
            case LAYER -> DisplayIcon.MULTI_LAYER;
            case UNKNOWN, CUE -> null;
        };
    }


    private static RgbColor dimToGray (final RgbColor color)
    {
        final int dimmed = (int) Math.round ((color.red () + color.green () + color.blue ()) / 3.0 * 0.4);
        return new RgbColor (dimmed, dimmed, dimmed);
    }


    private static RgbColor contrast (final RgbColor color)
    {
        final double luminance = 0.2126 * color.red () + 0.7152 * color.green () + 0.0722 * color.blue ();
        return luminance > 0.179 * 255 ? BLACK : WHITE;
    }


    private static double iconWidth (final DisplayIcon icon)
    {
        return icon == DisplayIcon.GROUP_TRACK_OPEN ? 16.0 : 15.0;
    }
}
