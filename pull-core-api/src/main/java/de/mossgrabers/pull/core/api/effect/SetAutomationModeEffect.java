// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.AutomationWriteMode;
import java.util.Objects;

/** Submit only the raw automation mode; enabling write is a separate request. */
public record SetAutomationModeEffect (String projectIdentity, AutomationWriteMode mode) implements CoreEffect
{
    public SetAutomationModeEffect
    {
        projectIdentity = Objects.requireNonNull (projectIdentity, "projectIdentity");
        mode = Objects.requireNonNull (mode, "mode");
        if (projectIdentity.isBlank () || mode == AutomationWriteMode.UNKNOWN)
            throw new IllegalArgumentException ("automation mode requires a known project and mode");
    }
}
