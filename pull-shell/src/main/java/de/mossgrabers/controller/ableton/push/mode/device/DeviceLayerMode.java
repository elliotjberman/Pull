// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.controller.ableton.push.parameterprovider.PushSelectedLayerOrDrumPadParameterProvider;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IChannel;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.daw.data.ILayer;
import de.mossgrabers.framework.daw.data.ISend;
import de.mossgrabers.framework.daw.data.bank.ILayerBank;
import de.mossgrabers.framework.daw.data.bank.ISendBank;
import de.mossgrabers.framework.daw.resource.ChannelType;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.MenuData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.ParameterData;
import de.mossgrabers.framework.graphics.canvas.component.TrackMixerComponent.TrackData;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.Pair;
import de.mossgrabers.framework.utils.StringUtils;


/**
 * Mode for editing a device layer.
 *
 * @author Jürgen Moßgraber
 */
public class DeviceLayerMode extends BaseMode<ILayer>
{
    protected final List<Pair<String, Boolean>> menu = new ArrayList<> ();
    protected final ICursorDevice               cursorDevice;
    protected final PushConfiguration           configuration;


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public DeviceLayerMode (final PushControlSurface surface, final IModel model)
    {
        this (Modes.NAME_LAYER, surface, model);

        this.setParameterProvider (new PushSelectedLayerOrDrumPadParameterProvider (this.cursorDevice, this.configuration));
    }


    /**
     * Constructor.
     *
     * @param name The name of the mode
     * @param surface The control surface
     * @param model The model
     */
    DeviceLayerMode (final String name, final PushControlSurface surface, final IModel model)
    {
        super (name, surface, model, model.getCursorDevice ().getLayerBank ());

        this.configuration = this.surface.getConfiguration ();
        this.cursorDevice = this.model.getCursorDevice ();
        this.cursorDevice.addHasDrumPadsObserver (hasDrumPads -> this.switchBanks (this.cursorDevice.hasDrumPads () ? this.cursorDevice.getDrumPadBank () : this.cursorDevice.getLayerBank ()));

        for (int i = 0; i < 8; i++)
            this.menu.add (new Pair<> (" ", Boolean.FALSE));
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        final Optional<ILayer> channelOpt = this.bank.getSelectedItem ();
        if (channelOpt.isEmpty ())
            return;

        final ILayer channel = channelOpt.get ();

        this.setTouchedKnob (index, isTouched);

        final ISendBank sendBank = channel.getSendBank ();

        if (isTouched && this.surface.isDeletePressed ())
        {
            this.surface.setTriggerConsumed (ButtonID.DELETE);
            switch (index)
            {
                case 0:
                    channel.resetVolume ();
                    break;
                case 1:
                    channel.resetPan ();
                    break;
                default:
                    if (index >= 4)
                        sendBank.getItem (this.getSendIndex (index)).resetValue ();
                    break;
            }
            return;
        }

        switch (index)
        {
            case 0:
                channel.touchVolume (isTouched);
                break;
            case 1:
                channel.touchPan (isTouched);
                break;
            default:
                if (index >= 4)
                    sendBank.getItem (this.getSendIndex (index)).touchValue (isTouched);
                break;
        }

        this.checkStopAutomationOnKnobRelease (isTouched);

        // Toggle send enablement
        if (isTouched && this.surface.isShiftPressed () && this.surface.isSelectPressed () && this.getParameterProvider ().get (index) instanceof final ISend send)
        {
            this.surface.setTriggerConsumed (ButtonID.SELECT);
            send.toggleEnabled ();
        }
    }


    private int getSendIndex (final int index)
    {
        return index - 4;
    }


