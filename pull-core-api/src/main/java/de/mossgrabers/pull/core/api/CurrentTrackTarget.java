// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/** Exact observed slot within one installed current-track bank window. */
public record CurrentTrackTarget (long generation, String bankId, int trackIndex, String channelId)
{
    public CurrentTrackTarget
    {
        bankId = Objects.requireNonNull (bankId, "bankId");
        channelId = Objects.requireNonNull (channelId, "channelId");
        if (generation <= 0 || bankId.isBlank () || channelId.isBlank () || trackIndex < 0 || trackIndex >= CurrentTrackBankSnapshot.CAPACITY)
            throw new IllegalArgumentException ("current-track target requires an observed eight-slot bank identity");
    }
}
