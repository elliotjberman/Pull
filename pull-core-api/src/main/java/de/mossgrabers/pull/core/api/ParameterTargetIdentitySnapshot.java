// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import java.util.Objects;

/** Observed semantic owner/role of a classified parameter proxy; the opaque target reference remains its actuator. */
public record ParameterTargetIdentitySnapshot (String domain, String ownerId, int page, int index, String resourceOwnerId)
{
    public ParameterTargetIdentitySnapshot
    {
        domain = Objects.requireNonNull (domain, "domain");
        ownerId = Objects.requireNonNull (ownerId, "ownerId");
        resourceOwnerId = Objects.requireNonNull (resourceOwnerId, "resourceOwnerId");
        if (domain.isBlank () != ownerId.isBlank () || page < 0 || index < 0)
            throw new IllegalArgumentException ("parameter identity must identify both its domain and owner with non-negative coordinates");
    }

    /** Ordinary project/track roles need no additional retained child owner. */
    public ParameterTargetIdentitySnapshot (final String domain, final String ownerId, final int page, final int index)
    { this (domain, ownerId, page, index, ""); }

    public static ParameterTargetIdentitySnapshot empty () { return new ParameterTargetIdentitySnapshot ("", "", 0, 0); }
}
