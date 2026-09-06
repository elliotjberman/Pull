// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/** Absolute write to one installed boolean controller preference. */
public record SetControllerBooleanSettingEffect (Setting setting, boolean enabled) implements CoreEffect
{
    public enum Setting { VU_METERS, ACCENT_ENABLED }
    public SetControllerBooleanSettingEffect { setting = Objects.requireNonNull (setting, "setting"); }
}
