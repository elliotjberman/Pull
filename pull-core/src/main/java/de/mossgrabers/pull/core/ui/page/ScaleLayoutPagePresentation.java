// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import java.util.List;

/** The six layout families and the separately observed orientation. */
public record ScaleLayoutPagePresentation (boolean available, List<String> layouts, int selected, boolean vertical)
{
    public ScaleLayoutPagePresentation { layouts = List.copyOf (layouts); if (layouts.size () > 6) throw new IllegalArgumentException ("Six layout families maximum"); }
}
