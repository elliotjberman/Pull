// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.grid.IPadGrid;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;


class PushColorManagerTest
{
    private final PushColorManager colorManager = new PushColorManager ();


    @Test
    void mapsAcceptedColorsToTheirCalibratedPaletteEntries ()
    {
        this.assertMapping ("#C9C9C9", 73, "#E3E5E6");
        this.assertMapping ("#FFFEA1", 43, "#FFFE70");
        this.assertMapping ("#43D2B8", 84, "#2ACAB2");
        this.assertMapping ("#D92F22", 81, "#7C0600");
        this.assertMapping ("#E4B74C", 92, "#B48502");
        this.assertMapping ("#0099D7", 96, "#0072BA");
        this.assertMapping ("#FF833C", 91, "#D84E00");
        this.assertMapping ("#73B453", 118, "#2A8616");
        this.assertMapping ("#DB8A00", 35, "#B13B00");
        this.assertMapping ("#F27E00", 123, "#E02D00");
        this.assertMapping ("#601E00", PushColorManager.PUSH2_COLOR2_RECORD_ARMED_DIM, "#110100");
        this.assertMapping ("#9C8F1D", PushColorManager.PUSH2_COLOR2_YELLOW_DIM_VISIBLE, "#564702");
    }


    @Test
    void distinguishesDynamicBlackContentFromSemanticOff ()
    {
        assertEquals (1, this.colorManager.getColorIndex (ColorEx.BLACK));
        assertArrayEquals (parseRGB ("#040404"), PushColorManager.getPaletteColorRGB (1));

        assertEquals (0, this.colorManager.getColorIndex (IPadGrid.GRID_OFF));
        assertArrayEquals (parseRGB ("#000000"), PushColorManager.getPaletteColorRGB (0));
    }


    @Test
    void includesACompleteWhiteOnlyProfile ()
    {
        assertEquals (128, PushPaletteData.WHITE_VALUES.length);
        assertEquals (0, PushPaletteData.WHITE_VALUES[0]);
        assertEquals (81, PushPaletteData.WHITE_VALUES[43]);
        assertEquals (93, PushPaletteData.WHITE_VALUES[64]);
        assertEquals (128, PushPaletteData.WHITE_VALUES[127]);
    }


    private void assertMapping (final String target, final int expectedIndex, final String expectedProgrammed)
    {
        assertEquals (expectedIndex, this.colorManager.getColorIndex (parseColor (target)));
        assertArrayEquals (parseRGB (expectedProgrammed), PushColorManager.getPaletteColorRGB (expectedIndex));
    }


    private static ColorEx parseColor (final String value)
    {
        final int [] rgb = parseRGB (value);
        return ColorEx.fromRGB (rgb[0], rgb[1], rgb[2]);
    }


    private static int [] parseRGB (final String value)
    {
        return new int []
        {
            Integer.parseInt (value.substring (1, 3), 16),
            Integer.parseInt (value.substring (3, 5), 16),
            Integer.parseInt (value.substring (5, 7), 16)
        };
    }
}
