// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Objects;


/** Authoritative state for the current project and Master controller page. */
public record MasterSnapshot (boolean available, String projectIdentity, String projectName, boolean engineActive, boolean canPrevious, boolean canNext, boolean commandPending, boolean projectDirty, String trackName, RgbColor trackColor, boolean trackActive, boolean trackSelected, boolean cursorPinned, int vuLeft, int vuRight)
{
    private static final MasterSnapshot EMPTY = new MasterSnapshot (false, "", "", false, false, false, false, false, "", new RgbColor (0, 0, 0), false, false, false, 0, 0);


    /** Validate state. */
    public MasterSnapshot
    {
        projectIdentity = Objects.requireNonNullElse (projectIdentity, "");
        projectName = Objects.requireNonNullElse (projectName, "");
        trackName = Objects.requireNonNullElse (trackName, "");
        trackColor = Objects.requireNonNull (trackColor, "trackColor");
        if (vuLeft < 0 || vuRight < 0)
            throw new IllegalArgumentException ("VU values must not be negative");
    }


    /** Get unavailable master state. */
    public static MasterSnapshot empty ()
    {
        return EMPTY;
    }
}
