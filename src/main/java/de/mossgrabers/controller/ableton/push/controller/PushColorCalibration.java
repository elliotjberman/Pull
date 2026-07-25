// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;


/**
 * Color compensation applied at the two Push 2 hardware output boundaries.
 */
final class PushColorCalibration
{
    private static final int CHROMA_THRESHOLD   = 8;
    private static final int LED_SATURATION     = 512;
    private static final int LED_BRIGHTNESS     = 192;
    private static final int DISPLAY_SATURATION = 333;
    private static final int DISPLAY_BRIGHTNESS = 215;


    private PushColorCalibration ()
    {
        // Utility class
    }


    static int [] toLedRGB (final int [] color)
    {
        final int calibrated = calibrate (color[0], color[1], color[2], LED_SATURATION, LED_BRIGHTNESS);
        return new int []
        {
            calibrated >> 16 & 0xFF,
            calibrated >> 8 & 0xFF,
            calibrated & 0xFF
        };
    }


    static int toDisplayRGB (final int red, final int green, final int blue)
    {
        return calibrate (red & 0xFF, green & 0xFF, blue & 0xFF, DISPLAY_SATURATION, DISPLAY_BRIGHTNESS);
    }


    private static int calibrate (final int red, final int green, final int blue, final int saturation, final int brightness)
    {
        final int maximum = Math.max (red, Math.max (green, blue));
        final int minimum = Math.min (red, Math.min (green, blue));
        if (maximum - minimum < CHROMA_THRESHOLD)
            return red << 16 | green << 8 | blue;

        final int luminance = (54 * red + 183 * green + 19 * blue) >> 8;
        final int calibratedRed = calibrateChannel (red, luminance, saturation, brightness);
        final int calibratedGreen = calibrateChannel (green, luminance, saturation, brightness);
        final int calibratedBlue = calibrateChannel (blue, luminance, saturation, brightness);
        return calibratedRed << 16 | calibratedGreen << 8 | calibratedBlue;
    }


    private static int calibrateChannel (final int channel, final int luminance, final int saturation, final int brightness)
    {
        final int saturated = luminance + (channel - luminance) * saturation / 256;
        return Math.max (0, Math.min (255, saturated * brightness / 256));
    }
}
