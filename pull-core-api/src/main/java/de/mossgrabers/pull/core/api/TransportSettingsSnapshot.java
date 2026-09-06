// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import java.util.Objects;

/** Bounded authoritative current-project transport settings. */
public record TransportSettingsSnapshot (String projectIdentity, boolean tickPlaybackEnabled, PreRoll preRoll, boolean metronomeDuringPreRoll)
{
    private static final TransportSettingsSnapshot EMPTY = new TransportSettingsSnapshot ("", false, PreRoll.NONE, false);

    public TransportSettingsSnapshot
    {
        projectIdentity = Objects.requireNonNull (projectIdentity, "projectIdentity");
        preRoll = Objects.requireNonNull (preRoll, "preRoll");
        if (projectIdentity.isBlank () && (tickPlaybackEnabled || preRoll != PreRoll.NONE || metronomeDuringPreRoll))
            throw new IllegalArgumentException ("unavailable transport settings must be empty");
    }

    public boolean available () { return !this.projectIdentity.isBlank (); }
    public static TransportSettingsSnapshot empty () { return EMPTY; }
}
