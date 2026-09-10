// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/** One requested page of the private selected-track clip window; an empty ID relinquishes it. */
public record DesiredClipScan (long targetGeneration, String channelId, int sceneStart)
{
    public DesiredClipScan
    {
        channelId = Objects.requireNonNull (channelId, "channelId");
        if (targetGeneration < 0 || sceneStart < 0 || !channelId.isEmpty () && targetGeneration == 0)
            throw new IllegalArgumentException ("Invalid clip scan target or page");
        if (channelId.isEmpty () && (targetGeneration != 0 || sceneStart != 0))
            throw new IllegalArgumentException ("Inactive clip scans must be empty");
    }

    public boolean active ()
    {
        return !this.channelId.isEmpty ();
    }

    public static DesiredClipScan inactive ()
    {
        return new DesiredClipScan (0, "", 0);
    }
}
