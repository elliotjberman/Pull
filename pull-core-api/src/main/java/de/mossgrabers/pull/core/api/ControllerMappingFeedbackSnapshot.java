// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Map;
import java.util.Objects;


/** Authoritative Bitwig Boolean feedback keyed by permanent semantic mapping endpoint. */
public record ControllerMappingFeedbackSnapshot (boolean available, Map<ControllerMappingId, Boolean> states)
{
    /** Maximum feedback endpoints accepted across the parent-loaded API. */
    public static final int CAPACITY = DesiredControllerMappings.CAPACITY;

    private static final ControllerMappingFeedbackSnapshot EMPTY = new ControllerMappingFeedbackSnapshot (false, Map.of ());


    /** Validate and copy one complete bounded snapshot. */
    public ControllerMappingFeedbackSnapshot
    {
        states = Map.copyOf (Objects.requireNonNull (states, "states"));
        if (states.size () > CAPACITY)
            throw new IllegalArgumentException ("controller mapping feedback exceeds the installed API capacity");
        if (!available && !states.isEmpty ())
            throw new IllegalArgumentException ("unavailable controller mapping feedback must be empty");
    }


    /** Test whether the installed inventory contains one semantic endpoint. */
    public boolean supports (final ControllerMappingId mappingId)
    {
        return this.available && this.states.containsKey (Objects.requireNonNull (mappingId, "mappingId"));
    }


    /** Get one supported endpoint's Boolean state, defaulting unsupported or unavailable to off. */
    public boolean isOn (final ControllerMappingId mappingId)
    {
        return Boolean.TRUE.equals (this.states.get (Objects.requireNonNull (mappingId, "mappingId")));
    }


    /** Get unavailable controller-mapping feedback. */
    public static ControllerMappingFeedbackSnapshot empty ()
    {
        return EMPTY;
    }
}
