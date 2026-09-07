// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import java.util.Objects;

/** Observed hardware identity, formatted by the feature without retaining a hardware object. */
public record InfoPagePresentation (boolean hardwareAvailable, String firmware, String boardRevision, String serialNumber)
{
    public InfoPagePresentation
    {
        firmware = Objects.requireNonNullElse (firmware, "");
        boardRevision = Objects.requireNonNullElse (boardRevision, "");
        serialNumber = Objects.requireNonNullElse (serialNumber, "");
    }
}
