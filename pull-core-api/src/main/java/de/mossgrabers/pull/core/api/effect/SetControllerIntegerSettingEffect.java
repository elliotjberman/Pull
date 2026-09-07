// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/** Absolute write to one bounded integer controller preference. */
public record SetControllerIntegerSettingEffect (Setting setting, int value) implements CoreEffect
{
    public enum Setting { MIX_SEND_OFFSET, ACCENT_VELOCITY, DISPLAY_BRIGHTNESS, LED_BRIGHTNESS, PAD_SENSITIVITY, PAD_GAIN, PAD_DYNAMICS, RIBBON_FUNCTION, RIBBON_CC, RIBBON_NOTE_REPEAT }
    public SetControllerIntegerSettingEffect
    {
        setting = Objects.requireNonNull (setting, "setting");
        switch (setting)
        {
            case MIX_SEND_OFFSET -> { if (value < 0 || value > 4) throw new IllegalArgumentException ("mix send offset must be between zero and four"); }
            case ACCENT_VELOCITY -> { if (value < 1 || value > 127) throw new IllegalArgumentException ("accent velocity must be between one and 127"); }
            case DISPLAY_BRIGHTNESS, LED_BRIGHTNESS -> { if (value < 0 || value > 100) throw new IllegalArgumentException ("brightness must be between zero and 100"); }
            case RIBBON_FUNCTION -> { if (value < 0 || value > 5) throw new IllegalArgumentException ("ribbon function must be between zero and five"); }
            case RIBBON_CC -> { if (value < 0 || value > 127) throw new IllegalArgumentException ("ribbon CC must be between zero and 127"); }
            case RIBBON_NOTE_REPEAT -> { if (value < 0 || value > 2) throw new IllegalArgumentException ("ribbon repeat must be between zero and two"); }
            case PAD_SENSITIVITY, PAD_GAIN, PAD_DYNAMICS -> { if (value < 0 || value > 10) throw new IllegalArgumentException ("pad preference must be between zero and ten"); }
        }
    }
}
