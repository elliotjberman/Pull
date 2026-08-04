// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.graphics.canvas.component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.daw.resource.ChannelType;
import de.mossgrabers.framework.daw.resource.DeviceTypes;
import de.mossgrabers.framework.graphics.IGraphicsContext;
import de.mossgrabers.framework.graphics.IGraphicsDimensions;
import de.mossgrabers.framework.graphics.IGraphicsInfo;
import de.mossgrabers.framework.graphics.canvas.component.LabelComponent.LabelLayout;


/**
 * An element in the grid which contains a fader and text for a value.
 *
 * Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
 *
 * @author Jürgen Moßgraber
 */
public class ParameterComponent extends MenuComponent
{
    private static final ColorEx METER_BACKGROUND = ColorEx.BLACK;
    private static final ColorEx METER_OFF        = ColorEx.fromRGB (20, 54, 65);
    private static final ColorEx METER_ON         = ColorEx.fromRGB (132, 214, 255);
    private static final ColorEx METER_TEXT       = ColorEx.fromRGB (190, 235, 247);

    private static final double  LABEL_FONT_SIZE  = 12.5;
    private static final double  LABEL_BASELINE   = 31.0;
    private static final double  VALUE_FONT_SIZE  = 30.0;
    private static final double  UNIT_FONT_SIZE   = 14.0;
    private static final double  VALUE_BASELINE   = 64.0;
    private static final double  CONTENT_LEFT     = 8.0;
    private static final double  VALUE_FIELD_WIDTH = 64.0;
    private static final double  VALUE_UNIT_GAP   = 2.0;
    private static final double  RING_DIAMETER    = 50.0;
    private static final double  RING_DOT_RADIUS  = 1.1;
    private static final double  RING_START       = 220.0;
    private static final double  RING_SWEEP       = -260.0;
    private static final int     RING_STEPS       = 220;
    private static final double  TOGGLE_WIDTH     = 66.0;
    private static final double  TOGGLE_HEIGHT    = 32.0;
    private static final double  TOGGLE_INSET     = 1.4;
    private static final double  TOGGLE_THUMB_GAP = 5.0;
    private static final double  TOGGLE_THUMB_RADIUS = 10.0;

    private static final Pattern VALUE_UNIT_PATTERN = Pattern.compile ("^(.+?)(?:\\s*)(%|dB|kHz|Hz|ms|sec|s|st|ct|BPM|x)$");

    private final String  paramName;
    private final String  paramValueText;
    private final int     paramValue;
    private final int     modulatedParamValue;
    private final boolean isTouched;


    /**
     * Constructor. A generic parameter.
     *
     * @param menuName The text for the menu
     * @param isMenuSelected True if the menu is selected
     * @param name The of the grid element (track name, parameter name, etc.)
     * @param color The color to use for the header, may be null
     * @param isSelected True if the grid element is selected
     * @param paramName The name of the parameter
     * @param paramValue The value of the fader
     * @param modulatedParamValue The modulated value of the fader, -1 if not modulated
     * @param paramValueText The textual form of the faders value
     * @param isTouched True if touched
     */
    public ParameterComponent (final String menuName, final boolean isMenuSelected, final String name, final ColorEx color, final boolean isSelected, final String paramName, final int paramValue, final int modulatedParamValue, final String paramValueText, final boolean isTouched)
    {
        this (menuName, isMenuSelected, name, (String) null, color, isSelected, paramName, paramValue, modulatedParamValue, paramValueText, isTouched);
    }


    /**
     * Constructor. A parameter with a device footer.
     *
     * @param menuName The text for the menu
     * @param isMenuSelected True if the menu is selected
     * @param name The of the grid element (track name, parameter name, etc.)
     * @param deviceName The name of the device
     * @param color The color to use for the header, may be null
     * @param isSelected True if the grid element is selected
     * @param paramName The name of the parameter
     * @param paramValue The value of the fader
     * @param modulatedParamValue The modulated value of the fader, -1 if not modulated
     * @param paramValueText The textual form of the faders value
     * @param isTouched True if touched
     */
    public ParameterComponent (final String menuName, final boolean isMenuSelected, final String name, final String deviceName, final ColorEx color, final boolean isSelected, final String paramName, final int paramValue, final int modulatedParamValue, final String paramValueText, final boolean isTouched)
    {
        this (menuName, isMenuSelected, name, deviceName, color, isSelected, paramName, paramValue, modulatedParamValue, paramValueText, isTouched, LabelLayout.COLORED);
    }


