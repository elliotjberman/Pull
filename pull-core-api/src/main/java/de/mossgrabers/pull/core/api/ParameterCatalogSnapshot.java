// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable catalog of shell-armed parameters for the selected track.
 *
 * @param generation Monotonic identity generation
 * @param parameters Ordered armed parameters
 */
public record ParameterCatalogSnapshot (long generation, List<CatalogParameter> parameters)
{
    private static final ParameterCatalogSnapshot EMPTY = new ParameterCatalogSnapshot (0, List.of ());


    /** Validate and copy values. */
    public ParameterCatalogSnapshot
    {
        if (generation < 0)
            throw new IllegalArgumentException ("generation must not be negative");
        parameters = List.copyOf (Objects.requireNonNull (parameters, "parameters"));
        final Set<ParameterTargetId> targets = new HashSet<> ();
        for (final CatalogParameter parameter: parameters)
        {
            if (!targets.add (parameter.targetId ()))
                throw new IllegalArgumentException ("parameter target IDs must be unique");
        }
    }


    /**
     * Get the empty initial catalog.
     *
     * @return Empty catalog
     */
    public static ParameterCatalogSnapshot empty ()
    {
        return EMPTY;
    }
}
