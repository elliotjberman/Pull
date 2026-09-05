// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/** Select one installed controller mode from the observed layout generation. */
public record SelectControllerModeEffect (long layoutGeneration, String modeId) implements CoreEffect
{
    public SelectControllerModeEffect
    {
        modeId = Objects.requireNonNull (modeId, "modeId");
        if (layoutGeneration < 0 || modeId.isBlank () || modeId.length () > 128)
            throw new IllegalArgumentException ("controller mode selection requires a layout generation and bounded ID");
    }
}
