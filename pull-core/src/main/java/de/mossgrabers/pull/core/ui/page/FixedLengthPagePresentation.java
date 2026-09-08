// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import java.util.List;

/** Complete observed fixed-length choices; selecting a value is outside this presentation. */
public record FixedLengthPagePresentation (boolean available, List<String> lengths, int selected)
{
    public FixedLengthPagePresentation { lengths = List.copyOf (lengths); if (lengths.size () > 8) throw new IllegalArgumentException ("Eight length choices maximum"); }
}
