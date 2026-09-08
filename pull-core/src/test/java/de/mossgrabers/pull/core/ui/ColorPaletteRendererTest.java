// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui;

import de.mossgrabers.pull.core.api.ColorPaletteSnapshot;
import de.mossgrabers.pull.core.api.output.PadGridPosition;
import de.mossgrabers.pull.core.api.output.RgbColor;
import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

class ColorPaletteRendererTest
{
    @Test
    void palettePagesStartAtPhysicalBottomLeftAndClearEveryUnusedPad ()
    {
        final var colors = IntStream.range (0, 70).mapToObj (index -> new RgbColor (index + 1, 50, 100)).toList ();
        final var first = ColorPaletteRenderer.pads (new ColorPaletteSnapshot (0, colors));
        assertEquals (64, first.size ());
        assertEquals (colors.get (0), first.get (new PadGridPosition (0, 0)));
        assertEquals (colors.get (63), first.get (new PadGridPosition (7, 7)));
        final var second = ColorPaletteRenderer.pads (new ColorPaletteSnapshot (1, colors));
        assertEquals (colors.get (64), second.get (new PadGridPosition (0, 0)));
        assertEquals (colors.get (69), second.get (new PadGridPosition (5, 0)));
        assertEquals (new RgbColor (0, 0, 0), second.get (new PadGridPosition (6, 0)));
        assertEquals (58, second.values ().stream ().filter (color -> color.equals (new RgbColor (0, 0, 0))).count ());
    }

    @Test
    void unavailableObservationClearsTheCompletePalette ()
    {
        final var output = ColorPaletteRenderer.pads (ColorPaletteSnapshot.empty ());
        assertEquals (64, output.size ());
        assertTrue (output.values ().stream ().allMatch (color -> color.equals (new RgbColor (0, 0, 0))));
    }
}
