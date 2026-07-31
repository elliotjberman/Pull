// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.parameterprovider.PushVolumeLayerOrDrumPadParameterProvider;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IChannel;
import de.mossgrabers.framework.daw.data.ILayer;
import de.mossgrabers.framework.daw.resource.ChannelType;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.MenuData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.ParameterData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.TrackData;
import de.mossgrabers.framework.graphics.canvas.component.TrackVolumeComponent;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.Pair;


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

        this.setParameterProvider (new PushVolumeLayerOrDrumPadParameterProvider (this.cursorDevice));
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
            layer.resetVolume ();
        }

        layer.touchVolume (isTouched);
        this.checkStopAutomationOnKnobRelease (isTouched);
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplayElements (final IGraphicDisplay display, final Optional<ILayer> selectedLayer)
    {
        this.updateMenuItems (1);

        final List<MenuData> menus = new ArrayList<> (8);
        final List<ParameterData> parameters = new ArrayList<> (8);
        final List<TrackData> layers = new ArrayList<> (8);
        final List<Integer> vuLeft = new ArrayList<> (8);
        final List<Integer> vuRight = new ArrayList<> (8);
        final int offset = this.getDrumPadIndex ();
        final IValueChanger valueChanger = this.model.getValueChanger ();
        final boolean enableVUMeters = this.configuration.isEnableVUMeters ();

        for (int i = 0; i < 8; i++)
        {
            final Pair<String, Boolean> menuItem = this.menu.get (i);
            menus.add (new MenuData (menuItem.getKey ().trim (), menuItem.getValue ().booleanValue ()));

            final IChannel layer = this.bank.getItem (offset + i);
            if (layer.doesExist ())
            {
                parameters.add (new ParameterData ("Volume", valueChanger.toDisplayValue (layer.getVolume ()), valueChanger.toDisplayValue (layer.getModulatedVolume ()), layer.getVolumeStr (8), layer.isActivated ()));
                vuLeft.add (Integer.valueOf (valueChanger.toDisplayValue (enableVUMeters ? layer.getVuLeft () : 0)));
                vuRight.add (Integer.valueOf (valueChanger.toDisplayValue (enableVUMeters ? layer.getVuRight () : 0)));
            }
            else
            {
                parameters.add (new ParameterData ("", -1, -1, "", false));
                vuLeft.add (Integer.valueOf (0));
                vuRight.add (Integer.valueOf (0));
            }

            layers.add (new TrackData (layer.doesExist () ? layer.getName (12) : "", ChannelType.LAYER, layer.getColor (), layer.isSelected (), layer.isActivated (), false));
        }

        display.addElement (new TrackVolumeComponent (menus, parameters, layers, vuLeft, vuRight));
    }
}
