// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.SessionBankShape;

import java.util.Objects;


/** Generation-fenced request to stop the active bounded Session bank. */
public record StopSessionBankEffect (long targetGeneration, SessionBankShape shape, boolean alternative) implements CoreEffect
{
    /** Validate the requested bank identity. */
    public StopSessionBankEffect
    {
        if (targetGeneration < 0)
            throw new IllegalArgumentException ("targetGeneration must not be negative");
        shape = Objects.requireNonNull (shape, "shape");
        if (!shape.isPresent ())
            throw new IllegalArgumentException ("a Session-bank stop requires a visible bank");
    }
}
