// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/**
 * Opaque parameter identity and generation of its current stable actuator.
 *
 * @param kind Stable target kind
 * @param identity Opaque identity within the target kind
 * @param generation Monotonic actuator generation
 */
public record ParameterTargetRef (ParameterTargetKind kind, String identity, long generation)
{
    /**
     * Validate the target reference.
     */
    public ParameterTargetRef
    {
        kind = Objects.requireNonNull (kind, "kind");
        identity = Objects.requireNonNull (identity, "identity").strip ();
        if (identity.isEmpty ())
            throw new IllegalArgumentException ("parameter target identity must not be blank");
        if (generation < 0)
            throw new IllegalArgumentException ("parameter target generation must not be negative");
    }
}
