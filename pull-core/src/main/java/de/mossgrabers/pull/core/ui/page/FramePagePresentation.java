// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import java.util.List;
import java.util.Objects;

/** Observed layout and option selection, independent of host proxies and display commands. */
public record FramePagePresentation (boolean available, Layout layout, List<Boolean> upperSelected)
{
    public enum Layout { ARRANGE, MIX, EDIT, OTHER }

    public FramePagePresentation
    {
        layout = Objects.requireNonNull (layout, "layout");
        upperSelected = List.copyOf (Objects.requireNonNull (upperSelected, "upperSelected"));
        if (upperSelected.size () != 8)
            throw new IllegalArgumentException ("Frame requires eight upper option states");
    }
}
