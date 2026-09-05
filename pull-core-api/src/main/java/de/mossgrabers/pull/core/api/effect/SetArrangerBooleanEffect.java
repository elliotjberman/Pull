// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ApplicationUiContext;
import java.util.Objects;

/** One native application UI request with an exact observed origin. */
public record SetArrangerBooleanEffect (ApplicationUiContext context, Property property, boolean enabled) implements CoreEffect
{
    public enum Property { CLIP_LAUNCHER_VISIBLE, IO_SECTION_VISIBLE, CUE_MARKERS_VISIBLE, TIMELINE_VISIBLE, EFFECT_TRACKS_VISIBLE, PLAYBACK_FOLLOW_ENABLED, DOUBLE_ROW_TRACK_HEIGHT }

    public SetArrangerBooleanEffect
    {
        context = Objects.requireNonNull (context, "context");
        property = Objects.requireNonNull (property, "property");
    }
}
