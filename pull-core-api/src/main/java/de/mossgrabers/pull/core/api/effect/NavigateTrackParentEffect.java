// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/** Navigate the installed main-bank cursor to its observed parent. */
public record NavigateTrackParentEffect (long parentGeneration, String cursorChannelId) implements CoreEffect
{
    public NavigateTrackParentEffect
    {
        cursorChannelId = Objects.requireNonNull (cursorChannelId, "cursorChannelId");
        if (parentGeneration <= 0 || cursorChannelId.isBlank ())
            throw new IllegalArgumentException ("parent navigation requires an observed cursor identity");
    }
}
