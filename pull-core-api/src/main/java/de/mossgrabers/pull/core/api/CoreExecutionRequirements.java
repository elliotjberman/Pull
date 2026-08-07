// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;


/**
 * Replayable runtime cadence and transaction fencing requested by the active core result.
 *
 * @param ticksRequested True while unchanged authoritative state must still advance core time
 * @param projectNavigationLeaseId Non-zero while one bounded cross-project transaction is active
 * @param projectNavigationOrigin Exact project identity to restore before releasing that lease
 */
public record CoreExecutionRequirements (boolean ticksRequested, long projectNavigationLeaseId, String projectNavigationOrigin)
{
    private static final CoreExecutionRequirements EMPTY = new CoreExecutionRequirements (false, 0, "");


    /** Validate one complete requirement set. */
    public CoreExecutionRequirements
    {
        projectNavigationOrigin = Objects.requireNonNull (projectNavigationOrigin, "projectNavigationOrigin");
        if (projectNavigationLeaseId < 0)
            throw new IllegalArgumentException ("projectNavigationLeaseId must not be negative");
        if ((projectNavigationLeaseId == 0) != projectNavigationOrigin.isBlank ())
            throw new IllegalArgumentException ("A project-navigation lease requires both a positive ID and an origin");
    }


    /** Get the inert requirements. */
    public static CoreExecutionRequirements empty ()
    {
        return EMPTY;
    }


    /** Test whether replacement must wait for a cross-project transaction. */
    public boolean hasProjectNavigationLease ()
    {
        return this.projectNavigationLeaseId != 0;
    }
}
