// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * Complete replayable state for one bounded parameter interaction.
 *
 * @param interactionId Core-owned nonzero session identity, or zero when inactive
 * @param acceptsMutations True while a new exact target may join the session
 * @param baselines Exact retained target-to-baseline leases
 * @param blockedMutations Retained targets whose established stable writes are temporarily blocked
 * @param blockedActions State scopes whose semantic actions must wait
 * @param pendingActionCount Number of admitted semantic actions waiting for restoration
 */
public record DesiredParameterInteraction (long interactionId, boolean acceptsMutations, Map<ParameterTargetRef, Double> baselines, Set<ParameterTargetRef> blockedMutations, Set<ControllerStateScope> blockedActions, int pendingActionCount)
{
    /** Maximum semantic actions waiting behind one interaction. */
    public static final int PENDING_ACTION_CAPACITY = 64;

    private static final DesiredParameterInteraction EMPTY = new DesiredParameterInteraction (0, false, Map.of (), Set.of (), Set.of (), 0);


    /** Validate and copy the bounded interaction. */
    public DesiredParameterInteraction
    {
        baselines = Map.copyOf (Objects.requireNonNull (baselines, "baselines"));
        blockedMutations = Set.copyOf (Objects.requireNonNull (blockedMutations, "blockedMutations"));
        blockedActions = Set.copyOf (Objects.requireNonNull (blockedActions, "blockedActions"));
        if (baselines.size () > ParameterSlot.INTERACTION_TARGET_CAPACITY)
            throw new IllegalArgumentException ("parameter leases exceed the interaction target capacity");
        if (pendingActionCount < 0 || pendingActionCount > PENDING_ACTION_CAPACITY)
            throw new IllegalArgumentException ("pending parameter actions exceed the installed capacity");
        if (!baselines.keySet ().containsAll (blockedMutations))
            throw new IllegalArgumentException ("blocked parameter mutations require matching exact leases");
        baselines.forEach ( (target, baseline) -> {
            Objects.requireNonNull (target, "parameter lease target");
            if (!Double.isFinite (Objects.requireNonNull (baseline, "parameter lease baseline").doubleValue ()))
                throw new IllegalArgumentException ("parameter lease baseline must be finite");
        });

        final boolean inactive = !acceptsMutations && baselines.isEmpty () && blockedMutations.isEmpty () && blockedActions.isEmpty () && pendingActionCount == 0;
        if (inactive != (interactionId == 0))
            throw new IllegalArgumentException ("parameter interaction identity must be nonzero exactly while active");
        if (pendingActionCount > 0 && blockedActions.isEmpty ())
            throw new IllegalArgumentException ("pending parameter actions require an action barrier");
    }


    /** Test whether one retained target's established mutation is blocked. */
    public boolean blocksMutation (final ParameterTargetRef target)
    {
        return this.blockedMutations.contains (Objects.requireNonNull (target, "target"));
    }


    /** Test whether one semantic action intersects this interaction's barrier. */
    public boolean blocksAction (final ControllerActionBinding action)
    {
        for (final ControllerActionIntent intent: Objects.requireNonNull (action, "action").intents ())
        {
            if (this.blocksAction (intent))
                return true;
        }
        return false;
    }


    /** Test whether one resolved semantic intent intersects this interaction's barrier. */
    public boolean blocksAction (final ControllerActionIntent intent)
    {
        for (final ControllerStateScope scope: Objects.requireNonNull (intent, "intent").invalidates ())
        {
            if (this.blockedActions.contains (scope))
                return true;
        }
        return false;
    }


    /** Get the inactive interaction. */
    public static DesiredParameterInteraction empty ()
    {
        return EMPTY;
    }
}
