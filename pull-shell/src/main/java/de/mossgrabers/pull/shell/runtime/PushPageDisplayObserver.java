// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.DAWColor;
import de.mossgrabers.framework.view.ColorView;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.pull.core.api.*;
import java.util.Arrays;

/** Copies only the active installed page's bounded state; never invokes its drawing or input. */
final class PushPageDisplayObserver
{
    private final PushEditingPageObserver editing = new PushEditingPageObserver ();

    ControllerPageDisplaySnapshot capture (final PushControlSurface surface, final IModel model)
    {
        final var mode = surface.getModeManager ().getActiveID ();
        final ControllerPageDisplayState state = mode == null ? new ControllerPageDisplayState.Empty () : switch (mode)
        {
            case BROWSER, SCALES, SCALE_LAYOUT, FIXED, REPEAT_NOTE, ADD_TRACK -> PushOptionPageObserver.capture (surface, model);
            case DEVICE_PARAMS, DEVICE_CHAINS, DEVICE_LAYER, DEVICE_LAYER_VOLUME, DEVICE_LAYER_PAN,
                DEVICE_LAYER_SEND1, DEVICE_LAYER_SEND2, DEVICE_LAYER_SEND3, DEVICE_LAYER_SEND4,
                DEVICE_LAYER_SEND5, DEVICE_LAYER_SEND6, DEVICE_LAYER_SEND7, DEVICE_LAYER_SEND8,
                DEVICE_LAYER_DETAILS, TRACK_DETAILS, CROSSFADER, USER -> PushDevicePageObserver.capture (surface, model);
            case CLIP, NOTE, REC_ARM, GROOVE -> this.editing.capture (surface, model);
            default -> new ControllerPageDisplayState.Empty ();
        };
        final var view = surface.getViewManager ();
        final ColorPaletteSnapshot palette = view.getActiveID () == Views.COLOR && view.getActive () instanceof final ColorView<?, ?> color ?
            new ColorPaletteSnapshot (color.getPage (), Arrays.stream (DAWColor.values ()).map (value -> SessionBankHost.toRgb (value.getColor ())).toList ()) : ColorPaletteSnapshot.empty ();
        return new ControllerPageDisplaySnapshot (mode == null ? "" : mode.name (), state, palette);
    }
}
