// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Map;
import java.util.Objects;


/** Authoritative Bitwig target facts keyed by permanent semantic mapping endpoint. */
public record ControllerMappingFeedbackSnapshot (boolean available, Map<ControllerMappingId, ControllerMappingTarget> targets, ControllerMappingStorageSnapshot storage)
{
    /** Maximum feedback endpoints accepted across the parent-loaded API. */
    public static final int CAPACITY = CoreControllerMappings.TRACK_CONTROL_PADS.size () + CoreControllerMappings.DRUM_CONTROL_PADS.size ();

    private static final ControllerMappingFeedbackSnapshot EMPTY = new ControllerMappingFeedbackSnapshot (false, Map.of ());


    /** Create target feedback without an observed document storage slot. */
    public ControllerMappingFeedbackSnapshot (final boolean available, final Map<ControllerMappingId, ControllerMappingTarget> targets)
    {
        this (available, targets, ControllerMappingStorageSnapshot.empty ());
    }


    /** Validate and copy one complete bounded snapshot. */
    public ControllerMappingFeedbackSnapshot
    {
        targets = Map.copyOf (Objects.requireNonNull (targets, "targets"));
        storage = Objects.requireNonNull (storage, "storage");
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
