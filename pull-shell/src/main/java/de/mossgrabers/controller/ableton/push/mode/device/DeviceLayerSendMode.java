// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;


import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.mode.Modes;


/**
 * Mode for editing a all sends of a device layer.
 *
 * @author Jürgen Moßgraber
 */
public class DeviceLayerSendMode extends DeviceLayerMode
{
    private final int sendIndex;

    /** Existing selected send lane; observed by the core display bridge. */
    public int getSendIndex () { return this.sendIndex; }


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     * @param sendIndex The index of the send
     */
    public DeviceLayerSendMode (final PushControlSurface surface, final IModel model, final int sendIndex)
    {
        super (Modes.NAME_LAYER_SENDS, surface, model);

        this.sendIndex = sendIndex;

    }





}
