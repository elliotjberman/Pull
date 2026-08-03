// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Locale;
import java.util.Objects;


/**
 * Stable physical identifiers installed by the bounded Push input canopy.
 *
 * <p>Symbolic names match the public framework identifiers, for example {@code PLAY},
 * {@code KNOB1}, and {@code TOUCHSTRIP}. Input kind remains separate, so one continuous control ID
 * can expose both motion and touch routes.</p>
 */
public final class PushControlIds
{
    /** Channel-pressure input shared by the pad grid. */
    public static final ControlId CHANNEL_PRESSURE = new ControlId ("push.pressure.channel");

    /** Sustain-pedal input on MIDI CC 64. */
    public static final ControlId SUSTAIN_PEDAL = new ControlId ("push.pedal.sustain");


    private PushControlIds ()
    {
        // Utility class
    }


    /**
     * Get a physical Push button ID.
     *
     * @param symbolicName Stable symbolic button name, such as {@code PLAY}
     * @return Control ID
     */
    public static ControlId button (final String symbolicName)
    {
        return symbolic ("push.button.", symbolicName);
    }


    /**
     * Get a one-based physical grid-pad ID.
     *
     * @param oneBasedIndex Pad index from 1 through 64
     * @return Control ID
     */
    public static ControlId pad (final int oneBasedIndex)
    {
        if (oneBasedIndex < 1 || oneBasedIndex > 64)
            throw new IllegalArgumentException ("Push pad index must be between 1 and 64");
        return new ControlId ("push.pad." + oneBasedIndex);
    }


    /**
     * Get a physical Push continuous-control ID.
     *
     * @param symbolicName Stable symbolic name, such as {@code KNOB1} or {@code TOUCHSTRIP}
     * @return Control ID
     */
    public static ControlId continuous (final String symbolicName)
    {
        return symbolic ("push.continuous.", symbolicName);
    }


    private static ControlId symbolic (final String prefix, final String symbolicName)
    {
        final String value = Objects.requireNonNull (symbolicName, "symbolicName").trim ();
        if (value.isEmpty ())
            throw new IllegalArgumentException ("symbolicName must not be blank");
        for (int index = 0; index < value.length (); index++)
        {
            final char character = value.charAt (index);
            if (!(character >= 'A' && character <= 'Z' || character >= '0' && character <= '9' || character == '_'))
                throw new IllegalArgumentException ("symbolicName must contain only A-Z, 0-9, and underscore");
        }
        return new ControlId (prefix + value.toLowerCase (Locale.ROOT).replace ('_', '-'));
    }
}
