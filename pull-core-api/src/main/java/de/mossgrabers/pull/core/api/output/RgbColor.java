// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;

/**
 * Hardware-independent RGB color.
 *
 * @param red Red channel, 0 through 255
 * @param green Green channel, 0 through 255
 * @param blue Blue channel, 0 through 255
 */
public record RgbColor (int red, int green, int blue)
{
    /**
     * Validate channel values.
     */
    public RgbColor
    {
        validateChannel (red, "red");
        validateChannel (green, "green");
        validateChannel (blue, "blue");
    }


    private static void validateChannel (final int value, final String name)
    {
        if (value < 0 || value > 255)
            throw new IllegalArgumentException (name + " must be between 0 and 255");
    }
}
