// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.ui.PageStyle;

import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;
import java.util.Objects;

/** Current-bank menu and aligned visible controls; independent of banks, slots and navigation state. */
public record GlobalMixerPagePresentation (List<MenuItem> menu, List<Control> controls)
{
    public GlobalMixerPagePresentation
    {
        menu = List.copyOf (menu);
        controls = List.copyOf (controls);
        if (controls.size () > PageStyle.COLUMNS || controls.stream ().map (Control::column).distinct ().count () != controls.size ())
            throw new IllegalArgumentException ("Mixer columns must be unique and bounded");
        if (menu.size () != PageStyle.COLUMNS) throw new IllegalArgumentException ("Mixer menu needs eight columns");
    }

    public enum Widget { VOLUME, PAN, SEND_VOLUME, RING }
    public record MenuItem (String text, boolean selected, boolean arrow)
    {
        public MenuItem { Objects.requireNonNull (text, "text"); }
    }
    public record Control (int column, Widget widget, String displayedValue, double value, boolean active,
        RgbColor color, double vuLeft, double vuRight)
    {
        public Control
        {
            if (column < 0 || column >= PageStyle.COLUMNS) throw new IllegalArgumentException ("Mixer column outside display");
            Objects.requireNonNull (widget, "widget");
            Objects.requireNonNull (displayedValue, "displayedValue");
            Objects.requireNonNull (color, "color");
            if (!Double.isFinite (value) || value < 0 || value > 1 || !Double.isFinite (vuLeft) || vuLeft < 0 || vuLeft > 1 || !Double.isFinite (vuRight) || vuRight < 0 || vuRight > 1)
                throw new IllegalArgumentException ("Mixer values must be normalized");
        }
    }
}
