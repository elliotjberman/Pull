// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.graphics.canvas.component;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.graphics.Align;
import de.mossgrabers.framework.graphics.IGraphicsContext;
import de.mossgrabers.framework.graphics.IGraphicsDimensions;
import de.mossgrabers.framework.graphics.IGraphicsInfo;
import de.mossgrabers.framework.graphics.canvas.component.LabelComponent.LabelLayout;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.MenuData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.ParameterData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.TrackData;


/**
 * A full-width volume bank for eight tracks.
 */
public class TrackVolumeComponent implements IComponent
{
    private static final ColorEx BACKGROUND         = ColorEx.BLACK;
    private static final double  CONTENT_LEFT       = 8.0;
    private static final double  VALUE_FONT_SIZE    = 18.0;
    private static final double  UNIT_FONT_SIZE     = 8.5;
    private static final double  UNIT_LEFT          = 66.0;
    private static final double  METER_LEFT         = 10.0;
    private static final double  METER_WIDTH        = 18.0;
    private static final double  METER_GAP          = 3.0;
    private static final double  FADER_LEFT         = 75.0;
    private static final double  FADER_MARKER_WIDTH = 12.0;

    private static final Pattern VALUE_UNIT_PATTERN = Pattern.compile ("^(.+?)(?:\\s*)(%|dB|kHz|Hz|ms|sec|s|st|ct|BPM|x|L|R)$");

    private final List<MenuData>      menus;
    private final List<ParameterData> parameters;
    private final List<TrackData>     tracks;
    private final List<Integer>       vuLeft;
    private final List<Integer>       vuRight;


    /**
     * Constructor.
     *
     * @param menus The top-row menu entries
     * @param parameters The track volume parameters
     * @param tracks The tracks shown in the footer
     * @param vuLeft The left-channel meter values
     * @param vuRight The right-channel meter values
     */
    public TrackVolumeComponent (final List<MenuData> menus, final List<ParameterData> parameters, final List<TrackData> tracks, final List<Integer> vuLeft, final List<Integer> vuRight)
    {
        this.menus = List.copyOf (menus);
        this.parameters = List.copyOf (parameters);
        this.tracks = List.copyOf (tracks);
        this.vuLeft = List.copyOf (vuLeft);
        this.vuRight = List.copyOf (vuRight);
    }


    /** {@inheritDoc} */
    @Override
    public void draw (final IGraphicsInfo info)
    {
        final IGraphicsContext gc = info.getContext ();
        final IGraphicsDimensions dimensions = info.getDimensions ();
        final double left = info.getBounds ().left ();
        final double width = info.getBounds ().width ();
        final double height = info.getBounds ().height ();
        final double columnWidth = width / 8.0;
        final double menuHeight = dimensions.getMenuHeight ();
        final double footerHeight = 2 * menuHeight;
        final double footerTop = height - footerHeight;

        gc.fillRectangle (left, 0, width, height, BACKGROUND);
        this.drawMenus (gc, left, columnWidth, menuHeight);
        this.drawFaders (gc, dimensions, left, columnWidth, menuHeight, footerTop);
        this.drawTracks (info, left, columnWidth, footerTop, footerHeight);
    }


    private void drawMenus (final IGraphicsContext gc, final double left, final double columnWidth, final double menuHeight)
    {
        final int count = Math.min (8, this.menus.size ());
        for (int i = 0; i < count; i++)
        {
            final MenuData menu = this.menus.get (i);
            if (menu.name () == null || menu.name ().isBlank ())
                continue;

            final double columnLeft = left + i * columnWidth;
            final ColorEx textColor;
            if (menu.selected ())
            {
                gc.fillRectangle (columnLeft, 0, columnWidth - 2, menuHeight - 1, ColorEx.WHITE);
                textColor = BACKGROUND;
            }
            else
                textColor = ColorEx.WHITE;

            gc.drawTextInBounds (menu.name (), columnLeft + CONTENT_LEFT, 0, columnWidth - 2 * CONTENT_LEFT, menuHeight, Align.LEFT, textColor, 12.0);
        }
    }


