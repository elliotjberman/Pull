// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;


import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.parameterprovider.PushSendLayerOrDrumPadParameterProvider;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IChannel;
import de.mossgrabers.framework.daw.data.ISend;
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

        this.setParameterProvider (new PushSendLayerOrDrumPadParameterProvider (this.cursorDevice, sendIndex));
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        this.setTouchedKnob (index, isTouched);

        // Drum Pad Bank has size of 16, layers only 8
        final int offset = this.getDrumPadIndex ();
        final IChannel layer = this.bank.getItem (offset + index);
        if (!layer.doesExist ())
            return;

        if (isTouched && this.surface.isDeletePressed ())
        {
            this.surface.setTriggerConsumed (ButtonID.DELETE);
            layer.getSendBank ().getItem (this.sendIndex).resetValue ();
        }

        layer.getSendBank ().getItem (this.sendIndex).touchValue (isTouched);
        this.checkStopAutomationOnKnobRelease (isTouched);

        // Toggle send enablement
        if (isTouched && this.surface.isShiftPressed () && this.surface.isSelectPressed () && this.getParameterProvider ().get (index) instanceof final ISend send)
        {
            this.surface.setTriggerConsumed (ButtonID.SELECT);
            send.toggleEnabled ();
        }
    }


}
