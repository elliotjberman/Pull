// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.SessionBankShape;
import java.util.Objects;

/** Absolute positioning of the existing Session banks. Minus one leaves that axis unchanged. */
public record SetSessionBankPositionEffect (long generation, SessionBankShape shape, int trackPosition, int scenePosition) implements CoreEffect
{
    public SetSessionBankPositionEffect
    {
        Objects.requireNonNull (shape, "shape");
        if (generation < 0 || !shape.isPresent () || trackPosition < -1 || scenePosition < -1 || trackPosition == -1 && scenePosition == -1)
            throw new IllegalArgumentException ("Invalid Session bank position");
    }
}
