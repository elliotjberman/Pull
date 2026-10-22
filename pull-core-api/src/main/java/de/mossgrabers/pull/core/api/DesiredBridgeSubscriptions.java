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
 * @param drumTarget Exact descendant requested by the group drum resolver
 */
public record DesiredBridgeSubscriptions (Set<BridgeSubscription> domains, DesiredClipScan clipScan, DesiredDrumTarget drumTarget)
{
    private static final DesiredBridgeSubscriptions EMPTY = new DesiredBridgeSubscriptions (Set.of ());


    /**
     * Validate and copy the subscriptions.
     */
    public DesiredBridgeSubscriptions
    {
        drumTarget = Objects.requireNonNull (drumTarget);
        if (drumTarget.active () && !domains.contains (BridgeSubscription.NOTE_VIEW))
            throw new IllegalArgumentException ("Drum target requires note observations");
        clipScan = Objects.requireNonNull (clipScan, "clipScan");
        domains = Set.copyOf (Objects.requireNonNull (domains, "domains"));
        if (clipScan.active () && !domains.contains (BridgeSubscription.SELECTED_TRACK_CLIPS))
            throw new IllegalArgumentException ("Clip scan subscription requires exactly one active window request");
    }


    public DesiredBridgeSubscriptions (Set<BridgeSubscription> domains, DesiredClipScan clipScan)
    {
        this (domains, clipScan, DesiredDrumTarget.inactive ());
    }

    /** Subscriptions without a selected-track clip window. */
    public DesiredBridgeSubscriptions (final Set<BridgeSubscription> domains)
    {
        this (domains, DesiredClipScan.inactive ());
    }


    /** Replace domains while preserving the exact bounded-window requests. */
    public DesiredBridgeSubscriptions withDomains (Set<BridgeSubscription> domains)
    {
        return new DesiredBridgeSubscriptions (domains, this.clipScan, this.drumTarget);
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
