// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller.grid;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.color.ColorManager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Tests the shared pad-color resolution boundary. */
class PadGridImplTest
{
    @Test
    void resolvesEveryPadColorKindImmediatelyBeforeCachingTheLight ()
    {
        final TrackingColorManager colors = new TrackingColorManager ();
        final PadGridImpl grid = new PadGridImpl (colors, null);
        final ColorEx paleYellow = ColorEx.fromRGB (254, 254, 170);

        grid.light (36, PadColor.rgb (paleYellow));
        grid.light (37, PadColor.registered ("TEST_COLOR"));
        grid.light (38, 12);

        assertSame (paleYellow, colors.lastRgb);
        assertEquals (43, grid.getLightInfo (36).getColor ());
        assertEquals (7, grid.getLightInfo (37).getColor ());
        assertEquals (12, grid.getLightInfo (38).getColor ());
    }


    @Test
    void resolvesMainAndBlinkColorsThroughTheSameBoundary ()
    {
        final TrackingColorManager colors = new TrackingColorManager ();
        final PadGridImpl grid = new PadGridImpl (colors, null);

        grid.lightEx (0, 0, PadColor.registered ("TEST_COLOR"), PadColor.rgb (ColorEx.WHITE), true);

        final LightInfo light = grid.getLightInfo (92);
        assertEquals (7, light.getColor ());
        assertEquals (43, light.getBlinkColor ());
        assertTrue (light.isFast ());
    }


    @Test
    void cachesResolvedRgbByPhysicalPad ()
    {
        final TrackingColorManager colors = new TrackingColorManager ();
        final PadGridImpl grid = new PadGridImpl (colors, null);

        grid.light (36, PadColor.rgb (ColorEx.fromRGB (254, 254, 170)));
        grid.light (36, PadColor.rgb (ColorEx.fromRGB (254, 254, 170)));
        assertEquals (1, colors.rgbResolutions);

        grid.light (36, PadColor.rgb (ColorEx.WHITE));
        assertEquals (2, colors.rgbResolutions);
    }


    @Test
    void keepsCoreOffDistinctFromDawBlack ()
    {
        final TrackingColorManager colors = new TrackingColorManager ();
        final PadGridImpl grid = new PadGridImpl (colors, null);

        grid.light (36, PadColor.rgbOrOff (ColorEx.BLACK));
        grid.light (37, PadColor.rgb (ColorEx.BLACK));

        assertEquals (0, grid.getLightInfo (36).getColor ());
        assertEquals (43, grid.getLightInfo (37).getColor ());
    }


    private static final class TrackingColorManager extends ColorManager
    {
        private ColorEx lastRgb;
        private int     rgbResolutions;


        private TrackingColorManager ()
        {
            this.registerColorIndex (IPadGrid.GRID_OFF, 0);
            this.registerColorIndex ("TEST_COLOR", 7);
        }


        /** {@inheritDoc} */
        @Override
        public int getColorIndex (final ColorEx color)
        {
            this.lastRgb = color;
            this.rgbResolutions++;
            return 43;
        }
    }
}
