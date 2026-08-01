// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Authoritative shell state supplied to the core.
 *
 * @param revision Monotonic snapshot revision
 * @param monotonicTimeNanos Shell monotonic time when captured
 * @param capabilities Capabilities available from the shell
 * @param clipCatalog Ordered candidate clips on the selected track
 * @param armedClipTargets Clip targets currently armed by logical control
 * @param pressedControls Currently pressed controls
 * @param touchedControls Currently touched controls
 */
public record ControllerSnapshot (long revision, long monotonicTimeNanos, ShellCapabilities capabilities, ClipCatalogSnapshot clipCatalog, Map<ControlId, ClipTargetId> armedClipTargets, Set<ControlId> pressedControls, Set<ControlId> touchedControls)
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
        armedClipTargets = Map.copyOf (Objects.requireNonNull (armedClipTargets, "armedClipTargets"));
        pressedControls = Set.copyOf (Objects.requireNonNull (pressedControls, "pressedControls"));
        touchedControls = Set.copyOf (Objects.requireNonNull (touchedControls, "touchedControls"));
    }
}
