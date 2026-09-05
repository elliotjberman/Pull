// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;


/** Installed hardware display modes for the controller's single touch strip. */
public enum TouchStripMode
{
    /** No illuminated position. */
    OFF,
    /** Centered pitch-bend indicator. */
    PITCH_BEND,
    /** Unipolar volume indicator. */
    VOLUME,
    /** Bipolar pan indicator. */
    PAN,
    /** Discrete position indicator. */
    DISCRETE
}
