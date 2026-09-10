// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/** Later host read-back of the requested clip window. Ready requires two coherent host samples. */
public record ClipScanSnapshot (long targetGeneration, String channelId, int sceneCount, int sceneStart, int pageSize, boolean ready)
{
    public ClipScanSnapshot
    {
        channelId = Objects.requireNonNull (channelId, "channelId");
        if (targetGeneration < 0 || sceneCount < 0 || sceneStart < 0 || pageSize < 0)
            throw new IllegalArgumentException ("Invalid clip scan observation");
        if (ready && (targetGeneration == 0 || channelId.isEmpty () || pageSize == 0))
            throw new IllegalArgumentException ("Ready clip scans require a target and window");
    }

    public boolean matches (final DesiredClipScan request)
    {
        return request.active () && this.targetGeneration == request.targetGeneration () && this.channelId.equals (request.channelId ());
    }

    public static ClipScanSnapshot empty ()
    {
        return new ClipScanSnapshot (0, "", 0, 0, 0, false);
    }
}
