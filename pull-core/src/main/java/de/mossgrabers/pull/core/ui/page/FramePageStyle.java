// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.ui.PageStyle;

import de.mossgrabers.pull.core.ui.component.ChoiceCell;

/** Frame's original large option rows and section headings. */
final class FramePageStyle
{
    static final double OPTION_HEIGHT = 2 * (PageStyle.HEIGHT / 12.0 + 4);
    static final double OPTION_WIDTH = PageStyle.COLUMN_WIDTH - 2;
    static final double LOWER_TOP = PageStyle.HEIGHT - OPTION_HEIGHT;
    static final double OPTIONS_HEADING_TOP = 35;
    static final double LOWER_HEADING_TOP = 80;
    static final double HEADING_WIDTH = 2 * PageStyle.COLUMN_WIDTH;
    static final double HEADING_HEIGHT = 45;
    static final double HEADING_FONT = 22.5;
    static final double HEADING_MIN_FONT = 14;
    static final double OPTION_MIN_FONT = 10;
    static final ChoiceCell.Style CHOICE = new ChoiceCell.Style (OPTION_WIDTH, OPTION_HEIGHT, 6, 0, OPTION_HEIGHT / 2, OPTION_MIN_FONT);

    private FramePageStyle () { }
}
