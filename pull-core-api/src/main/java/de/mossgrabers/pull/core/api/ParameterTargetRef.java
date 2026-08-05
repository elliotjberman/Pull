// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/**
 * Opaque parameter identity and generation of its current stable actuator.
 *
 * @param domain Stable target domain
 * @param identity Opaque identity within the domain
 * @param generation Monotonic actuator generation
 */
public record ParameterTargetRef (String domain, String identity, long generation)
{
    /**
     * Validate the target reference.
     */
    public ParameterTargetRef
    {
        domain = Objects.requireNonNull (domain, "domain").strip ();
        identity = Objects.requireNonNull (identity, "identity").strip ();
        if (domain.isEmpty () || identity.isEmpty ())
            throw new IllegalArgumentException ("parameter target domain and identity must not be blank");
        if (generation < 0)
            throw new IllegalArgumentException ("parameter target generation must not be negative");
    }
}
