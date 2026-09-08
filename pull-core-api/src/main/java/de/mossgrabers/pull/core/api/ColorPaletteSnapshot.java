// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;

/** Observed host palette and legacy page, bounded to two physical 64-pad pages. */
public record ColorPaletteSnapshot (int page, List<RgbColor> colors)
{
    private static final ColorPaletteSnapshot EMPTY = new ColorPaletteSnapshot (0, List.of ());

    public ColorPaletteSnapshot
    {
        colors = List.copyOf (colors);
        if (colors.size () > 128) throw new IllegalArgumentException ("Color palette exceeds 128 colors");
        if (page < 0 || page >= Math.max (1, (colors.size () + 63) / 64)) throw new IllegalArgumentException ("Color palette page is out of bounds");
    }

    public static ColorPaletteSnapshot empty () { return EMPTY; }
}