    /** {@inheritDoc} */
    @Override
    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event == ButtonEvent.DOWN)
            return;

        if (event == ButtonEvent.UP)
        {
            if (!this.cursorDevice.doesExist ())
                return;

            final int offset = this.getDrumPadIndex ();
            final ILayer layer = this.bank.getItem (offset + index);
            if (!layer.doesExist ())
                return;

            final int layerIndex = layer.getIndex ();
            if (!layer.isSelected ())
            {
                this.bank.getItem (layerIndex).select ();
                return;
            }

            // Only select if it exists otherwise the parent device is selected which is confusing
            // to the user
            if (!layer.hasDevices ())
                return;
            layer.enter ();
            final ModeManager modeManager = this.surface.getModeManager ();
            this.setMode (Modes.DEVICE_PARAMS);
            ((DeviceParamsMode) modeManager.get (Modes.DEVICE_PARAMS)).setShowDevices (true);
            return;
        }

        // LONG press
        this.surface.setTriggerConsumed (ButtonID.get (ButtonID.ROW1_1, index));
        this.moveUp ();
    }


    /**
     * Move up the hierarchy.
     */
    protected void moveUp ()
    {
        // There is no device on the track move upwards to the track view
        if (!this.cursorDevice.doesExist ())
        {
            this.surface.getButton (ButtonID.TRACK).trigger (ButtonEvent.DOWN);
            return;
        }

        this.setMode (Modes.DEVICE_PARAMS);
        this.cursorDevice.selectChannel ();
        final ModeManager modeManager = this.surface.getModeManager ();
        ((DeviceParamsMode) modeManager.get (Modes.DEVICE_PARAMS)).setShowDevices (true);
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.DOWN)
            return;

        final ModeManager modeManager = this.surface.getModeManager ();
        switch (index)
        {
            case 0:
                if (modeManager.isActive (Modes.DEVICE_LAYER_VOLUME))
                    this.setMode (Modes.DEVICE_LAYER);
                else
                    this.setMode (Modes.DEVICE_LAYER_VOLUME);
                break;

            case 1:
                if (modeManager.isActive (Modes.DEVICE_LAYER_PAN))
                    this.setMode (Modes.DEVICE_LAYER);
                else
                    this.setMode (Modes.DEVICE_LAYER_PAN);
                break;

            case 2:
                // Not used
                break;

            case 3:
                final boolean isShift = this.surface.isShiftPressed ();
                final int offset = this.getDrumPadIndex ();
                for (int i = 0; i < this.bank.getPageSize (); i++)
                {
                    final ILayer layer = this.bank.getItem (offset + i);
                    final ISendBank sendBank = layer.getSendBank ();
                    if (isShift)
                    {
                        if (sendBank.canScrollPageBackwards ())
                            sendBank.selectPreviousPage ();
                        else
                            sendBank.scrollTo (sendBank.getItemCount () / 4 * 4);
                    }
                    else
                    {
                        if (sendBank.canScrollPageForwards ())
                            sendBank.selectNextPage ();
                        else
                            sendBank.scrollTo (0);
                    }
                }

                break;

            case 7:
                if (this.surface.isShiftPressed ())
                    this.handleSendEffect (3);
                else
                    this.moveUp ();
                break;

            default:
                this.handleSendEffect (index - 4);
                break;
        }
    }


    private void setMode (final Modes layerMode)
    {
        this.surface.getModeManager ().setActive (layerMode);
        if (Modes.isLayerMode (layerMode))
            this.surface.getConfiguration ().setLayerMixMode (layerMode);
    }


    /**
     * Handle the selection of a send effect.
     *
     * @param sendIndex The index of the send
     */
    protected void handleSendEffect (final int sendIndex)
    {
        final ISendBank sendBank = this.bank.getItem (0).getSendBank ();
        if (!sendBank.getItem (sendIndex).doesExist ())
            return;
        final Modes si = Modes.get (Modes.DEVICE_LAYER_SEND1, sendIndex);
        final ModeManager modeManager = this.surface.getModeManager ();
        this.setMode (modeManager.isActive (si) ? Modes.DEVICE_LAYER : si);
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        if (!this.cursorDevice.doesExist ())
        {
            for (int i = 0; i < 8; i++)
                display.addOptionElement (i == 2 ? "Please select a device or press 'Add Device'..." : "", i == 7 ? "Up" : "", true, "", "", false, true);
            return;
        }

        if (this.checkLayerExistance (display))
            this.updateDisplayElements (display, this.bank.getSelectedItem ());
    }


    /**
     * Check if the cursor device has layers and at least one. Otherwise a message is displayed
     *
     * @param display The display where to show the message
     * @return True if layers exist
     */
    protected boolean checkLayerExistance (final IGraphicDisplay display)
    {
        if (!this.cursorDevice.hasLayers ())
        {
            for (int i = 0; i < 8; i++)
                display.addOptionElement (i == 3 ? "This device does not have layers." : "", i == 7 ? "Up" : "", true, "", "", false, true);
            return false;
        }

        if (this.bank.hasExistingItems ())
            return true;

        for (int i = 0; i < 8; i++)
        {
            final String label;
            if (i == 3)
                label = "Please create a " + (this.cursorDevice.hasDrumPads () ? "Drum Pad..." : "Device Layer...");
            else
                label = "";
            display.addOptionElement (label, i == 7 ? "Up" : "", true, "", "", false, true);
        }
        return false;
    }


    /**
     * Update all 8 elements.
     *
     * @param display The display
     * @param l The channel data
     */
    protected void updateDisplayElements (final IGraphicDisplay display, final Optional<ILayer> l)
    {
        // Drum Pad Bank has size of 16, layers only 8
        final int offset = this.getDrumPadIndex ();
        this.updateMenuItems (-1);

        final List<MenuData> menus = new ArrayList<> (8);
        final List<ParameterData> parameters = new ArrayList<> (8);
        final List<TrackData> layers = new ArrayList<> (8);
        for (int i = 0; i < 8; i++)
        {
            final IChannel layer = this.bank.getItem (offset + i);
            final Pair<String, Boolean> pair = this.menu.get (i);
            menus.add (new MenuData (pair.getKey ().trim (), pair.getValue ().booleanValue ()));
            parameters.add (new ParameterData ("", -1, -1, "", false));
            layers.add (new TrackData (layer.doesExist () ? layer.getName (12) : "", ChannelType.LAYER, layer.getColor (), layer.isSelected (), layer.isActivated (), false));
        }

        final IValueChanger valueChanger = this.model.getValueChanger ();
        int vuLeft = 0;
        int vuRight = 0;
        ColorEx controlColor = ColorEx.WHITE;
        if (l.isPresent ())
        {
            final ILayer layer = l.get ();
            final boolean isActive = layer.isActivated ();
            controlColor = layer.getColor ();
            parameters.set (0, new ParameterData ("Layer Volume", valueChanger.toDisplayValue (layer.getVolume ()), valueChanger.toDisplayValue (layer.getModulatedVolume ()), layer.getVolumeStr (8), isActive));
            parameters.set (1, new ParameterData ("Pan", valueChanger.toDisplayValue (layer.getPan ()), valueChanger.toDisplayValue (layer.getModulatedPan ()), this.formatPanValue (layer.getPan ()), isActive));

            final ISendBank sendBank = layer.getSendBank ();
            for (int i = 0; i < 4; i++)
            {
                final ISend send = sendBank.getItem (i);
                if (send.doesExist ())
                    parameters.set (4 + i, new ParameterData (send.getName (), valueChanger.toDisplayValue (send.getValue ()), valueChanger.toDisplayValue (send.getModulatedValue ()), send.getDisplayedValue (8), isActive && send.isEnabled ()));
            }

            if (this.configuration.isEnableVUMeters ())
            {
                vuLeft = valueChanger.toDisplayValue (layer.getVuLeft ());
                vuRight = valueChanger.toDisplayValue (layer.getVuRight ());
            }
        }

        display.addElement (new TrackMixerComponent (menus, parameters, layers, vuLeft, vuRight, controlColor));
    }


    protected String formatPanValue (final int value)
    {
        final double bipolarValue = 2.0 * this.model.getValueChanger ().toNormalizedValue (value) - 1.0;
        final int amount = (int) Math.round (100.0 * Math.abs (bipolarValue));
        if (amount == 0)
            return "C";
        return (bipolarValue < 0 ? "L " : "R ") + amount;
    }


    protected void updateMenuItems (final int selectedMenu)
    {
        this.menu.get (0).set ("Volume", Boolean.valueOf (selectedMenu - 1 == 0));
        this.menu.get (1).set ("Pan", Boolean.valueOf (selectedMenu - 1 == 1));
        this.menu.get (2).set (" ", Boolean.FALSE);

        final ILayerBank layerBank = (ILayerBank) this.bank;
        final int start = Math.max (0, layerBank.getItem (0).getSendBank ().getItem (0).getPosition ()) + 1;
        this.menu.get (3).set (String.format ("Sends %d-%d", Integer.valueOf (start), Integer.valueOf (start + 3)), Boolean.FALSE);

        for (int i = 0; i < 4; i++)
        {
            final String sendName = StringUtils.optimizeName (layerBank.getEditSendName (i), 12);
            this.menu.get (4 + i).set (sendName.isEmpty () ? " " : sendName, Boolean.valueOf (4 + i == selectedMenu - 1));
        }

        if (!this.surface.isShiftPressed () && !this.isKnobTouched (7))
            this.menu.get (7).set ("Up", Boolean.TRUE);
    }


    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        final ICursorDevice cd = this.model.getCursorDevice ();
        if (cd == null || !cd.hasLayers ())
            return super.getButtonColor (buttonID);

        // Drum Pad Bank has size of 16, layers only 8
        final int offset = this.getDrumPadIndex ();

        int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
        {
            final IChannel dl = this.bank.getItem (offset + buttonID.ordinal () - ButtonID.ROW1_1.ordinal ());
            if (dl.doesExist () && dl.isActivated ())
            {
                if (dl.isSelected ())
                    return PushColorManager.PUSH2_COLOR_ORANGE_HI;
                return PushColorManager.PUSH2_COLOR_YELLOW_LO;
            }
            return super.getButtonColor (buttonID);
        }

        index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            final ModeManager modeManager = this.surface.getModeManager ();
            switch (index)
            {
                case 0:
                    return modeManager.isActive (Modes.DEVICE_LAYER_VOLUME) ? PushColorManager.PUSH2_COLOR2_WHITE : PushColorManager.PUSH2_COLOR_BLACK;
                case 1:
                    return modeManager.isActive (Modes.DEVICE_LAYER_PAN) ? PushColorManager.PUSH2_COLOR2_WHITE : PushColorManager.PUSH2_COLOR_BLACK;
                case 4, 5, 6, 7:
                    return modeManager.isActive (Modes.get (Modes.DEVICE_LAYER_SEND1, index - 4)) ? PushColorManager.PUSH2_COLOR2_WHITE : PushColorManager.PUSH2_COLOR_BLACK;
                default:
                    return PushColorManager.PUSH2_COLOR_BLACK;
            }
        }

        return super.getButtonColor (buttonID);
    }


    protected int getDrumPadIndex ()
    {
        if (this.cursorDevice.hasDrumPads ())
        {
            final Optional<ILayer> selectedDrumPad = this.bank.getSelectedItem ();
            if (selectedDrumPad.isPresent () && selectedDrumPad.get ().getIndex () > 7)
                return 8;
        }
        return 0;
    }
}
