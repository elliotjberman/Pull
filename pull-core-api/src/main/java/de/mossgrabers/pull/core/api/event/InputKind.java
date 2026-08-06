// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.event;

/**
 * Hardware-independent controller input kinds.
 */
public enum InputKind
{
    /** A discrete non-grid button. */
    BUTTON,

    /** A velocity-sensitive grid pad. */
    PAD,

    /** A relative encoder turn. */
    RELATIVE,

    /** An absolute continuous input such as the ribbon. */
    ABSOLUTE,

    /** A touch sensor associated with another control. */
    TOUCH,

    /** Pressure for one identified grid pad. */
    POLY_PRESSURE,

    /** Aggregate channel pressure. */
    CHANNEL_PRESSURE,

    /** A discrete pedal or footswitch. */
    PEDAL;


    /** Test whether this kind has begin/long/end edge phases. */
    public boolean isEdge ()
    {
        return this == BUTTON || this == PAD || this == TOUCH || this == PEDAL;
    }
}
