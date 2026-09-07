// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.utils;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import de.mossgrabers.framework.controller.color.ColorEx;


/**
 * Helper class for string methods.
 *
 * @author Jürgen Moßgraber
 */
public class StringUtils
{
    private static final char [] REMOVABLE_CHARS =
    {
        ' ',
        'e',
        'a',
        'u',
        'i',
        'o'
    };


    /**
     * Constructor, private due to help class.
     */
    private StringUtils ()
    {
        // Intentionally empty
    }


    /**
     * Replaces some non-ASCII characters which are not in the default font which is used for
     * drawing text on graphics displays.
     *
     * @param text The string to check
     * @return The string with replaced characters
     */
    public static String fixFontCharacters (final String text)
    {
        if (text == null)
            return "";
        final StringBuilder str = new StringBuilder ();
        for (int i = 0; i < text.length (); i++)
        {
            final char c = text.charAt (i);
            if (c == '♯')
                str.append ("#");
            else
                str.append (c);
        }
        return str.toString ();
    }


    /**
     * Shortens a text to the given length.
     *
     * @param text The text to shorten
     * @param length The length to shorten to
     * @return The shortened text
     */
    public static String optimizeName (final String text, final int length)
    {
        if (text == null)
            return "";

        String shortened = text;
        for (final char element: REMOVABLE_CHARS)
        {
            if (shortened.length () <= length)
                return shortened;
            int pos;
            while ((pos = shortened.indexOf (element)) != -1)
            {
                shortened = shortened.substring (0, pos) + shortened.substring (pos + 1, shortened.length ());
                if (shortened.length () <= length)
                    return shortened;
            }
        }
        return shortened.length () <= length ? shortened : shortened.substring (0, length);
    }


    /**
     * Limits a text to the given length.
     *
     * @param text The text to limit
     * @param length The length to limit to
     * @return The limited text
     */
    public static String limit (final String text, final int length)
    {
        if (text == null)
            return "";
        return text.length () <= length ? text : text.substring (0, length);
    }


    /**
     * Convert the bytes to a hex string.
     *
     * @param data The data to convert
     * @return The hex string
     */
    public static String toHexStr (final int [] data)
    {
        return toHexStr (data, true);
    }


    /**
     * Convert the bytes to a hex string.
     *
     * @param data The data to convert
     * @param addSpace True to add a space character after each hex number
     * @return The hex string
     */
    public static String toHexStr (final int [] data, final boolean addSpace)
    {
        final StringBuilder sysex = new StringBuilder ();
        for (final int d: data)
        {
            sysex.append (toHexStr (d));
            if (addSpace)
                sysex.append (' ');
        }
        return sysex.toString ();
    }


    /**
     * Convert the byte to a hex string
     *
     * @param number The value to convert
     * @return The hex string
     */
    public static String toHexStr (final int number)
    {
        return String.format ("%02X", Integer.valueOf (number));
    }


    /**
     * Convert a string with hex encoded bytes. One byte is 2 characters without any spaces.
     *
     * @param data The data to convert
     * @return The parsed byte array
     */
    public static int [] fromHexStr (final String data)
    {
        final int length = data.length ();
        if (length % 2 != 0)
            throw new IllegalArgumentException ("Length of hex data must be a multiple of 2!");

        final int size = length / 2;
        final int [] result = new int [size];
        for (int i = 0; i < size; i++)
        {
            final int pos = i * 2;
            result[i] = Integer.parseInt (data.substring (pos, pos + 2), 16);
        }
        return result;
    }


    /**
     * Format a velocity percentage.
     *
     * @param noteVelocity The velocity in the range of 0..1.
     * @return The formatted velocity
     */
    public static String formatPercentage (final double noteVelocity)
    {
        final DecimalFormat df = new DecimalFormat ("0", DecimalFormatSymbols.getInstance (Locale.ENGLISH));
        df.setMaximumFractionDigits (1);
        return df.format (Double.valueOf (noteVelocity * 100.0)) + "%";
    }


