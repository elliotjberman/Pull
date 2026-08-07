// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;

import de.mossgrabers.pull.core.api.ControlId;

import java.util.Map;
import java.util.Objects;

/**
 * Complete replayable hardware state currently owned by the core.
 *
 * @param lights Desired light colors by stable control identifier
 * @param display Complete controller display scene
 * @param padGridOverlay Temporary sparse pad-grid overlay
 * @param displayOverlay Temporary complete scene above the current display page
 */
public record DesiredHardwareOutput (Map<ControlId, RgbColor> lights, ControllerDisplayScene display, ControllerPadGridOverlay padGridOverlay, ControllerDisplayOverlay displayOverlay)
{
    private static final DesiredHardwareOutput EMPTY = new DesiredHardwareOutput (Map.of (), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive ());


    /**
     * Validate and copy the output map.
     */
    public DesiredHardwareOutput
    {
        lights = Map.copyOf (Objects.requireNonNull (lights, "lights"));
        display = Objects.requireNonNull (display, "display");
        padGridOverlay = Objects.requireNonNull (padGridOverlay, "padGridOverlay");
        displayOverlay = Objects.requireNonNull (displayOverlay, "displayOverlay");
    }


    /** Compatibility constructor without a display overlay. */
    public DesiredHardwareOutput (final Map<ControlId, RgbColor> lights, final ControllerDisplayScene display, final ControllerPadGridOverlay padGridOverlay)
    {
        this (lights, display, padGridOverlay, ControllerDisplayOverlay.inactive ());
    }


    /** Compatibility constructor without temporary overlays. */
    public DesiredHardwareOutput (final Map<ControlId, RgbColor> lights, final ControllerDisplayScene display)
    {
        this (lights, display, ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive ());
    }


    /** Compatibility constructor without a display override. */
    public DesiredHardwareOutput (final Map<ControlId, RgbColor> lights)
    {
        this (lights, ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive ());
    }


    /**
     * Get empty desired hardware output.
     *
     * @return Empty output
     */
    public static DesiredHardwareOutput empty ()
    {
        return EMPTY;
    }
}