    private void drawFaders (final IGraphicsContext gc, final IGraphicsDimensions dimensions, final double left, final double columnWidth, final double menuHeight, final double footerTop)
    {
        final double valueBaseline = menuHeight + 21.0;
        final double faderTop = menuHeight + 28.0;
        final double faderHeight = Math.max (1, footerTop - faderTop - 5.0);
        final double upperBound = dimensions.getParameterUpperBound ();
        final int count = Math.min (8, Math.min (this.parameters.size (), Math.min (this.tracks.size (), Math.min (this.vuLeft.size (), this.vuRight.size ()))));

        for (int i = 0; i < count; i++)
        {
            final ParameterData parameter = this.parameters.get (i);
            if (parameter.name () == null || parameter.name ().isBlank ())
                continue;

            final double columnLeft = left + i * columnWidth;
            final ColorEx trackColor = this.tracks.get (i).color () == null ? ColorEx.WHITE : this.tracks.get (i).color ();
            final ColorEx onColor = parameter.active () ? trackColor : ColorEx.dimToGray (trackColor);
            final ColorEx offColor = parameter.active () ? ColorEx.DARKER_GRAY : ColorEx.evenDarker (ColorEx.DARKER_GRAY);
            final ColorEx textColor = parameter.active () ? ColorEx.WHITE : ColorEx.dimToGray (ColorEx.WHITE);
            this.drawValue (gc, columnLeft, valueBaseline, parameter.text (), textColor);

            final double leftRatio = this.toRatio (this.vuLeft.get (i).intValue (), upperBound);
            final double rightRatio = this.toRatio (this.vuRight.get (i).intValue (), upperBound);
            this.drawMeter (gc, columnLeft + METER_LEFT, faderTop, faderHeight, leftRatio, onColor, offColor);
            this.drawMeter (gc, columnLeft + METER_LEFT + METER_WIDTH + METER_GAP, faderTop, faderHeight, rightRatio, onColor, offColor);

            final double value = parameter.modulatedValue () == -1 ? parameter.value () : parameter.modulatedValue ();
            final double valueRatio = this.toRatio (value, upperBound);
            final double railX = columnLeft + FADER_LEFT;
            final double markerY = faderTop + (1 - valueRatio) * faderHeight;
            gc.fillRectangle (railX - FADER_MARKER_WIDTH, markerY, FADER_MARKER_WIDTH + 1, 1, onColor);
            gc.fillRectangle (railX, markerY, 1, faderTop + faderHeight - markerY, onColor);
        }
    }


    private void drawValue (final IGraphicsContext gc, final double left, final double baseline, final String text, final ColorEx color)
    {
        if (text == null || text.isBlank ())
            return;

        final Matcher matcher = VALUE_UNIT_PATTERN.matcher (text.trim ());
        if (matcher.matches ())
        {
            gc.drawTextAt (matcher.group (1).trim (), left + CONTENT_LEFT, baseline, color, VALUE_FONT_SIZE);
            gc.drawTextAt (matcher.group (2), left + UNIT_LEFT, baseline, color, UNIT_FONT_SIZE);
            return;
        }

        gc.drawTextAt (text, left + CONTENT_LEFT, baseline, color, VALUE_FONT_SIZE);
    }


    private void drawMeter (final IGraphicsContext gc, final double left, final double top, final double height, final double ratio, final ColorEx onColor, final ColorEx offColor)
    {
        gc.fillRectangle (left, top, METER_WIDTH, height, offColor);
        final double meterHeight = ratio * height;
        gc.fillRectangle (left, top + height - meterHeight, METER_WIDTH, meterHeight, onColor);
    }


    private double toRatio (final double value, final double upperBound)
    {
        return Math.max (0, Math.min (1, value / upperBound));
    }


    private void drawTracks (final IGraphicsInfo info, final double left, final double columnWidth, final double top, final double height)
    {
        final int count = Math.min (8, this.tracks.size ());
        for (int i = 0; i < count; i++)
        {
            final TrackData track = this.tracks.get (i);
            final LabelComponent footer = new LabelComponent (track.name (), ChannelSelectComponent.getIcon (track.type (), track.pinned ()), track.color (), track.selected (), track.active (), LabelLayout.TRACK);
            footer.draw (info.withBounds (left + i * columnWidth, top, columnWidth - 2, height));
        }
    }


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        return Objects.hash (this.menus, this.parameters, this.tracks, this.vuLeft, this.vuRight);
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if (!(obj instanceof final TrackVolumeComponent other))
            return false;
        return this.menus.equals (other.menus) && this.parameters.equals (other.parameters) && this.tracks.equals (other.tracks) && this.vuLeft.equals (other.vuLeft) && this.vuRight.equals (other.vuRight);
    }
}