    /**
     * Format the given time as measure.quarters.eights / measure.quarters.eights.ticks.
     *
     * @param quartersPerMeasure The number of quarters of a measure
     * @param beats The beats to format
     * @param startOffset An offset that is added to the measure, quarter and eights values
     * @param includeFrames Add the frames (ticks) if true
     * @return The formatted text
     */
    public static String formatMeasures (final int quartersPerMeasure, final double beats, final int startOffset, final boolean includeFrames)
    {
        return formatMeasures (quartersPerMeasure, beats, startOffset, includeFrames, "%d.%d.%d", "%d.%d.%d:%03d");
    }


    /**
     * Format the given time as measure.quarters.eights / measure.quarters.eights.ticks. Padded to 3
     * / 2 digits.
     *
     * @param quartersPerMeasure The number of quarters of a measure
     * @param beats The beats to format
     * @param startOffset An offset that is added to the measure, quarter and eights values
     * @param includeFrames Add the frames (ticks) if true
     * @return The formatted text
     */
    public static String formatMeasuresLong (final int quartersPerMeasure, final double beats, final int startOffset, final boolean includeFrames)
    {
        return formatMeasures (quartersPerMeasure, beats, startOffset, includeFrames, "%03d.%d.%d", "%d.%02d.%02d:%02d");
    }


    /**
     * Format the given time as minutes.seconds / minutes.seconds.millis. Padded to 3 / 2 digits.
     *
     * @param tempo The tempo
     * @param beats The beats to format as time
     * @param includeFrames Add the frames (ticks) if true
     * @return The formatted text
     */
    public static String formatTimeLong (final double tempo, final double beats, final boolean includeFrames)
    {
        return formatTime (tempo, beats, includeFrames, "%02d.%02d.%02d", "%d.%02d.%02d:%03d");
    }


    private static String formatMeasures (final int quartersPerMeasure, final double beats, final int startOffset, final boolean includeFrames, final String shortFormat, final String longFormat)
    {
        final int measure = (int) Math.floor (beats / quartersPerMeasure);
        double t = beats - measure * quartersPerMeasure;
        final int quarters = (int) Math.floor (t); // :1
        t = t - quarters; // *1
        final int eights = (int) Math.floor (t / 0.25);

        if (!includeFrames)
            return String.format (shortFormat, Integer.valueOf (measure + startOffset), Integer.valueOf (quarters + startOffset), Integer.valueOf (eights + startOffset));

        t = t - eights * 0.25;
        final int frames = (int) Math.floor (t / 0.25 * 100.0);
        return String.format (longFormat, Integer.valueOf (measure + startOffset), Integer.valueOf (quarters + startOffset), Integer.valueOf (eights + startOffset), Integer.valueOf (frames));
    }


    private static String formatTime (final double tempo, final double beats, final boolean includeFrames, final String shortFormat, final String longFormat)
    {
        final double time = beats * 60.0 / tempo;

        final int seconds = (int) Math.floor (time % 60);
        double t = (time - seconds) / 60.0;
        final int minutes = (int) Math.floor (t % 60);
        t = (t - minutes) / 60.0;
        final int hours = (int) Math.floor (t);

        if (!includeFrames)
            return String.format (shortFormat, Integer.valueOf (minutes), Integer.valueOf (seconds));

        final int millis = (int) ((time - ((hours * 60 + minutes) * 60 + seconds)) * 1000);
        return String.format (longFormat, Integer.valueOf (hours), Integer.valueOf (minutes), Integer.valueOf (seconds), Integer.valueOf (millis));
    }


    /**
     * Format the color as a 3 byte hex number, e.g. FFFFFF.
     *
     * @param color THe color to format
     * @return The formatted color
     */
    public static String formatColor (final ColorEx color)
    {
        return toHexStr (color.toIntRGB255 (), false);
    }
}
