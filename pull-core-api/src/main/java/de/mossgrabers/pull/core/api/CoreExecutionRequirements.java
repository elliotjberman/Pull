// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

/**
 * Replayable runtime cadence requested by the active core result.
 *
 * @param ticksRequested True while unchanged authoritative state must still advance core time
 */
public record CoreExecutionRequirements (boolean ticksRequested)
{
    private static final CoreExecutionRequirements EMPTY = new CoreExecutionRequirements (false);


    /** Get the inert requirements. */
    public static CoreExecutionRequirements empty ()
    {
        return EMPTY;
    }
}
