// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui;

import de.mossgrabers.pull.core.api.ColorPaletteSnapshot;
import de.mossgrabers.pull.core.api.output.PadGridPosition;
import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.LinkedHashMap;
import java.util.Map;

/** Complete physical pad palette; first supplied color starts at the bottom left. */
public final class ColorPaletteRenderer
{
    private static final RgbColor OFF = new RgbColor (0, 0, 0);
    private ColorPaletteRenderer () { }

    public static Map<PadGridPosition, RgbColor> pads (final ColorPaletteSnapshot state)
    {
        final Map<PadGridPosition, RgbColor> result = new LinkedHashMap<> (64);
        for (int index = 0; index < 64; index++)
        {
            final int paletteIndex = state.page () * 64 + index;
            result.put (new PadGridPosition (index % 8, index / 8), paletteIndex < state.colors ().size () ? state.colors ().get (paletteIndex) : OFF);
        }
        return Map.copyOf (result);
    }
}