    /**
     * Constructor. A parameter with a device footer.
     *
     * @param menuName The text for the menu
     * @param isMenuSelected True if the menu is selected
     * @param name The of the grid element (track name, parameter name, etc.)
     * @param deviceName The name of the device
     * @param color The color to use for the header, may be null
     * @param isSelected True if the grid element is selected
     * @param paramName The name of the parameter
     * @param paramValue The value of the fader
     * @param modulatedParamValue The modulated value of the fader, -1 if not modulated
     * @param paramValueText The textual form of the faders value
     * @param isTouched True if touched
     * @param lowerLayout The layout for the lower label
     */
    public ParameterComponent (final String menuName, final boolean isMenuSelected, final String name, final String deviceName, final ColorEx color, final boolean isSelected, final String paramName, final int paramValue, final int modulatedParamValue, final String paramValueText, final boolean isTouched, final LabelLayout lowerLayout)
    {
        super (menuName, isMenuSelected, name, deviceName == null ? null : DeviceTypes.getIconId (deviceName), color, isSelected, true, lowerLayout);

        this.paramName = paramName;
        this.paramValue = paramValue;
        this.modulatedParamValue = modulatedParamValue;
        this.paramValueText = paramValueText;
        this.isTouched = isTouched;
    }


    /**
     * Constructor. A parameter with a channel footer.
     *
     * @param menuName The text for the menu
     * @param isMenuSelected True if the menu is selected
     * @param name The of the grid element (track name, parameter name, etc.)
     * @param type The type of the channel
     * @param color The color to use for the header, may be null
     * @param isSelected True if the grid element is selected
     * @param paramName The name of the parameter
     * @param paramValue The value of the fader
     * @param modulatedParamValue The modulated value of the fader, -1 if not modulated
     * @param paramValueText The textual form of the faders value
     * @param isTouched True if touched
     */
    public ParameterComponent (final String menuName, final boolean isMenuSelected, final String name, final ChannelType type, final ColorEx color, final boolean isSelected, final String paramName, final int paramValue, final int modulatedParamValue, final String paramValueText, final boolean isTouched)
    {
        super (menuName, isMenuSelected, name, ChannelSelectComponent.getIcon (type, false), color, isSelected, true, LabelLayout.TRACK);

        this.paramName = paramName;
        this.paramValue = paramValue;
        this.modulatedParamValue = modulatedParamValue;
        this.paramValueText = paramValueText;
        this.isTouched = isTouched;
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

        final boolean isValueMissing = this.paramValue == -1;
        final boolean isModulated = this.modulatedParamValue != -1;
        final double intensity = this.isTouched ? 1.0 : 0.5;
        final ColorEx meterOff = METER_OFF.dim (intensity);
        final ColorEx meterOn = METER_ON.dim (intensity);
        final ColorEx meterText = METER_TEXT.dim (intensity);

        gc.fillRectangle (left, 0, width, height, METER_BACKGROUND);

        // Component is off if the name is empty
        if (this.paramName == null || this.paramName.length () == 0)
        {
            this.drawFooter (info);
            return;
        }

        gc.drawTextAt (this.paramName, left + CONTENT_LEFT, LABEL_BASELINE, meterText, LABEL_FONT_SIZE);
        this.drawValue (gc, left, meterText);

        final double centerY = 105.0;
        final double ringRadius = RING_DIAMETER / 2.0;

        if (this.isButtonValue ())
            this.drawToggleState (gc, left + CONTENT_LEFT + TOGGLE_WIDTH / 2.0, centerY, meterOn);
        else if (!isValueMissing)
        {
            final double maxValue = dimensions.getParameterUpperBound ();
            final double value = isModulated ? this.modulatedParamValue : this.paramValue;
            final double valueRatio = Math.max (0, Math.min (1, value / maxValue));
            final double centerX = left + CONTENT_LEFT + ringRadius;

            this.drawRing (gc, centerX, centerY, ringRadius, RING_SWEEP, meterOff);
            this.drawRing (gc, centerX, centerY, ringRadius, RING_SWEEP * valueRatio, meterOn);
        }

        this.drawFooter (info);
    }


