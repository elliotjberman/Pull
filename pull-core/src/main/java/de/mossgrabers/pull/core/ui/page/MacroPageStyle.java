// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.RgbColor;

/** Shared visual language of macro and fixed-velocity ring controls. */
public final class MacroPageStyle
{
    public static final double CONTENT_LEFT = 8;
    public static final double LABEL_BASELINE = 31;
    public static final double LABEL_FONT = 12.5;
    public static final double VALUE_BASELINE = 64;
    public static final double VALUE_FONT = 30;
    public static final double RING_CENTER_Y = 105;
    public static final double RING_RADIUS = 25;
    public static final double RING_START = 220;
    public static final double RING_SWEEP = -260;
    public static final int RING_STEPS = 220;
    public static final double RING_DOT_RADIUS = 1.1;
    public static final RgbColor METER_OFF = new RgbColor (20, 54, 65);
    public static final RgbColor METER_ON = new RgbColor (132, 214, 255);
    public static final RgbColor METER_TEXT = new RgbColor (190, 235, 247);

    public static final double UNIT_FONT_SIZE = 14.0;
    public static final double VALUE_FIELD_WIDTH = 64.0;
    public static final double VALUE_UNIT_GAP = 2.0;
    public static final double TOGGLE_WIDTH = 66.0;
    public static final double TOGGLE_HEIGHT = 32.0;
    public static final double TOGGLE_INSET = 1.4;
    public static final double TOGGLE_THUMB_GAP = 5.0;
    public static final double TOGGLE_THUMB_RADIUS = 10.0;

    private MacroPageStyle () { }

    public static RgbColor brightness (final RgbColor color, final boolean touched)
    {
        final double intensity = touched ? 1 : 0.5;
        return new RgbColor ((int) Math.round (color.red () * intensity), (int) Math.round (color.green () * intensity), (int) Math.round (color.blue () * intensity));
    }
}
