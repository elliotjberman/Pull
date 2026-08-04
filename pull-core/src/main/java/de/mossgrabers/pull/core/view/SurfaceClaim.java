// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import java.util.Objects;


/**
 * One view's fixed use of a Push 2 region.
 *
 * @param area Fixed surface region
 * @param kind Input/output ownership kind
 */
public record SurfaceClaim (SurfaceArea area, Kind kind)
{
    /**
     * Validate a claim.
     */
    public SurfaceClaim
    {
        area = Objects.requireNonNull (area, "area");
        kind = Objects.requireNonNull (kind, "kind");
    }


    /**
     * Claim types. Direct input is the same exclusive ownership rule as routed input, but its
     * permanent shell route does not appear in {@code DesiredInputRoutes}.
     */
    public enum Kind
    {
        /** Input delivered by a permanent feature-specific shell route. */
        DIRECT_INPUT,
        /** Input observed alongside stable behavior. */
        OBSERVE_INPUT,
        /** Input owned by the reloadable core instead of stable behavior. */
        EXCLUSIVE_INPUT,
        /** Replayable hardware output. */
        OUTPUT;


        /**
         * Test whether this claim receives input events.
         *
         * @return True for any input claim
         */
        public boolean isInput ()
        {
            return this != OUTPUT;
        }


        /**
         * Test whether this claim owns, rather than merely observes, input.
         *
         * @return True for direct or exclusive input
         */
        public boolean ownsInput ()
        {
            return this == DIRECT_INPUT || this == EXCLUSIVE_INPUT;
        }
    }
}
