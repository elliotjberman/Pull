// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ApplicationUiContext;
import java.util.Objects;

/** One native application UI request with an exact observed origin. */
public record SetMixerBooleanEffect (ApplicationUiContext context, Property property, boolean enabled) implements CoreEffect
{
    public enum Property { CLIP_LAUNCHER_VISIBLE, IO_SECTION_VISIBLE, CROSS_FADE_VISIBLE, DEVICE_SECTION_VISIBLE, METER_SECTION_VISIBLE, SEND_SECTION_VISIBLE }

    public SetMixerBooleanEffect
    {
        context = Objects.requireNonNull (context, "context");
        property = Objects.requireNonNull (property, "property");
    }
}
