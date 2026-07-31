// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.graphics.canvas.component;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.daw.resource.ChannelType;
import de.mossgrabers.framework.graphics.Align;
import de.mossgrabers.framework.graphics.IGraphicsContext;
import de.mossgrabers.framework.graphics.IGraphicsDimensions;
import de.mossgrabers.framework.graphics.IGraphicsInfo;
import de.mossgrabers.framework.graphics.canvas.component.LabelComponent.LabelLayout;


/**
 * A compact full-width mixer for the selected track.
 */
public class TrackMixerComponent implements IComponent
{
    /** A menu item above the mixer. */
    public record MenuData (String name, boolean selected)
    {
        // Intentionally empty.
    }


    /** A mixer parameter. */
    public record ParameterData (String name, int value, int modulatedValue, String text, boolean active)
    {
        // Intentionally empty.
    }


    /** A track shown in the footer. */
    public record TrackData (String name, ChannelType type, ColorEx color, boolean selected, boolean active, boolean pinned)
    {
        // Intentionally empty.
    }


    private static final ColorEx BACKGROUND          = ColorEx.BLACK;
    private static final double  CONTENT_LEFT        = 8.0;
    private static final double  LABEL_BASELINE      = 34.0;
    private static final double  LABEL_FONT_SIZE     = 12.5;
    private static final double  VALUE_BASELINE      = 55.0;
    private static final double  VALUE_FONT_SIZE     = 19.0;
    private static final double  UNIT_FONT_SIZE      = 8.5;
    private static final double  VALUE_FIELD_WIDTH   = 58.0;
    private static final double  VALUE_UNIT_GAP      = 3.0;
    private static final double  RING_RADIUS         = 23.0;
    private static final double  RING_CENTER_Y       = 106.0;
    private static final double  RING_DOT_RADIUS     = 1.1;
    private static final double  RING_START          = 220.0;
    private static final double  RING_SWEEP          = -260.0;
    private static final int     RING_STEPS          = 200;
    private static final double  PAN_SLIDER_WIDTH    = 82.0;
    private static final double  FADER_TOP           = 60.0;
    private static final double  FADER_HEIGHT        = 70.0;
    private static final double  METER_WIDTH         = 24.0;
    private static final double  METER_GAP           = 4.0;
    private static final double  FADER_RAIL_LEFT     = 80.0;

    private static final Pattern VALUE_UNIT_PATTERN  = Pattern.compile ("^(.+?)(?:\\s*)(%|dB|kHz|Hz|ms|sec|s|st|ct|BPM|x|L|R)$");

    private final List<MenuData>      menus;
    private final List<ParameterData> parameters;
    private final List<TrackData>     tracks;
    private final int                 vuLeft;
    private final int                 vuRight;
    private final ColorEx             controlColor;


    /**
     * Constructor.
     *
     * @param menus The top-row menu entries
     * @param parameters The selected track parameters
     * @param tracks The tracks shown in the footer
     * @param vuLeft The left-channel meter value
     * @param vuRight The right-channel meter value
     */
    public TrackMixerComponent (final List<MenuData> menus, final List<ParameterData> parameters, final List<TrackData> tracks, final int vuLeft, final int vuRight)
    {
        this (menus, parameters, tracks, vuLeft, vuRight, null);
    }


    /**
     * Constructor with one color for all controls.
     *
     * @param menus The top-row menu entries
     * @param parameters The selected track parameters
     * @param tracks The tracks shown in the footer
     * @param vuLeft The left-channel meter value
     * @param vuRight The right-channel meter value
     * @param controlColor The control color, or null to derive it from the tracks
     */
    public TrackMixerComponent (final List<MenuData> menus, final List<ParameterData> parameters, final List<TrackData> tracks, final int vuLeft, final int vuRight, final ColorEx controlColor)
    {
        this.menus = List.copyOf (menus);
        this.parameters = List.copyOf (parameters);
        this.tracks = List.copyOf (tracks);
        this.vuLeft = vuLeft;
        this.vuRight = vuRight;
        this.controlColor = controlColor;
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
        final double footerHeight = 2 * dimensions.getMenuHeight ();

        gc.fillRectangle (left, 0, width, height, BACKGROUND);

        this.drawMenus (gc, dimensions, left, columnWidth);
        this.drawParameters (gc, dimensions, left, columnWidth);
        this.drawTracks (info, left, columnWidth, height - footerHeight, footerHeight);
    }


