// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.view;

import de.mossgrabers.pull.core.api.ClipTargetId;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.DesiredNoteRepeat;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.api.output.ControllerDisplayOverlay;

import java.util.Map;
import java.util.Objects;


/**
 * Complete replayable state contributed by one view.
 *
 * @param lights Hardware lights owned by the view
 * @param clipBindings Clip targets owned by the view's shell interaction bridge
 * @param display Complete controller display owned by the view
 * @param padGridOverlay Temporary overlay composed above stable pad-grid output
 * @param displayOverlay Temporary overlay composed above the current display page
 * @param controllerLayout Complete replayable note-controller layout request
 * @param noteRepeat Complete replayable note-repeat ownership and state
 */
public record ViewOutput (Map<ControlId, RgbColor> lights, Map<ControlId, ClipTargetId> clipBindings, ControllerDisplayScene display, ControllerPadGridOverlay padGridOverlay, ControllerDisplayOverlay displayOverlay, DesiredControllerLayout controllerLayout, DesiredNoteRepeat noteRepeat)
{
    private static final ViewOutput EMPTY = new ViewOutput (Map.of (), Map.of (), ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive (), DesiredControllerLayout.empty (), DesiredNoteRepeat.unowned ());


    /**
     * Validate and copy output.
     */
    public ViewOutput
    {
        lights = Map.copyOf (Objects.requireNonNull (lights, "lights"));
        clipBindings = Map.copyOf (Objects.requireNonNull (clipBindings, "clipBindings"));
        display = Objects.requireNonNull (display, "display");
        padGridOverlay = Objects.requireNonNull (padGridOverlay, "padGridOverlay");
        displayOverlay = Objects.requireNonNull (displayOverlay, "displayOverlay");
        controllerLayout = Objects.requireNonNull (controllerLayout, "controllerLayout");
        noteRepeat = Objects.requireNonNull (noteRepeat, "noteRepeat");
    }


    /** Compatibility constructor without controller-mechanism ownership. */
    public ViewOutput (final Map<ControlId, RgbColor> lights, final Map<ControlId, ClipTargetId> clipBindings, final ControllerDisplayScene display, final ControllerPadGridOverlay padGridOverlay, final ControllerDisplayOverlay displayOverlay)
    {
        this (lights, clipBindings, display, padGridOverlay, displayOverlay, DesiredControllerLayout.empty (), DesiredNoteRepeat.unowned ());
    }


    /** Compatibility constructor without a display overlay. */
    public ViewOutput (final Map<ControlId, RgbColor> lights, final Map<ControlId, ClipTargetId> clipBindings, final ControllerDisplayScene display, final ControllerPadGridOverlay padGridOverlay)
    {
        this (lights, clipBindings, display, padGridOverlay, ControllerDisplayOverlay.inactive ());
    }


    /** Compatibility constructor without a pad-grid overlay. */
    public ViewOutput (final Map<ControlId, RgbColor> lights, final Map<ControlId, ClipTargetId> clipBindings, final ControllerDisplayScene display)
    {
        this (lights, clipBindings, display, ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive ());
    }


    /** Compatibility constructor without a display override. */
    public ViewOutput (final Map<ControlId, RgbColor> lights, final Map<ControlId, ClipTargetId> clipBindings)
    {
        this (lights, clipBindings, ControllerDisplayScene.empty (), ControllerPadGridOverlay.inactive (), ControllerDisplayOverlay.inactive ());
    }


    /**
     * Get empty view output.
     *
     * @return Empty output
     */
    public static ViewOutput empty ()
    {
        return EMPTY;
    }
}
