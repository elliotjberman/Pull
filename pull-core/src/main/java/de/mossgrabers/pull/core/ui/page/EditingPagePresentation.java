// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import java.util.List;

/** Resolved editing page content; input policy never enters the renderer. */
public record EditingPagePresentation (List<ChoiceCell> upper, List<ChoiceCell> lower, List<Control> controls,
    TrackFooterPresentation footer, List<Label> labels, String message)
{
    public enum Kind { VALUE, RING, TOGGLE }
    public record Control (int column, String name, String value, double position, Kind kind, boolean touched) { }
    public record Label (int column, boolean bottom, String text) { }
    public EditingPagePresentation
    {
        upper = List.copyOf (upper); lower = List.copyOf (lower); controls = List.copyOf (controls); labels = List.copyOf (labels);
        if (upper.size () > 8 || lower.size () > 8 || controls.size () > 8) throw new IllegalArgumentException ("Editing page exceeds eight columns");
    }
}
