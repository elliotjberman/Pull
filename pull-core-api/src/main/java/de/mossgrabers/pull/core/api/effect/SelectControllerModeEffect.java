// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import java.util.Objects;

/** Request a bounded mode-manager operation from the complete observed layout generation. */
public record SelectControllerModeEffect (long layoutGeneration, String modeId, Operation operation) implements CoreEffect
{
    public enum Operation { SELECT, TEMPORARY, RESTORE }

    public SelectControllerModeEffect
    {
        modeId = Objects.requireNonNull (modeId, "modeId");
        operation = Objects.requireNonNull (operation, "operation");
        if (layoutGeneration < 0 || modeId.length () > 128 || (operation == Operation.RESTORE ? !modeId.isEmpty () : modeId.isBlank ()))
            throw new IllegalArgumentException ("controller mode operation requires a layout generation and valid bounded target");
    }

    public SelectControllerModeEffect (final long layoutGeneration, final String modeId)
    {
        this (layoutGeneration, modeId, Operation.SELECT);
    }

    public static SelectControllerModeEffect restore (final long layoutGeneration)
    {
        return new SelectControllerModeEffect (layoutGeneration, "", Operation.RESTORE);
    }
}
