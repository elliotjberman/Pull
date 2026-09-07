// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.SessionLocation;
import java.util.Objects;

/** Copy between two simultaneously addressable slots in the bounded Session window. */
public record CopySessionClipEffect (SessionLocation source, SessionLocation target) implements CoreEffect
{
    public CopySessionClipEffect
    {
        Objects.requireNonNull (source, "source");
        Objects.requireNonNull (target, "target");
        if (source.isScene () || target.isScene () || !source.projectIdentity ().equals (target.projectIdentity ()) || source.generation () != target.generation () || !source.shape ().equals (target.shape ()))
            throw new IllegalArgumentException ("Copy requires slots in the same Session window");
    }
}