    private void drawValue (final IGraphicsContext gc, final double left, final ColorEx color)
    {
        if (this.paramValueText == null || this.paramValueText.length () == 0)
            return;

        final Matcher matcher = VALUE_UNIT_PATTERN.matcher (this.paramValueText.trim ());
        if (matcher.matches ())
        {
            final String value = matcher.group (1).trim ();
            final String unit = matcher.group (2);

            gc.drawTextAt (value, left + CONTENT_LEFT, VALUE_BASELINE, color, VALUE_FONT_SIZE);
            gc.drawTextAt (unit, left + CONTENT_LEFT + VALUE_FIELD_WIDTH + VALUE_UNIT_GAP, VALUE_BASELINE, color, UNIT_FONT_SIZE);
            return;
        }

        gc.drawTextAt (this.paramValueText, left + CONTENT_LEFT, VALUE_BASELINE, color, VALUE_FONT_SIZE);
    }


    private void drawFooter (final IGraphicsInfo info)
    {
        final double footerHeight = 2 * info.getDimensions ().getMenuHeight ();
        this.footer.draw (info.withBounds (info.getBounds ().height () - footerHeight, footerHeight));
    }


    private boolean isButtonValue ()
    {
        final String text = this.paramValueText == null ? "" : this.paramValueText.trim ();
        return "On".equalsIgnoreCase (text) || "Off".equalsIgnoreCase (text);
    }


    private boolean isButtonOn ()
    {
        return this.paramValueText != null && "On".equalsIgnoreCase (this.paramValueText.trim ());
    }


    private void drawToggleState (final IGraphicsContext gc, final double centerX, final double centerY, final ColorEx color)
    {
        final double left = centerX - TOGGLE_WIDTH / 2.0;
        final double top = centerY - TOGGLE_HEIGHT / 2.0;
        final double radius = TOGGLE_HEIGHT / 2.0;
        final boolean isOn = this.isButtonOn ();
        final double thumbX = isOn ? left + TOGGLE_WIDTH - TOGGLE_THUMB_GAP - TOGGLE_THUMB_RADIUS : left + TOGGLE_THUMB_GAP + TOGGLE_THUMB_RADIUS;
        final double thumbY = top + TOGGLE_HEIGHT / 2.0;

        gc.fillRoundedRectangle (left, top, TOGGLE_WIDTH, TOGGLE_HEIGHT, radius, color);

        if (isOn)
        {
            gc.fillCircle (thumbX, thumbY, TOGGLE_THUMB_RADIUS, METER_BACKGROUND);
            return;
        }

        gc.fillRoundedRectangle (left + TOGGLE_INSET, top + TOGGLE_INSET, TOGGLE_WIDTH - 2 * TOGGLE_INSET, TOGGLE_HEIGHT - 2 * TOGGLE_INSET, radius - TOGGLE_INSET, METER_BACKGROUND);
        gc.fillCircle (thumbX, thumbY, TOGGLE_THUMB_RADIUS, color);
        gc.fillCircle (thumbX, thumbY, TOGGLE_THUMB_RADIUS - TOGGLE_INSET, METER_BACKGROUND);
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


    /** {@inheritDoc} */
    @Override
    public int hashCode ()
    {
        final int prime = 31;
        int result = super.hashCode ();
        result = prime * result + (this.isTouched ? 1231 : 1237);
        result = prime * result + this.modulatedParamValue;
        result = prime * result + (this.paramName == null ? 0 : this.paramName.hashCode ());
        result = prime * result + this.paramValue;
        result = prime * result + (this.paramValueText == null ? 0 : this.paramValueText.hashCode ());
        return result;
    }


    /** {@inheritDoc} */
    @Override
    public boolean equals (final Object obj)
    {
        if (this == obj)
            return true;
        if (!super.equals (obj) || this.getClass () != obj.getClass ())
            return false;
        final ParameterComponent other = (ParameterComponent) obj;
        if (this.isTouched != other.isTouched || this.modulatedParamValue != other.modulatedParamValue)
            return false;
        if (this.paramName == null)
        {
            if (other.paramName != null)
                return false;
        }
        else if (!this.paramName.equals (other.paramName))
            return false;
        if (this.paramValue != other.paramValue)
            return false;
        if (this.paramValueText == null)
        {
            if (other.paramValueText != null)
                return false;
        }
        else if (!this.paramValueText.equals (other.paramValueText))
            return false;
        return true;
    }
}
