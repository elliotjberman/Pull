// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerActionBinding;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.event.ControllerInputEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;

import java.util.List;
import java.util.Map;
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
     * Get the selected fixed profile and optional facets.
     *
     * @return Immutable profile
     */
    ViewProfile profile ();


    /**
     * Get all effective claims from the selected profile.
     *
     * @return Immutable claims
     */
    default Set<SurfaceClaim> claims ()
    {
        return this.profile ().claims ();
    }


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
     * Declare physical edge inputs which this view maps to semantic actions.
     *
     * @return Complete immutable action binding set
     */
    default Set<ControllerActionBinding> actionBindings ()
    {
        return Set.of ();
    }


    /**
     * Declare physical continuous controls mapped to bounded parameter slots by this view.
     *
     * @return Complete immutable control-to-slot mapping
     */
    default Map<ControlId, ParameterSlot> parameterBindings ()
    {
        return Map.of ();
    }


    /**
     * Select installed parameter banks this view needs sampled. A view may request a bank for
     * rendering without mapping a physical control to it.
     *
     * @return Complete immutable bank selection
     */
    default Set<ParameterBankId> parameterBanks ()
    {
        return this.parameterBindings ().values ().stream ().map (ParameterSlot::bank).collect (java.util.stream.Collectors.toUnmodifiableSet ());
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


    /** Resolve a complete immutable intent at gesture begin. */
    default ResolvedControllerAction resolveAction (final ControllerActionBinding binding, final ControllerInputEvent input, final ControllerSnapshot snapshot)
    {
        return ResolvedControllerAction.stable (binding.intent ());
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
