// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/** Absolute write to one bounded integer controller preference. */
public record SetControllerIntegerSettingEffect (Setting setting, int value) implements CoreEffect
{
    public enum Setting { MIX_SEND_OFFSET, ACCENT_VELOCITY }
    public SetControllerIntegerSettingEffect
    {
        setting = Objects.requireNonNull (setting, "setting");
        switch (setting)
        {
            case MIX_SEND_OFFSET -> { if (value < 0 || value > 4) throw new IllegalArgumentException ("mix send offset must be between zero and four"); }
            case ACCENT_VELOCITY -> { if (value < 1 || value > 127) throw new IllegalArgumentException ("accent velocity must be between one and 127"); }
        }
    }
}
