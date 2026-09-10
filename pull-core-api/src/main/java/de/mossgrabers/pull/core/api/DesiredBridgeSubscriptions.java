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
 * @param clipScan Target-fenced request for the one selected-track clip window
 */
public record DesiredBridgeSubscriptions (Set<BridgeSubscription> domains, DesiredClipScan clipScan)
{
    private static final DesiredBridgeSubscriptions EMPTY = new DesiredBridgeSubscriptions (Set.of ());


    /**
     * Validate and copy the subscriptions.
     */
    public DesiredBridgeSubscriptions
    {
        clipScan = Objects.requireNonNull (clipScan, "clipScan");
        domains = Set.copyOf (Objects.requireNonNull (domains, "domains"));
        if (clipScan.active () && !domains.contains (BridgeSubscription.SELECTED_TRACK_CLIPS))
            throw new IllegalArgumentException ("Clip scan subscription requires exactly one active window request");
    }


    /** Subscriptions without a selected-track clip window. */
    public DesiredBridgeSubscriptions (final Set<BridgeSubscription> domains)
    {
        this (domains, DesiredClipScan.inactive ());
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
