// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.ui.component.ParameterValue;
import de.mossgrabers.pull.core.ui.component.ResponseCurve;

/** Configuration content below the common tabs, with fixed physical encoder columns. */
final class SetupPageStyle
{
    static final double LABEL_TOP = 42;
    static final double LABEL_HEIGHT = 18;
    static final double VALUE_TOP = 61;
    static final double RING_CENTER_Y = 121;
    static final ParameterValue.Style VALUE = new ParameterValue.Style (MacroPageStyle.CONTENT_WIDTH, 28, 26, 12, 64, 2, 14, 24, DisplayTextFit.SHRINK_ELLIPSIS);
    static final double CURVE_LEFT = 380;
    static final double CURVE_TOP = 84;
    static final ResponseCurve.Style CURVE = new ResponseCurve.Style (90, 34, 1);

    private SetupPageStyle () { }
}
