// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.effect.CoreEffect;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Complete output of one core lifecycle call.
 *
 * TODO(Pull architecture): {@code desiredClipBindings} is the vertical-slice bridge for drum
 * fills. Do not add parallel feature-specific binding/session fields here; introduce modular typed
 * shell capabilities before the next interaction needs stable target ownership.
 *
 * @param desiredOutput Complete replayable hardware output
 * @param desiredInputRoutes Complete replayable controller-input ownership
 * @param desiredBridgeSubscriptions Complete replayable bridge-state subscriptions
 * @param desiredClipBindings Complete replayable clip binding state by logical control
 * @param effects Ordered one-shot shell effects
 */
public record CoreResult (DesiredHardwareOutput desiredOutput, DesiredInputRoutes desiredInputRoutes, DesiredBridgeSubscriptions desiredBridgeSubscriptions, Map<ControlId, ClipTargetId> desiredClipBindings, List<CoreEffect> effects)
{
    private static final CoreResult EMPTY = new CoreResult (DesiredHardwareOutput.empty (), DesiredInputRoutes.empty (), DesiredBridgeSubscriptions.empty (), Map.of (), List.of ());


    /**
     * Validate and copy result values.
     */
    public CoreResult
    {
        desiredOutput = Objects.requireNonNull (desiredOutput, "desiredOutput");
        desiredInputRoutes = Objects.requireNonNull (desiredInputRoutes, "desiredInputRoutes");
        desiredBridgeSubscriptions = Objects.requireNonNull (desiredBridgeSubscriptions, "desiredBridgeSubscriptions");
        desiredClipBindings = Map.copyOf (Objects.requireNonNull (desiredClipBindings, "desiredClipBindings"));
        effects = List.copyOf (Objects.requireNonNull (effects, "effects"));
    }


    /**
     * Construct a result using legacy input routing.
     *
     * @param desiredOutput Complete replayable hardware output
     * @param desiredClipBindings Complete replayable clip binding state by logical control
     * @param effects Ordered one-shot shell effects
     */
    public CoreResult (final DesiredHardwareOutput desiredOutput, final Map<ControlId, ClipTargetId> desiredClipBindings, final List<CoreEffect> effects)
    {
        this (desiredOutput, DesiredInputRoutes.empty (), DesiredBridgeSubscriptions.empty (), desiredClipBindings, effects);
    }


    /**
     * Construct a result with no desired clip bindings.
     *
     * @param desiredOutput Complete replayable hardware output
     * @param effects Ordered one-shot shell effects
     */
    public CoreResult (final DesiredHardwareOutput desiredOutput, final List<CoreEffect> effects)
    {
        this (desiredOutput, DesiredInputRoutes.empty (), DesiredBridgeSubscriptions.empty (), Map.of (), effects);
    }


    /**
     * Construct a result with replayable input routing and no clip bindings.
     *
     * @param desiredOutput Complete replayable hardware output
     * @param desiredInputRoutes Complete replayable controller-input ownership
     * @param effects Ordered one-shot shell effects
     */
    public CoreResult (final DesiredHardwareOutput desiredOutput, final DesiredInputRoutes desiredInputRoutes, final List<CoreEffect> effects)
    {
        this (desiredOutput, desiredInputRoutes, DesiredBridgeSubscriptions.empty (), Map.of (), effects);
    }


    /**
     * Construct a result with replayable input routing and clip bindings but no bridge-state
     * subscriptions.
     *
     * @param desiredOutput Complete replayable hardware output
     * @param desiredInputRoutes Complete replayable controller-input ownership
     * @param desiredClipBindings Complete replayable clip binding state
     * @param effects Ordered one-shot shell effects
     */
    public CoreResult (final DesiredHardwareOutput desiredOutput, final DesiredInputRoutes desiredInputRoutes, final Map<ControlId, ClipTargetId> desiredClipBindings, final List<CoreEffect> effects)
    {
        this (desiredOutput, desiredInputRoutes, DesiredBridgeSubscriptions.empty (), desiredClipBindings, effects);
    }


    /**
     * Construct a result with replayable input routing and bridge subscriptions.
     *
     * @param desiredOutput Complete replayable hardware output
     * @param desiredInputRoutes Complete replayable controller-input ownership
     * @param desiredBridgeSubscriptions Complete replayable bridge-state subscriptions
     * @param effects Ordered one-shot shell effects
     */
    public CoreResult (final DesiredHardwareOutput desiredOutput, final DesiredInputRoutes desiredInputRoutes, final DesiredBridgeSubscriptions desiredBridgeSubscriptions, final List<CoreEffect> effects)
    {
        this (desiredOutput, desiredInputRoutes, desiredBridgeSubscriptions, Map.of (), effects);
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
