// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.SessionLocation;
import java.util.Objects;

/** Request creation at an exact Session slot; dependent actions require subsequent read-back. */
public record CreateSessionClipEffect (SessionLocation target, int lengthBeats) implements CoreEffect
{
    public CreateSessionClipEffect
    {
        Objects.requireNonNull (target, "target");
        if (target.isScene () || lengthBeats <= 0)
            throw new IllegalArgumentException ("Clip creation requires a slot and positive length");
    }
}
