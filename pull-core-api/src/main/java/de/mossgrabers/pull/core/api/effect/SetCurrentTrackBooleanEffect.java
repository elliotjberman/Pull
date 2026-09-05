// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.CurrentTrackTarget;
import java.util.Objects;

/** An absolute Boolean write on an observed current-bank track. */
public record SetCurrentTrackBooleanEffect (CurrentTrackTarget target, Property property, boolean enabled) implements CoreEffect
{
    public enum Property { RECORD_ARMED, GROUP_EXPANDED }

    public SetCurrentTrackBooleanEffect
    {
        target = Objects.requireNonNull (target, "target");
        property = Objects.requireNonNull (property, "property");
    }
}
