// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Authoritative shell state supplied to the core.
 *
 * @param revision Monotonic snapshot revision
 * @param monotonicTimeNanos Shell monotonic time when captured
 * @param capabilities Capabilities available from the shell
 * @param clipCatalog Ordered candidate clips on the selected track
 * @param selectedTrackParameters Armed tagged remote parameters on the selected track
 * @param armedClipTargets Clip targets currently armed by logical control
 * @param clipLaunchSessionTargets Frozen target for every owner retained in the shell-managed
 * clip-launch session
 * @param activeClipLaunchOwner Logical owner of the active shell-managed clip-launch session
 * @param pressedControls Currently pressed controls
 * @param touchedControls Currently touched controls
 */
public record ControllerSnapshot (long revision, long monotonicTimeNanos, ShellCapabilities capabilities, ClipCatalogSnapshot clipCatalog, ParameterCatalogSnapshot selectedTrackParameters, Map<ControlId, ClipTargetId> armedClipTargets, Map<ControlId, ClipTargetId> clipLaunchSessionTargets, Optional<ControlId> activeClipLaunchOwner, Set<ControlId> pressedControls, Set<ControlId> touchedControls)
{
    /**
     * Validate and copy snapshot values.
     */
    public ControllerSnapshot
    {
        if (revision < 0)
            throw new IllegalArgumentException ("revision must not be negative");
        if (monotonicTimeNanos < 0)
            throw new IllegalArgumentException ("monotonicTimeNanos must not be negative");

        capabilities = Objects.requireNonNull (capabilities, "capabilities");
        clipCatalog = Objects.requireNonNull (clipCatalog, "clipCatalog");
        selectedTrackParameters = Objects.requireNonNull (selectedTrackParameters, "selectedTrackParameters");
        armedClipTargets = Map.copyOf (Objects.requireNonNull (armedClipTargets, "armedClipTargets"));
        clipLaunchSessionTargets = Map.copyOf (Objects.requireNonNull (clipLaunchSessionTargets, "clipLaunchSessionTargets"));
        if (clipLaunchSessionTargets.size () > 1)
            throw new IllegalArgumentException ("The shell-managed clip-launch session can retain at most one target");
        activeClipLaunchOwner = Objects.requireNonNull (activeClipLaunchOwner, "activeClipLaunchOwner");
        if (activeClipLaunchOwner.isPresent () && !clipLaunchSessionTargets.containsKey (activeClipLaunchOwner.get ()))
            throw new IllegalArgumentException ("activeClipLaunchOwner must identify a retained clip-launch target");
        pressedControls = Set.copyOf (Objects.requireNonNull (pressedControls, "pressedControls"));
        touchedControls = Set.copyOf (Objects.requireNonNull (touchedControls, "touchedControls"));
    }


    /**
     * Construct a snapshot without an active clip-launch session.
     *
     * @param revision Monotonic snapshot revision
     * @param monotonicTimeNanos Shell monotonic time when captured
     * @param capabilities Capabilities available from the shell
     * @param clipCatalog Ordered candidate clips on the selected track
     * @param armedClipTargets Clip targets currently armed by logical control
     * @param pressedControls Currently pressed controls
     * @param touchedControls Currently touched controls
     */
    public ControllerSnapshot (final long revision, final long monotonicTimeNanos, final ShellCapabilities capabilities, final ClipCatalogSnapshot clipCatalog, final Map<ControlId, ClipTargetId> armedClipTargets, final Set<ControlId> pressedControls, final Set<ControlId> touchedControls)
    {
        this (revision, monotonicTimeNanos, capabilities, clipCatalog, ParameterCatalogSnapshot.empty (), armedClipTargets, Map.of (), Optional.empty (), pressedControls, touchedControls);
    }


    /**
     * Construct a snapshot with a clip-launch session and no selected-track parameters.
     *
     * @param revision Monotonic snapshot revision
     * @param monotonicTimeNanos Shell monotonic time when captured
     * @param capabilities Capabilities available from the shell
     * @param clipCatalog Ordered candidate clips on the selected track
     * @param armedClipTargets Clip targets currently armed by logical control
     * @param clipLaunchSessionTargets Retained clip-launch target
     * @param activeClipLaunchOwner Authoritative active clip owner
     * @param pressedControls Currently pressed controls
     * @param touchedControls Currently touched controls
     */
    public ControllerSnapshot (final long revision, final long monotonicTimeNanos, final ShellCapabilities capabilities, final ClipCatalogSnapshot clipCatalog, final Map<ControlId, ClipTargetId> armedClipTargets, final Map<ControlId, ClipTargetId> clipLaunchSessionTargets, final Optional<ControlId> activeClipLaunchOwner, final Set<ControlId> pressedControls, final Set<ControlId> touchedControls)
    {
        this (revision, monotonicTimeNanos, capabilities, clipCatalog, ParameterCatalogSnapshot.empty (), armedClipTargets, clipLaunchSessionTargets, activeClipLaunchOwner, pressedControls, touchedControls);
    }
}
