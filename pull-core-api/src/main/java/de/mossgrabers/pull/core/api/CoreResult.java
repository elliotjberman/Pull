// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Complete output of one core lifecycle call.
 *
 * @param desiredOutput Complete replayable hardware output
 * @param desiredClipBindings Complete replayable clip binding state by logical control
 * @param claimedInputs Complete set of physical inputs owned by the core
 * @param effects Ordered one-shot shell effects
 */
public record CoreResult (DesiredHardwareOutput desiredOutput, Map<ControlId, ClipTargetId> desiredClipBindings, Set<ControlId> claimedInputs, List<CoreEffect> effects)
{
    private static final CoreResult EMPTY = new CoreResult (DesiredHardwareOutput.empty (), Map.of (), Set.of (), List.of ());


    /**
     * Validate and copy result values.
     */
    public CoreResult
    {
        desiredOutput = Objects.requireNonNull (desiredOutput, "desiredOutput");
        desiredClipBindings = Map.copyOf (Objects.requireNonNull (desiredClipBindings, "desiredClipBindings"));
        claimedInputs = Set.copyOf (Objects.requireNonNull (claimedInputs, "claimedInputs"));
        effects = List.copyOf (Objects.requireNonNull (effects, "effects"));
    }


    /**
     * Construct a result with no claimed physical inputs.
     *
     * @param desiredOutput Complete replayable hardware output
     * @param desiredClipBindings Complete replayable clip binding state by logical control
     * @param effects Ordered one-shot shell effects
     */
    public CoreResult (final DesiredHardwareOutput desiredOutput, final Map<ControlId, ClipTargetId> desiredClipBindings, final List<CoreEffect> effects)
    {
        this (desiredOutput, desiredClipBindings, Set.of (), effects);
    }


    /**
     * Construct a result with no desired clip bindings.
     *
     * @param desiredOutput Complete replayable hardware output
     * @param effects Ordered one-shot shell effects
     */
    public CoreResult (final DesiredHardwareOutput desiredOutput, final List<CoreEffect> effects)
    {
        this (desiredOutput, Map.of (), Set.of (), effects);
    }


    /**
     * Get an empty result.
     *
     * @return Empty output and effects
     */
    public static CoreResult empty ()
    {
        return EMPTY;
    }
}
