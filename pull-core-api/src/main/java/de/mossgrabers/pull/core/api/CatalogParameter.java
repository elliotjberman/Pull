// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/**
 * One armed parameter published by the stable shell.
 *
 * @param targetId Opaque target identity
 * @param pageName Owning parameter-page name
 * @param name Parameter name
 * @param normalizedValue Authoritative normalized value in the range {@code [0, 1]}
 */
public record CatalogParameter (ParameterTargetId targetId, String pageName, String name, double normalizedValue)
{
    /** Validate and copy values. */
    public CatalogParameter
    {
        targetId = Objects.requireNonNull (targetId, "targetId");
        pageName = Objects.requireNonNull (pageName, "pageName");
        name = Objects.requireNonNull (name, "name");
        if (!Double.isFinite (normalizedValue) || normalizedValue < 0 || normalizedValue > 1)
            throw new IllegalArgumentException ("normalizedValue must be finite and in [0, 1]");
    }
}
