// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

/** Native attributes, addressed independently for every selected note cell. */
public enum NoteParameterRole
{
    DURATION, MUTE, VELOCITY, VELOCITY_SPREAD, RELEASE_VELOCITY, CHANCE,
    OCCURRENCE, RECURRENCE_LENGTH, GAIN, PAN, TRANSPOSE, TIMBRE, PRESSURE,
    REPEAT_COUNT, REPEAT_CURVE, REPEAT_VELOCITY_CURVE, REPEAT_VELOCITY_END;

    public ParameterSlot slot (final int note)
    {
        if (note < 0 || note >= ParameterSlot.NOTE_CAPACITY) throw new IllegalArgumentException ("note index outside installed window");
        return new ParameterSlot (ParameterBankId.NOTE, note * values ().length + this.ordinal ());
    }
}
