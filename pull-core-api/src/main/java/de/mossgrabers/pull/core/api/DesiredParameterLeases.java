// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Map;
import java.util.Objects;


/**
 * Complete replayable set of exact parameter actuators retained for one core generation.
 *
 * @param baselines Target-to-restoration-baseline map
 */
public record DesiredParameterLeases (Map<ParameterTargetRef, Double> baselines)
{
    private static final DesiredParameterLeases EMPTY = new DesiredParameterLeases (Map.of ());


    /**
     * Validate and copy the bounded lease set.
     */
    public DesiredParameterLeases
    {
        baselines = Map.copyOf (Objects.requireNonNull (baselines, "baselines"));
        if (baselines.size () > ParameterBridgeSnapshot.TARGET_CAPACITY)
            throw new IllegalArgumentException ("parameter leases exceed the installed target capacity");
        baselines.forEach ( (target, baseline) -> {
            Objects.requireNonNull (target, "parameter lease target");
            if (!Double.isFinite (Objects.requireNonNull (baseline, "parameter lease baseline").doubleValue ()))
                throw new IllegalArgumentException ("parameter lease baseline must be finite");
        });
    }


    /**
     * Get an empty desired lease set.
     *
     * @return Empty leases
     */
    public static DesiredParameterLeases empty ()
    {
        return EMPTY;
    }
}
