// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.parameterprovider.PushSendLayerOrDrumPadParameterProvider;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IChannel;
import de.mossgrabers.framework.daw.data.ILayer;
import de.mossgrabers.framework.daw.data.ISend;
import de.mossgrabers.framework.daw.data.bank.ISendBank;
import de.mossgrabers.framework.daw.resource.ChannelType;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.MenuData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.ParameterData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.TrackData;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.Pair;


/**
 * Mode for editing a all sends of a device layer.
 *
 * @author Jürgen Moßgraber
 */
public class DeviceLayerSendMode extends DeviceLayerMode
{
    private final int sendIndex;


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


    /** {@inheritDoc} */
    @Override
    public void updateDisplayElements (final IGraphicDisplay display, final Optional<ILayer> selectedLayer)
    {
        this.updateMenuItems (5 + this.sendIndex % 4);

        final List<MenuData> menus = new ArrayList<> (8);
        final List<ParameterData> parameters = new ArrayList<> (8);
        final List<TrackData> layers = new ArrayList<> (8);
        final int offset = this.getDrumPadIndex ();
        final IValueChanger valueChanger = this.model.getValueChanger ();

        for (int i = 0; i < 8; i++)
        {
            final Pair<String, Boolean> menuItem = this.menu.get (i);
            menus.add (new MenuData (menuItem.getKey ().trim (), menuItem.getValue ().booleanValue ()));

            final IChannel layer = this.bank.getItem (offset + i);
            final ISendBank sendBank = layer.getSendBank ();
            final ISend send = this.sendIndex < sendBank.getPageSize () ? sendBank.getItem (this.sendIndex) : null;
            if (layer.doesExist () && send != null && send.doesExist ())
            {
                parameters.add (new ParameterData (send.getName (), valueChanger.toDisplayValue (send.getValue ()), valueChanger.toDisplayValue (send.getModulatedValue ()), send.getDisplayedValue (8), layer.isActivated () && send.isEnabled ()));
            }
            else
                parameters.add (new ParameterData ("", -1, -1, "", false));

            layers.add (new TrackData (layer.doesExist () ? layer.getName (12) : "", ChannelType.LAYER, layer.getColor (), layer.isSelected (), layer.isActivated (), false));
        }

        display.addElement (new TrackMixerComponent (menus, parameters, layers, 0, 0));
    }
}
