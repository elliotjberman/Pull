// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.ui.component.BipolarSlider;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import de.mossgrabers.pull.core.ui.component.FaderMarker;
import de.mossgrabers.pull.core.ui.component.ParameterValue;
import de.mossgrabers.pull.core.ui.component.RingMeter;
import de.mossgrabers.pull.core.ui.component.VerticalMeter;
import static de.mossgrabers.pull.core.ui.page.MixerPageStyle.*;

/** Global-bank geometry retains its compact meters while consuming the shared controls. */
public final class GlobalMixerPageStyle
{
    public static final ChoiceCell.Style MENU = new ChoiceCell.Style (COLUMN - 2, MENU_HEIGHT - 1, 6, 0, 12, 10);
    public static final double VOLUME_TOP = MENU_HEIGHT + 28;
    public static final double VOLUME_HEIGHT = 160 - 2 * MENU_HEIGHT - VOLUME_TOP - 5;
    public static final VerticalMeter.Style VOLUME_METER = new VerticalMeter.Style (18, VOLUME_HEIGHT);
    public static final FaderMarker.Style VOLUME_FADER = new FaderMarker.Style (VOLUME_HEIGHT, 13, 1, 1);
    public static final VerticalMeter.Style SEND_METER = new VerticalMeter.Style (24, 70);
    public static final FaderMarker.Style SEND_FADER = new FaderMarker.Style (70, 13, 1, 1);
    public static final BipolarSlider.Style PAN = new BipolarSlider.Style (82, 4, 5, 16, 2, 2.5);
    public static final RingMeter.Style RING = new RingMeter.Style (23, 220, -260, 200, 1.1);
    public static final ParameterValue.Style VOLUME_VALUE = new ParameterValue.Style (104, 24, 18, 11, 55, 3, 8.5, 18, DisplayTextFit.SHRINK);
    public static final ParameterValue.Style VALUE = new ParameterValue.Style (104, 24, 19, 11, 58, 3, 8.5, 19, DisplayTextFit.SHRINK);

    private GlobalMixerPageStyle () { }
}
