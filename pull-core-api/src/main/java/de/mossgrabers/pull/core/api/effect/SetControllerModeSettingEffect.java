// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/** Remember a mode in one installed controller preference; this does not select that mode. */
public record SetControllerModeSettingEffect (Setting setting, String modeId) implements CoreEffect
{
    public enum Setting { GLOBAL_MIX_MODE }
    public SetControllerModeSettingEffect
    {
        setting = Objects.requireNonNull (setting, "setting");
        modeId = Objects.requireNonNull (modeId, "modeId");
        if (modeId.isBlank ()) throw new IllegalArgumentException ("mode setting must identify a mode");
    }
}
