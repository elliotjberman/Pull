// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

/**
 * Replayable runtime cadence and admission of core replacement.
 *
 * @param ticksRequested True while unchanged authoritative state must still advance core time
 * @param replacementBlocked True while a bounded host-dependent continuation still belongs to this core
 */
public record CoreExecutionRequirements (boolean ticksRequested, boolean replacementBlocked)
{
    private static final CoreExecutionRequirements EMPTY = new CoreExecutionRequirements (false);


    public CoreExecutionRequirements (final boolean ticksRequested) { this (ticksRequested, false); }


    /** Combine complete requirements without dropping another owner's replacement fence. */
    public CoreExecutionRequirements merge (final CoreExecutionRequirements other)
    {
        return new CoreExecutionRequirements (this.ticksRequested || other.ticksRequested, this.replacementBlocked || other.replacementBlocked);
    }


    /** Get the inert requirements. */
    public static CoreExecutionRequirements empty ()
    {
        return EMPTY;
    }
}
