// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/** Lightweight authoritative state for the currently visible project tab. */
public record ProjectSnapshot (boolean available, String projectIdentity, String projectName, boolean engineActive, boolean canPrevious, boolean canNext, boolean commandPending)
{
    private static final ProjectSnapshot EMPTY = new ProjectSnapshot (false, "", "", false, false, false, false);


    /** Validate project state. */
    public ProjectSnapshot
    {
        projectIdentity = Objects.requireNonNullElse (projectIdentity, "");
        projectName = Objects.requireNonNullElse (projectName, "");
        if (available && projectIdentity.isBlank ())
            throw new IllegalArgumentException ("available project must have an identity");
    }


    /** Get unavailable project state. */
    public static ProjectSnapshot empty ()
    {
        return EMPTY;
    }
}
