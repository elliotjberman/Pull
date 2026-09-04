// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Map;
import java.util.Objects;


/** Authoritative Bitwig target facts keyed by permanent semantic mapping endpoint. */
public record ControllerMappingFeedbackSnapshot (boolean available, Map<ControllerMappingId, ControllerMappingTarget> targets)
{
    /** Maximum feedback endpoints accepted across the parent-loaded API. */
    public static final int CAPACITY = DesiredControllerMappings.CAPACITY;

    private static final ControllerMappingFeedbackSnapshot EMPTY = new ControllerMappingFeedbackSnapshot (false, Map.of ());


    /** Validate and copy one complete bounded snapshot. */
    public ControllerMappingFeedbackSnapshot
    {
        targets = Map.copyOf (Objects.requireNonNull (targets, "targets"));
        if (targets.size () > CAPACITY)
            throw new IllegalArgumentException ("controller mapping feedback exceeds the installed API capacity");
        if (!available && !targets.isEmpty ())
            throw new IllegalArgumentException ("unavailable controller mapping feedback must be empty");
    }


    /** Test whether the installed inventory contains one semantic endpoint. */
    public boolean supports (final ControllerMappingId mappingId)
    {
        return this.available && this.targets.containsKey (Objects.requireNonNull (mappingId, "mappingId"));
    }


    /** Get unavailable controller-mapping feedback. */
    public static ControllerMappingFeedbackSnapshot empty ()
    {
        return EMPTY;
    }
}
