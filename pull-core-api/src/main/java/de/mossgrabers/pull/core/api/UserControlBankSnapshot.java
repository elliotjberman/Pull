// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.List;
import java.util.Objects;


/** Authoritative values of the fixed four-slot Bitwig user-control bank. */
public record UserControlBankSnapshot (boolean available, List<Double> values)
{
    /** Fixed bank capacity installed during extension initialization. */
    public static final int CAPACITY = 4;

    private static final UserControlBankSnapshot EMPTY = new UserControlBankSnapshot (false, List.of (0.0, 0.0, 0.0, 0.0));


    /** Validate and copy the bounded values. */
    public UserControlBankSnapshot
    {
        values = List.copyOf (Objects.requireNonNull (values, "values"));
        if (values.size () != CAPACITY)
            throw new IllegalArgumentException ("user-control bank must contain exactly four values");
        for (final Double value: values)
        {
            if (value == null || !Double.isFinite (value.doubleValue ()) || value.doubleValue () < 0 || value.doubleValue () > 1)
                throw new IllegalArgumentException ("user-control values must be finite and normalized");
        }
    }


    /** Get an unavailable bank. */
    public static UserControlBankSnapshot empty ()
    {
        return EMPTY;
    }
}
