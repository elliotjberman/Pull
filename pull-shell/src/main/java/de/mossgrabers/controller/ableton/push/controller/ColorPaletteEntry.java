// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

/**
 * A color palette entry of the Push 2/3.
 *
 * @author Jürgen Moßgraber
 */
public class ColorPaletteEntry
{
    private static final int PALETTE_MESSAGE_OUT_ID = 0x03;
    private static final int PALETTE_MESSAGE_IN_ID  = 0x04;

    private static final int MESSAGE_LENGTH         = 17;

    private final int        index;
    private final int        red;
    private final int        green;
    private final int        blue;
    private final int        white;


    /**
     * Constructor.
     *
     * @param index The index of the entry
     * @param color The default palette color consisting of three integers for red, green and blue
     * @param white The parallel white-only palette value
     */
    public ColorPaletteEntry (final int index, final int [] color, final int white)
    {
        this.index = index;
        this.red = color[0];
        this.green = color[1];
        this.blue = color[2];
        this.white = white;
    }


    /**
     * Test if the given data is a valid palette entry message.
     *
     * @param data The data to test
     * @return True if valid
     */
    public static boolean isValid (final int [] data)
    {
        return data.length == MESSAGE_LENGTH && data[6] == PALETTE_MESSAGE_IN_ID;
    }


    /**
     * Test if the received data contains this entry's complete RGB plus white-only value.
     *
     * @param data The SysEx data of a received color palette entry. Must be 17 characters long.
     * @return True if the complete palette value matches
     */
    public boolean matches (final int [] data)
    {
        return isValid (data) && this.index == data[7] && this.red == decode (data, 8) && this.green == decode (data, 10) && this.blue == decode (data, 12) && this.white == decode (data, 14);
    }


    /**
     * Creates a system exclusive message which contains the current color.
     *
     * @return The created message
     */
    public int [] createUpdateMessage ()
    {
        final int [] data = new int [10];
        data[0] = PALETTE_MESSAGE_OUT_ID;
        data[1] = this.index;
        data[2] = this.red % 128;
        data[3] = this.red / 128;
        data[4] = this.green % 128;
        data[5] = this.green / 128;
        data[6] = this.blue % 128;
        data[7] = this.blue / 128;
        data[8] = this.white % 128;
        data[9] = this.white / 128;
        return data;
    }


    private static int decode (final int [] data, final int offset)
    {
        return data[offset] + (data[offset + 1] << 7);
    }
}
