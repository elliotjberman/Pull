// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ControllerNoteView;

import java.util.Objects;


/** Persist one selected-target-fenced controller Note-view preference. */
public record SetNoteViewPreferenceEffect (long targetGeneration, String channelId, int trackPosition, ControllerNoteView view) implements CoreEffect
{
    /** Validate the exact selected target and installed Note view. */
    public SetNoteViewPreferenceEffect
    {
        if (targetGeneration < 0)
            throw new IllegalArgumentException ("targetGeneration must not be negative");
        channelId = Objects.requireNonNull (channelId, "channelId");
        if (channelId.isBlank ())
            throw new IllegalArgumentException ("channelId must not be blank");
        if (trackPosition < 0)
            throw new IllegalArgumentException ("trackPosition must not be negative");
        view = Objects.requireNonNull (view, "view");
        if (!view.isPresent ())
            throw new IllegalArgumentException ("preferred note view must be present");
    }
}
