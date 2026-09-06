// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import java.util.List;
import java.util.Objects;

/** Available macro labels and normalized read-back values, with no live parameter identities. */
public record MacroPagePresentation (List<Control> controls)
{
    public MacroPagePresentation
    {
        controls = List.copyOf (controls);
        if (controls.size () > PageStyle.COLUMNS || controls.stream ().map (Control::column).distinct ().count () != controls.size ())
            throw new IllegalArgumentException ("Macro columns must be unique and bounded");
    }

    public enum Widget { RING, TOGGLE_ON, TOGGLE_OFF }

    public record Control (int column, String label, String displayedValue, double value, Widget widget, boolean touched)
    {
        public Control
        {
            if (column < 0 || column >= PageStyle.COLUMNS) throw new IllegalArgumentException ("Macro column outside display");
            Objects.requireNonNull (label, "label");
            Objects.requireNonNull (displayedValue, "displayedValue");
            Objects.requireNonNull (widget, "widget");
            if (!Double.isFinite (value) || value < 0 || value > 1) throw new IllegalArgumentException ("Macro value must be normalized");
        }
    }
}
