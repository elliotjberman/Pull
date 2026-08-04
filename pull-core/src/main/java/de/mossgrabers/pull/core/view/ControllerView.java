// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.CoreEvent;

import java.util.List;
import java.util.Set;


/**
 * Reloadable behavior with a fixed Push 2 footprint.
 */
public interface ControllerView
{
    /**
     * Get the stable view identifier.
     *
     * @return View identifier
     */
    String id ();


    /**
     * Get all fixed surface claims for this view profile.
     *
     * @return Immutable claims
     */
    Set<SurfaceClaim> claims ();


    /**
     * Get authoritative shell-state domains needed by this view.
     *
     * @return Bridge subscriptions
     */
    default Set<BridgeSubscription> bridgeSubscriptions ()
    {
        return Set.of ();
    }


    /**
     * Initialize view state from an authoritative snapshot.
     *
     * @param snapshot Initial snapshot
     */
    default void start (final ControllerSnapshot snapshot)
    {
        // Most views are stateless.
    }


    /**
     * Reconcile complete view state before the current event is dispatched. Every view receives
     * this callback because one shell snapshot can change several independent domains.
     *
     * @param snapshot Authoritative state after the event
     */
    default void reconcile (final ControllerSnapshot snapshot)
    {
        // Most views derive output directly from the snapshot.
    }


    /**
     * Handle an event routed through this view's declared input claims.
     *
     * @param event Event
     * @param snapshot Authoritative state after the event
     * @return Ordered one-shot effects
     */
    default List<CoreEffect> handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        return List.of ();
    }


    /**
     * Render this view's complete replayable output.
     *
     * @param snapshot Current authoritative snapshot
     * @return Complete view-owned output
     */
    default ViewOutput render (final ControllerSnapshot snapshot)
    {
        return ViewOutput.empty ();
    }
}
