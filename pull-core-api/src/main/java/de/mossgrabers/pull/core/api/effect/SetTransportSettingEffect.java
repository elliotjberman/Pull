// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/** Submit one absolute boolean transport-setting write to the exact current project. */
public record SetTransportSettingEffect (String projectIdentity, TransportSetting setting, boolean enabled) implements CoreEffect
{
    public SetTransportSettingEffect
    {
        projectIdentity = Objects.requireNonNull (projectIdentity, "projectIdentity");
        setting = Objects.requireNonNull (setting, "setting");
        if (projectIdentity.isBlank ()) throw new IllegalArgumentException ("projectIdentity must not be blank");
    }
}
