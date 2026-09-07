// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.ui.PageStyle;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;

/** Info's aligned identity fields and configuration navigation cells. */
final class InfoPageStyle
{
    static final ChoiceCell.Style CHOICE = new ChoiceCell.Style (PageStyle.COLUMN_WIDTH - 2, 34, 4, 2, 17, 10, PageStyle.DARK);
    static final double INSET = 12;
    static final double LABEL_TOP = 62;
    static final double LABEL_HEIGHT = 22;
    static final double LABEL_FONT = 14;
    static final double VALUE_TOP = 86;
    static final double VALUE_HEIGHT = 36;
    static final double VALUE_FONT = 24;
    static final double MINIMUM_FONT = 12;
    static final double STATUS_TOP = 70;
    static final double STATUS_HEIGHT = 42;
    static final double STATUS_FONT = 20;

    private InfoPageStyle () { }
}
