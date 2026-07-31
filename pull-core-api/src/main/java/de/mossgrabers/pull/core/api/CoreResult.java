// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;

import java.util.List;
import java.util.Objects;

/**
 * Complete output of one core lifecycle call.
 *
 * @param desiredOutput Complete replayable hardware output
 * @param effects Ordered one-shot shell effects
 */
public record CoreResult (DesiredHardwareOutput desiredOutput, List<CoreEffect> effects)
{
    private static final CoreResult EMPTY = new CoreResult (DesiredHardwareOutput.empty (), List.of ());


    /**
     * Validate and copy result values.
     */
    public CoreResult
    {
        desiredOutput = Objects.requireNonNull (desiredOutput, "desiredOutput");
        effects = List.copyOf (Objects.requireNonNull (effects, "effects"));
    }


    /**
     * Get an empty result.
     *
     * @return Empty output and effects
     */
    public static CoreResult empty ()
    {
        return EMPTY;
    }
}