    private void drawMenus (final IGraphicsContext gc, final IGraphicsDimensions dimensions, final double left, final double columnWidth)
    {
        final double menuHeight = dimensions.getMenuHeight ();
        ColorEx selectionColor = ColorEx.WHITE;
        if (!this.menus.isEmpty () && "Mix".equals (this.menus.get (0).name ()))
        {
            for (final TrackData track: this.tracks)
            {
                if (track.selected () && track.color () != null)
                {
                    selectionColor = track.color ();
                    break;
                }
            }
        }

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
                gc.fillRectangle (columnLeft, 0, columnWidth - 2, menuHeight - 1, selectionColor);
                textColor = BACKGROUND;
            }
            else
                textColor = ColorEx.WHITE;

            gc.drawTextInBounds (menu.name (), columnLeft + CONTENT_LEFT, 0, columnWidth - 2 * CONTENT_LEFT, menuHeight, Align.LEFT, textColor, 12.0);
        }
    }


    private void drawParameters (final IGraphicsContext gc, final IGraphicsDimensions dimensions, final double left, final double columnWidth)
    {
        final boolean isTrackMix = !this.menus.isEmpty () && "Mix".equals (this.menus.get (0).name ());
        final int count = Math.min (8, this.parameters.size ());
        for (int i = 0; i < count; i++)
        {
            final ParameterData parameter = this.parameters.get (i);
            if (parameter.name () == null || parameter.name ().isBlank ())
                continue;

            final double columnLeft = left + i * columnWidth;
            final ColorEx color = parameter.active () ? ColorEx.WHITE : ColorEx.dimToGray (ColorEx.WHITE);
            final ColorEx controlColor = this.getControlColor (i);
            final ColorEx onColor = parameter.active () ? controlColor : ColorEx.dimToGray (controlColor);
            final ColorEx offColor = parameter.active () ? ColorEx.DARKER_GRAY : ColorEx.evenDarker (ColorEx.DARKER_GRAY);
            if (isTrackMix)
                gc.drawTextAt (parameter.name (), columnLeft + CONTENT_LEFT, LABEL_BASELINE, color, LABEL_FONT_SIZE);
            this.drawValue (gc, columnLeft, parameter.text (), color);

            if (parameter.name ().contains ("Volume"))
                this.drawVolume (gc, dimensions, columnLeft, parameter, onColor, offColor);
            else if ("Pan".equals (parameter.name ()) || parameter.name ().contains ("Panning"))
                this.drawPan (gc, dimensions, columnLeft, parameter, onColor, offColor);
            else
                this.drawRing (gc, dimensions, columnLeft, parameter, onColor, offColor);
        }
    }


    private ColorEx getControlColor (final int index)
    {
        if (this.controlColor != null)
            return this.controlColor;

        if (!this.menus.isEmpty () && "Mix".equals (this.menus.get (0).name ()))
        {
            for (final TrackData track: this.tracks)
            {
                if (track.selected () && track.color () != null)
                    return track.color ();
            }
        }

        if (index < this.tracks.size () && this.tracks.get (index).color () != null)
            return this.tracks.get (index).color ();
        return ColorEx.WHITE;
    }


    private void drawValue (final IGraphicsContext gc, final double left, final String text, final ColorEx color)
    {
        if (text == null || text.isBlank ())
            return;

        final Matcher matcher = VALUE_UNIT_PATTERN.matcher (text.trim ());
        if (matcher.matches ())
        {
            gc.drawTextAt (matcher.group (1).trim (), left + CONTENT_LEFT, VALUE_BASELINE, color, VALUE_FONT_SIZE);
            gc.drawTextAt (matcher.group (2), left + CONTENT_LEFT + VALUE_FIELD_WIDTH + VALUE_UNIT_GAP, VALUE_BASELINE, color, UNIT_FONT_SIZE);
            return;
        }

        gc.drawTextAt (text, left + CONTENT_LEFT, VALUE_BASELINE, color, VALUE_FONT_SIZE);
    }


    private void drawVolume (final IGraphicsContext gc, final IGraphicsDimensions dimensions, final double left, final ParameterData parameter, final ColorEx onColor, final ColorEx offColor)
    {
        final double maxValue = dimensions.getParameterUpperBound ();
        final double leftRatio = Math.max (0, Math.min (1, this.vuLeft / maxValue));
        final double rightRatio = Math.max (0, Math.min (1, this.vuRight / maxValue));
        final double value = parameter.modulatedValue () == -1 ? parameter.value () : parameter.modulatedValue ();
        final double valueRatio = Math.max (0, Math.min (1, value / maxValue));

        this.drawMeter (gc, left + CONTENT_LEFT, leftRatio, onColor, offColor);
        this.drawMeter (gc, left + CONTENT_LEFT + METER_WIDTH + METER_GAP, rightRatio, onColor, offColor);

        final double railX = left + FADER_RAIL_LEFT;
        final double markerY = FADER_TOP + (1 - valueRatio) * FADER_HEIGHT;
        gc.fillRectangle (railX - 12, markerY, 13, 1, onColor);
        gc.fillRectangle (railX, markerY, 1, FADER_TOP + FADER_HEIGHT - markerY, onColor);
    }


    private void drawMeter (final IGraphicsContext gc, final double left, final double ratio, final ColorEx onColor, final ColorEx offColor)
    {
        gc.fillRectangle (left, FADER_TOP, METER_WIDTH, FADER_HEIGHT, offColor);
        final double meterHeight = ratio * FADER_HEIGHT;
        gc.fillRectangle (left, FADER_TOP + FADER_HEIGHT - meterHeight, METER_WIDTH, meterHeight, onColor);
    }


    private void drawPan (final IGraphicsContext gc, final IGraphicsDimensions dimensions, final double left, final ParameterData parameter, final ColorEx onColor, final ColorEx offColor)
    {
        if (parameter.value () == -1)
            return;

        final double value = parameter.modulatedValue () == -1 ? parameter.value () : parameter.modulatedValue ();
        final double ratio = Math.max (0, Math.min (1, value / dimensions.getParameterUpperBound ()));
        BipolarSlider.draw (gc, left + CONTENT_LEFT, RING_CENTER_Y, PAN_SLIDER_WIDTH, ratio, onColor, offColor);
    }


    private void drawRing (final IGraphicsContext gc, final IGraphicsDimensions dimensions, final double left, final ParameterData parameter, final ColorEx onColor, final ColorEx offColor)
    {
        if (parameter.value () == -1)
            return;

        final double value = parameter.modulatedValue () == -1 ? parameter.value () : parameter.modulatedValue ();
        final double ratio = Math.max (0, Math.min (1, value / dimensions.getParameterUpperBound ()));
        final double centerX = left + CONTENT_LEFT + RING_RADIUS;

        this.drawRing (gc, centerX, RING_CENTER_Y, RING_RADIUS, RING_SWEEP, offColor);
        this.drawRing (gc, centerX, RING_CENTER_Y, RING_RADIUS, RING_SWEEP * ratio, onColor);
    }


    private void drawRing (final IGraphicsContext gc, final double centerX, final double centerY, final double radius, final double sweep, final ColorEx color)
    {
        final int steps = Math.max (2, (int) Math.ceil (RING_STEPS * Math.abs (sweep) / Math.abs (RING_SWEEP)));
        for (int i = 0; i <= steps; i++)
        {
            final double angle = Math.toRadians (RING_START + sweep * i / steps);
            gc.fillCircle (centerX + Math.cos (angle) * radius, centerY - Math.sin (angle) * radius, RING_DOT_RADIUS, color);
        }
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
        return Objects.hash (this.menus, this.parameters, this.tracks, Integer.valueOf (this.vuLeft), Integer.valueOf (this.vuRight), this.controlColor);
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if (!(obj instanceof final TrackMixerComponent other))
            return false;
        return this.vuLeft == other.vuLeft && this.vuRight == other.vuRight && this.menus.equals (other.menus) && this.parameters.equals (other.parameters) && this.tracks.equals (other.tracks) && Objects.equals (this.controlColor, other.controlColor);
    }
}
