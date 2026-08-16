// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/** Stable semantic identity of one permanent Bitwig controller-mapping endpoint. */
public record ControllerMappingId (String value)
{
    /** Validate and normalize the semantic identifier. */
    public ControllerMappingId
    {
        value = Objects.requireNonNull (value, "value").strip ();
        if (value.isEmpty ())
            throw new IllegalArgumentException ("controller mapping ID must not be blank");
        if (value.length () > 128)
            throw new IllegalArgumentException ("controller mapping ID exceeds 128 characters");
    }
}
