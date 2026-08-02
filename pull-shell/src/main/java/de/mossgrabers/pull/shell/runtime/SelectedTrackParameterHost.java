// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ParameterTargetId;

import java.util.List;
import java.util.Objects;


/**
 * Stable-shell boundary for the selected track's dedicated Pull remote-control page.
 */
interface SelectedTrackParameterHost
{
    /**
     * Sample the subscribed Bitwig proxies.
     *
     * @return True when the immutable state changed
     */
    boolean refresh ();


    /**
     * Get the most recently sampled immutable state.
     *
     * @return Current state
     */
    State state ();


    /**
     * Submit one immediate normalized value after revalidating its exact host identity.
     *
     * @param generation Catalog generation that selected the target
     * @param targetId Stable slot identity within that generation
     * @param normalizedValue Normalized value in {@code [0, 1]}
     */
    void setImmediately (long generation, ParameterTargetId targetId, double normalizedValue);


    /**
     * One subscribed remote-control slot.
     *
     * @param targetId Stable physical slot identity
     * @param pageName Owning remote-control page name
     * @param name Parameter name
     * @param exists Whether the slot currently maps to a parameter
     * @param normalizedValue Authoritative normalized value read from Bitwig
     * @param coherent Whether the host-specific barrier confirmed the complete target identity
     */
    record Slot (ParameterTargetId targetId, String pageName, String name, boolean exists, double normalizedValue, boolean coherent)
    {
        /** Validate and copy values. */
        public Slot
        {
            targetId = Objects.requireNonNull (targetId, "targetId");
            pageName = Objects.requireNonNull (pageName, "pageName");
            name = Objects.requireNonNull (name, "name");
            if (!Double.isFinite (normalizedValue) || normalizedValue < 0 || normalizedValue > 1)
                throw new IllegalArgumentException ("normalizedValue must be finite and in [0, 1]");
        }
    }


    /**
     * Complete selected-track remote-control state.
     *
     * @param generation Monotonic structural-identity generation
     * @param trackId Selected Bitwig channel identity, or empty when unavailable
     * @param pageName Currently observed page name
     * @param slots Ordered subscribed slots
     */
    record State (long generation, String trackId, String pageName, List<Slot> slots)
    {
        private static final State EMPTY = new State (0, "", "", List.of ());


        /** Validate and copy values. */
        public State
        {
            if (generation < 0)
                throw new IllegalArgumentException ("generation must not be negative");
            trackId = Objects.requireNonNull (trackId, "trackId");
            pageName = Objects.requireNonNull (pageName, "pageName");
            slots = List.copyOf (Objects.requireNonNull (slots, "slots"));
        }


        /**
         * Get the initial empty state.
         *
         * @return Empty state
         */
        static State empty ()
        {
            return EMPTY;
        }
    }
}
