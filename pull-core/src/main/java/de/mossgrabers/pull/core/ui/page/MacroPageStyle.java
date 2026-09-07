// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.ui.PageStyle;

import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.ui.component.ParameterValue;
import de.mossgrabers.pull.core.ui.component.RingMeter;

/** Shared visual language of macro and fixed-velocity ring controls. */
public final class MacroPageStyle
{
    public static final double CONTENT_LEFT = 8;
    public static final double CONTENT_WIDTH = PageStyle.COLUMN_WIDTH - 2 * CONTENT_LEFT;
    public static final double LABEL_FONT = 12.5;
    public static final double LABEL_TOP = 18;
    public static final double LABEL_HEIGHT = 20;
    public static final double LABEL_MIN_FONT = 9;
    public static final double VALUE_BASELINE = 64;
    public static final double VALUE_FONT = 30;
    public static final double VALUE_TOP = 38;
    public static final double RING_CENTER_Y = 105;
    public static final RingMeter.Style RING = new RingMeter.Style (25, 220, -260, 220, 1.1);
    public static final ParameterValue.Style VALUE = new ParameterValue.Style (CONTENT_WIDTH, 30, VALUE_FONT, 12, 64, 2, 14, VALUE_BASELINE - VALUE_TOP, DisplayTextFit.SHRINK_ELLIPSIS);
    public static final RgbColor METER_OFF = new RgbColor (20, 54, 65);
    public static final RgbColor METER_ON = new RgbColor (132, 214, 255);
    public static final RgbColor METER_TEXT = new RgbColor (190, 235, 247);

    private MacroPageStyle () { }

    public static RgbColor brightness (final RgbColor color, final boolean touched)
    {
        final double intensity = touched ? 1 : 0.5;
        return new RgbColor ((int) Math.round (color.red () * intensity), (int) Math.round (color.green () * intensity), (int) Math.round (color.blue () * intensity));
    }
}
