// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;

/** Track/device kinds and observed shortcut names with caller-resolved colors. */
public record AddTrackPagePresentation (List<Kind> kinds, String selectedKind, String primaryAction, RgbColor selectedColor, List<String> shortcuts)
{
    public record Kind (int column, String label, RgbColor color) { }
    public AddTrackPagePresentation
    {
        kinds = List.copyOf (kinds); shortcuts = List.copyOf (shortcuts);
        if (kinds.size () > 8 || kinds.stream ().anyMatch (kind -> kind.column () < 0 || kind.column () > 7) || shortcuts.size () > 7) throw new IllegalArgumentException ("Add chooser exceeds its display footprint");
    }
}
