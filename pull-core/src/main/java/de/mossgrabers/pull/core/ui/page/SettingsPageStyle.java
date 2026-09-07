// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.ui.component.ChoiceCell;

/** Typography and geometry of pre-roll/automation option cells. */
final class SettingsPageStyle
{
    static final double CONTENT_LEFT = 8;
    static final double CONTENT_WIDTH = 104;
    static final double HEADING_TOP = 82;
    static final double HEADING_FONT = 13;
    static final double TEXT_HEIGHT = 25;
    static final double TEXT_MIN_FONT = 9;
    static final double OPTION_TOP = 108;
    static final double OPTION_HEIGHT = 28;
    static final double LABEL_FONT = 14;
    static final double LABEL_MIN_FONT = 10;
    static final ChoiceCell.Style CHOICE = new ChoiceCell.Style (CONTENT_WIDTH, OPTION_HEIGHT, 6, 2, LABEL_FONT, LABEL_MIN_FONT);
    static final double METRONOME_HEADING_TOP = 25;
    static final double METRONOME_HEADING_FONT = 14;

    private SettingsPageStyle () { }
}
