// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import java.util.Objects;

/** Exact address in the installed Session window, not a persistent clip identity. */
public record SessionLocation (String projectIdentity, long generation, SessionBankShape shape, int trackIndex, String channelId, int scenePosition)
{
    public SessionLocation
    {
        Objects.requireNonNull (projectIdentity, "projectIdentity");
        Objects.requireNonNull (shape, "shape");
        Objects.requireNonNull (channelId, "channelId");
        if (projectIdentity.isBlank () || generation < 0 || !shape.isPresent () || shape.tracks () > 8 || shape.scenes () > 8 || trackIndex < -1 || trackIndex >= shape.tracks () || scenePosition < 0 || (trackIndex == -1 ? !channelId.isEmpty () : channelId.isBlank ()))
            throw new IllegalArgumentException ("Invalid bounded Session location");
    }

    public boolean isScene () { return this.trackIndex == -1; }
}
