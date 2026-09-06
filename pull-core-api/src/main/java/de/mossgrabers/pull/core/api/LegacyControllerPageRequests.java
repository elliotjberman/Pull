// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import java.util.List;
import java.util.Objects;

/**
 * Parent-owned request stream. The retired prefix survives child checkpoint rejection and includes
 * acknowledged or abandoned requests. Its zero-cost lifecycle metadata is always available; only
 * the bounded pending list is controlled by the CONTROLLER_PAGES subscription.
 */
public record LegacyControllerPageRequests (long retiredSequence, List<LegacyControllerPageRequest> requests)
{
    public static final int CAPACITY = 64;
    private static final LegacyControllerPageRequests EMPTY = new LegacyControllerPageRequests (0, List.of ());
    public LegacyControllerPageRequests
    {
        requests = List.copyOf (Objects.requireNonNull (requests, "requests"));
        if (retiredSequence < 0) throw new IllegalArgumentException ("Retired request prefix must be nonnegative");
        if (requests.size () > CAPACITY) throw new IllegalArgumentException ("Controller page inbox exceeds its bound");
        long previous = retiredSequence;
        for (final LegacyControllerPageRequest request: requests)
        {
            if (previous == Long.MAX_VALUE || request.sequence () != previous + 1) throw new IllegalArgumentException ("Controller page requests must continue their retired prefix in order");
            previous = request.sequence ();
        }
    }
    /** Compatibility construction of a contiguous pending suffix. */
    public LegacyControllerPageRequests (final List<LegacyControllerPageRequest> requests)
    {
        this (requests.isEmpty () ? 0 : requests.get (0).sequence () - 1, requests);
    }
    public static LegacyControllerPageRequests empty () { return EMPTY; }
}
