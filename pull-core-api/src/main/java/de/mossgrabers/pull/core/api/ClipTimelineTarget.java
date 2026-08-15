// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/**
 * Exact bounded launcher-clip target observed by the stable shell.
 *
 * @param generation Monotonic generation of the installed clip window
 * @param selectedTrackGeneration Generation of the private selection-following target
 * @param trackId Stable Bitwig channel identity
 * @param sceneIndex Absolute launcher scene index
 */
public record ClipTimelineTarget (long generation, long selectedTrackGeneration, String trackId, int sceneIndex)
{
    /** Validate the exact target identity. */
    public ClipTimelineTarget
    {
        if (generation <= 0)
            throw new IllegalArgumentException ("generation must be positive");
        if (selectedTrackGeneration < 0)
            throw new IllegalArgumentException ("selectedTrackGeneration must not be negative");
        trackId = Objects.requireNonNull (trackId, "trackId");
        if (trackId.isBlank ())
            throw new IllegalArgumentException ("trackId must not be blank");
        if (sceneIndex < 0)
            throw new IllegalArgumentException ("sceneIndex must not be negative");
    }
}
