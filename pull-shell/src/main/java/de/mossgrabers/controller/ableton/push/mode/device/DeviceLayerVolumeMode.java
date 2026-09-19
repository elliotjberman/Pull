// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;


import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.mode.Modes;


/**
 * Mode for editing the volume of all device layers.
 *
 * @author Jürgen Moßgraber
 */
public class DeviceLayerVolumeMode extends DeviceLayerMode
{
    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public DeviceLayerVolumeMode (final PushControlSurface surface, final IModel model)
    {
        super (Modes.NAME_LAYER_VOLUME, surface, model);

    }





}
