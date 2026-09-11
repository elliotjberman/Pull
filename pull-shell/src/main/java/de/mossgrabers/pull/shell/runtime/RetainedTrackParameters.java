// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.parameter.IParameter;

import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Initialization-owned, UUID-addressed track mix resources supplied by the shared cursor pool. */
interface RetainedTrackParameters
{
    RetainedTrackParameters UNAVAILABLE = new RetainedTrackParameters ()
    {
        @Override public void requestTracks (final Set<String> trackIds) { }
        @Override public TrackMix lookup (final String trackId) { return null; }
    };

    /** Complete demand from the parameter consumer, including outstanding exact cleanup targets. */
    void requestTracks (Set<String> trackIds);

    /** Return only a subsequently observed ready assignment; lookup never submits acquisition. */
    TrackMix lookup (String trackId);

    /** The fence must recheck the live project, assignment generation and retained track UUID. */
    record TrackMix (String trackId, long assignmentGeneration, IParameter volume, IParameter pan, BooleanSupplier addressable)
    {
        public TrackMix
        {
            trackId = Objects.requireNonNull (trackId, "trackId");
            if (trackId.isBlank () || assignmentGeneration < 1)
                throw new IllegalArgumentException ("A retained track mix requires a ready identity and generation");
            volume = Objects.requireNonNull (volume, "volume");
            pan = Objects.requireNonNull (pan, "pan");
            addressable = Objects.requireNonNull (addressable, "addressable");
        }

        IParameter parameter (final int index)
        {
            return index == 0 ? this.volume : this.pan;
        }
    }
}
