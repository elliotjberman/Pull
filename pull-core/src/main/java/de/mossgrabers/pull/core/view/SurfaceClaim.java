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
     * Claim types. The stable-adapter variants make the migration boundary explicit: the core
     * selects and validates the owning view, while an initialization-owned shell adapter still
     * realizes that input or output.
     */
    public enum Kind
    {
        /** Input delivered by a permanent feature-specific shell route. */
        DIRECT_INPUT,
        /** Input owned by the selected view but realized by its stable mechanical adapter. */
        STABLE_ADAPTER_INPUT,
        /** Input observed alongside stable behavior. */
        OBSERVE_INPUT,
        /** Input owned by the reloadable core instead of stable behavior. */
        EXCLUSIVE_INPUT,
        /** Replayable hardware output rendered directly by the reloadable core. */
        OUTPUT,
        /** Hardware output rendered by the selected view's stable mechanical adapter. */
        STABLE_ADAPTER_OUTPUT;


        /**
         * Test whether this claim receives input events.
         *
         * @return True for any input claim
         */
        public boolean isInput ()
        {
            return this != OUTPUT && this != STABLE_ADAPTER_OUTPUT;
        }


        /**
         * Test whether this claim owns, rather than merely observes, input.
         *
         * @return True for direct, stable-adapter, or exclusive input
         */
        public boolean ownsInput ()
        {
            return this == DIRECT_INPUT || this == STABLE_ADAPTER_INPUT || this == EXCLUSIVE_INPUT;
        }


        /**
         * Test whether this claim owns hardware output.
         *
         * @return True for core or stable-adapter output
         */
        public boolean ownsOutput ()
        {
            return this == OUTPUT || this == STABLE_ADAPTER_OUTPUT;
        }


        /**
         * Test whether this claim requires a stable mechanical adapter.
         *
         * @return True for stable-adapter input or output
         */
        public boolean requiresStableAdapter ()
        {
            return this == STABLE_ADAPTER_INPUT || this == STABLE_ADAPTER_OUTPUT;
        }
    }
}
