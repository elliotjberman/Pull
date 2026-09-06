// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/** One observed track in the controller model's bounded current bank. */
public record CurrentTrackSnapshot (SessionTrackSnapshot track, boolean groupExpanded, double vuLeft, double vuRight)
{
    public CurrentTrackSnapshot
    {
        track = Objects.requireNonNull (track, "track");
        if (!Double.isFinite (vuLeft) || !Double.isFinite (vuRight) || vuLeft < 0 || vuLeft > 1 || vuRight < 0 || vuRight > 1)
            throw new IllegalArgumentException ("track VU values must be normalized");
        if (!track.exists () && (groupExpanded || vuLeft != 0 || vuRight != 0))
            throw new IllegalArgumentException ("unavailable tracks cannot contain state");
    }

    public static CurrentTrackSnapshot empty ()
    {
        return new CurrentTrackSnapshot (SessionTrackSnapshot.empty (), false, 0, 0);
    }
}
