// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * Current bounded parameter slots and exact actuators retained by the stable shell.
 *
 * @param slots Current slot-to-target bindings
 * @param retainedBaselines Core-requested actuator leases and restoration baselines
 */
public record ParameterBridgeSnapshot (Map<ParameterSlot, ParameterTargetSnapshot> slots, Map<ParameterTargetRef, Double> retainedBaselines)
{
    /** Total number of simultaneously addressable installed targets. */
    public static final int TARGET_CAPACITY = ParameterSlot.INSTALLED_TARGET_CAPACITY;

    private static final ParameterBridgeSnapshot EMPTY = new ParameterBridgeSnapshot (Map.of (), Map.of ());


    /**
     * Validate and copy the bounded snapshot.
     */
    public ParameterBridgeSnapshot
    {
        slots = Map.copyOf (Objects.requireNonNull (slots, "slots"));
        retainedBaselines = Map.copyOf (Objects.requireNonNull (retainedBaselines, "retainedBaselines"));
        if (slots.size () > TARGET_CAPACITY)
            throw new IllegalArgumentException ("parameter bridge exceeds its installed target capacity");
        if (retainedBaselines.size () > ParameterSlot.INTERACTION_TARGET_CAPACITY)
            throw new IllegalArgumentException ("parameter bridge exceeds its interaction lease capacity");

        final Set<ParameterTargetRef> currentTargets = new HashSet<> ();
        slots.forEach ( (slot, target) -> {
            Objects.requireNonNull (slot, "parameter slot");
            currentTargets.add (Objects.requireNonNull (target, "parameter target").target ());
        });
        retainedBaselines.forEach ( (target, baseline) -> {
            if (!currentTargets.contains (Objects.requireNonNull (target, "retained parameter target")))
                throw new IllegalArgumentException ("retained parameter target must remain in the current slot window");
            if (!Double.isFinite (Objects.requireNonNull (baseline, "parameter baseline").doubleValue ()))
                throw new IllegalArgumentException ("parameter baseline must be finite");
        });
    }


    /**
     * Get unavailable parameter state.
     *
     * @return Empty snapshot
     */
    public static ParameterBridgeSnapshot empty ()
    {
        return EMPTY;
    }


    /**
     * Find authoritative state for one opaque target.
     *
     * @param target Target reference
     * @return Current target snapshot, or {@code null}
     */
    public ParameterTargetSnapshot targetOrNull (final ParameterTargetRef target)
    {
        final ParameterTargetRef checkedTarget = Objects.requireNonNull (target, "target");
        for (final ParameterTargetSnapshot snapshot: this.slots.values ())
        {
            if (checkedTarget.equals (snapshot.target ()))
                return snapshot;
        }
        return null;
    }
}
