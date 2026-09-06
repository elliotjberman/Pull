// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.MixerControlDisplay;
import de.mossgrabers.pull.core.api.output.RgbColor;

/** Shared mixer typography, widget geometry and meter thresholds. */
public final class MixerControlStyle
{
    public static final double  COLUMN_WIDTH           = MixerControlDisplay.WIDTH;
    public static final double  CONTENT_LEFT           = 8.0;
    public static final double  LABEL_TOP              = 18.0;
    public static final double  LABEL_HEIGHT           = 20.0;
    public static final double  LABEL_FONT_SIZE        = 15.0;
    public static final double  LABEL_MIN_FONT_SIZE    = 9.0;
    public static final double  VALUE_TOP              = 36.0;
    public static final double  VALUE_HEIGHT           = 24.0;
    public static final double  VALUE_BASELINE         = 55.0;
    public static final double  VALUE_FONT_SIZE        = 19.0;
    public static final double  VALUE_MIN_FONT_SIZE    = 11.0;
    public static final double  UNIT_FONT_SIZE         = 8.5;
    public static final double  VALUE_FIELD_WIDTH      = 58.0;
    public static final double  VALUE_UNIT_GAP         = 3.0;
    public static final double  PAN_VALUE_TOP           = 38.0;
    public static final double  PAN_VALUE_HEIGHT        = 30.0;
    public static final double  PAN_VALUE_BASELINE      = 64.0;
    public static final double  PAN_VALUE_FONT_SIZE     = 30.0;
    public static final double  PAN_VALUE_MIN_FONT_SIZE = 12.0;
    public static final double  PAN_UNIT_FONT_SIZE      = 14.0;
    public static final double  PAN_VALUE_FIELD_WIDTH   = 64.0;
    public static final double  CONTROL_CENTER_Y       = 106.0;
    public static final double  PAN_SLIDER_WIDTH       = 82.0;
    public static final double  PAN_RAIL_HEIGHT        = 4.0;
    public static final double  PAN_MARKER_WIDTH       = 3.0;
    public static final double  PAN_MARKER_HEIGHT      = 16.0;
    public static final double  KNOB_RING_RADIUS       = 25.0;
    public static final double  KNOB_DOT_RADIUS        = 1.1;
    public static final double  KNOB_START             = 220.0;
    public static final double  KNOB_SWEEP             = -260.0;
    public static final int     KNOB_STEPS             = 200;
    public static final double  FADER_TOP              = 60.0;
    public static final double  FADER_HEIGHT           = 80.0;
    public static final double  METER_WIDTH            = 24.0;
    public static final double  METER_GAP              = 4.0;
    public static final double  FADER_RAIL_LEFT        = 69.0;
    public static final double  FADER_LINE_WIDTH       = 2.0;
    public static final double  FADER_MARKER_WIDTH     = 6.0;
    public static final double  METER_ORANGE_START     = 0.75;
    public static final double  METER_RED_START        = 0.90;

    public static final RgbColor WHITE       = new RgbColor (255, 255, 255);
    public static final RgbColor DIM_WHITE   = new RgbColor (102, 102, 102);
    public static final RgbColor DARKER_GRAY = new RgbColor (63, 63, 63);
    public static final RgbColor GREEN       = new RgbColor (0, 255, 0);
    public static final RgbColor ORANGE      = new RgbColor (255, 80, 0);
    public static final RgbColor RED         = new RgbColor (255, 0, 0);
    public static final RgbColor PROJECT_MACRO = new RgbColor (132, 214, 255);

    private MixerControlStyle () { }
}
