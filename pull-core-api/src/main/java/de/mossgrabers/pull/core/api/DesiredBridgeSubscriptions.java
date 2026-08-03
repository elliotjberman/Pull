// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;
import java.util.Set;

/**
 * Complete replayable set of bounded bridge-state domains requested by the reloadable core.
 *
 * <p>An absent domain is represented by that snapshot type's {@code empty()} value. Returning a
 * new result replaces the complete subscription set.</p>
 *
 * @param domains Requested state domains
 */
public record DesiredBridgeSubscriptions (Set<BridgeSubscription> domains)
{
    private static final DesiredBridgeSubscriptions EMPTY = new DesiredBridgeSubscriptions (Set.of ());


    /**
     * Validate and copy the subscriptions.
     */
    public DesiredBridgeSubscriptions
    {
        domains = Set.copyOf (Objects.requireNonNull (domains, "domains"));
    }


    /**
     * Get an empty subscription set.
     *
     * @return Empty subscriptions
     */
    public static DesiredBridgeSubscriptions empty ()
    {
        return EMPTY;
    }


    /**
     * Test whether a bridge domain is requested.
     *
     * @param domain Domain
     * @return True when requested
     */
    public boolean includes (final BridgeSubscription domain)
    {
        return this.domains.contains (Objects.requireNonNull (domain, "domain"));
    }
}
