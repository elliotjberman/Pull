// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import java.util.List;

/** Scale list, twelve root names, range and chromatic state read back by the consuming view. */
public record ScalesPagePresentation (boolean available, List<String> scales, int selectedScale, List<String> roots, int selectedRoot, boolean chromatic, String range)
{
    public ScalesPagePresentation
    {
        scales = List.copyOf (scales); roots = List.copyOf (roots);
        if (scales.size () > 128 || roots.size () != 12) throw new IllegalArgumentException ("Scales require twelve root names and at most 128 scale names");
    }
}
