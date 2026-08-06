// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ColorPaletteEntryTest
{
    @Test
    void writesAndValidatesCompletePaletteValue ()
    {
        final ColorPaletteEntry entry = new ColorPaletteEntry (43, new int []
        {
            255,
            254,
            112
        }, 81);

        assertArrayEquals (new int []
        {
            0x03,
            43,
            127,
            1,
            126,
            1,
            112,
            0,
            81,
            0
        }, entry.createUpdateMessage ());

        final int [] response = new int [17];
        response[6] = 0x04;
        response[7] = 43;
        System.arraycopy (entry.createUpdateMessage (), 2, response, 8, 8);
        assertTrue (entry.matches (response));

        response[14] = 80;
        assertFalse (entry.matches (response));

        response[14] = 81;
        response[7] = 44;
        assertFalse (entry.matches (response));
    }
}
