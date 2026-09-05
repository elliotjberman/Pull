// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.CurrentTrackTarget;
import java.util.Objects;

/** One direct operation on an observed current-bank track. */
public record CurrentTrackActionEffect (CurrentTrackTarget target, Action action) implements CoreEffect
{
    public enum Action { SELECT, DUPLICATE, REMOVE, ENTER_SELECTED_GROUP }

    public CurrentTrackActionEffect
    {
        target = Objects.requireNonNull (target, "target");
        action = Objects.requireNonNull (action, "action");
    }
}
