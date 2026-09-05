// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/** Current project's unified Automation Write state and the user's touch-release preference. */
public record AutomationSnapshot (String projectIdentity, boolean writingEnabled, boolean stopOnTouchRelease)
{
    private static final AutomationSnapshot EMPTY = new AutomationSnapshot ("", false, false);


    public AutomationSnapshot
    {
        projectIdentity = Objects.requireNonNull (projectIdentity, "projectIdentity");
        if (projectIdentity.isBlank () && (writingEnabled || stopOnTouchRelease))
            throw new IllegalArgumentException ("unavailable automation state must be empty");
    }


    public boolean available ()
    {
        return !this.projectIdentity.isBlank ();
    }


    public static AutomationSnapshot empty ()
    {
        return EMPTY;
    }
}
