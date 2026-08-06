// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;

import java.util.Optional;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.daw.data.IDevice;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.IDeviceBank;
import de.mossgrabers.framework.daw.data.bank.IParameterBank;
import de.mossgrabers.framework.daw.data.bank.IParameterPageBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.parameterprovider.device.BankParameterProvider;
import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * Mode for editing device remote control parameters.
 *
 * @author Jürgen Moßgraber
 */
public class DeviceParamsMode extends BaseMode<IParameter>
{
    private static final String [] MENU     =
    {
        "On",
        "Parameters",
        "Expanded",
        "Chains",
        "Banks",
        "Pin Device",
        "Window",
        "Up"
    };

    protected final String []      hostMenu = new String [MENU.length];
    protected boolean              showDevices;


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public DeviceParamsMode (final PushControlSurface surface, final IModel model)
    {
        super ("Parameters", surface, model, model.getCursorDevice ().getParameterBank ());

        this.setParameterProvider (new BankParameterProvider (this.model.getCursorDevice ().getParameterBank ()));

        this.setShowDevices (true);

        System.arraycopy (MENU, 0, this.hostMenu, 0, MENU.length);
    }


    /**
     * Show devices or the parameter banks of the cursor device for selection.
     *
     * @param enable True to enable
     */
    public final void setShowDevices (final boolean enable)
    {
        this.showDevices = enable;
    }


    /**
     * Returns true if devices are shown otherwise parameter banks.
     *
     * @return True if devices are shown otherwise parameter banks
     */
    public boolean isShowDevices ()
    {
        return this.showDevices;
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        this.setTouchedKnob (index, isTouched);

        final ICursorDevice cd = this.model.getCursorDevice ();
        final IParameter param = cd.getParameterBank ().getItem (index);
        if (isTouched && this.surface.isDeletePressed ())
        {
            this.surface.setTriggerConsumed (ButtonID.DELETE);
            param.resetValue ();
        }
        param.touchValue (isTouched);
        this.checkStopAutomationOnKnobRelease (isTouched);
    }


    /** {@inheritDoc} */
    @Override
    public void onFirstRow (final int index, final ButtonEvent event)
    {
        final ICursorDevice cursorDevice = this.model.getCursorDevice ();
        if (!this.showDevices)
        {
            if (event == ButtonEvent.UP && cursorDevice.doesExist ())
                cursorDevice.getParameterBank ().getPageBank ().selectPage (index);
            return;
        }

        final ITrack track = this.model.getCurrentTrackBank ().getItem (index);
        if (!track.doesExist ())
            return;

        if (event == ButtonEvent.LONG)
        {
            track.toggleRecArm ();
            this.surface.setTriggerConsumed (ButtonID.get (ButtonID.ROW1_1, index));
            return;
        }

        if (event != ButtonEvent.UP)
            return;

        if (this.isButtonCombination (ButtonID.DUPLICATE))
        {
            track.duplicate ();
            return;
        }

        if (this.isButtonCombination (ButtonID.DELETE))
        {
            track.remove ();
            return;
        }

        if (this.isButtonCombination (ButtonID.RECORD))
        {
            track.toggleRecArm ();
            return;
        }

        final PushConfiguration configuration = this.surface.getConfiguration ();
        if (configuration.isMuteState (this.surface.isLongPressed (ButtonID.MUTE)))
        {
            this.surface.setTriggerConsumed (ButtonID.MUTE);
            track.toggleMute ();
            return;
        }
        if (configuration.isSoloState (this.surface.isLongPressed (ButtonID.SOLO)))
        {
            this.surface.setTriggerConsumed (ButtonID.SOLO);
            track.toggleSolo ();
            return;
        }
        if (configuration.isClipStopState (this.surface.isLongPressed (ButtonID.STOP_CLIP)))
        {
            this.surface.setTriggerConsumed (ButtonID.STOP_CLIP);
            track.stop (true);
            return;
        }

        if (this.surface.isSelectPressed ())
        {
            this.surface.setTriggerConsumed (ButtonID.SELECT);
            track.toggleMultiSelect ();
            return;
        }

        if (!track.isSelected ())
            track.select ();
        else if (track.isGroup ())
        {
            if (this.surface.isShiftPressed () || configuration.isTrackNavigationFlat ())
                track.toggleGroupExpanded ();
            else
            {
                track.setGroupExpanded (true);
                track.enter ();
            }
        }
    }


    private void onDeviceButton (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;

        final ICursorDevice cursorDevice = this.model.getCursorDevice ();
        final IDevice device = cursorDevice.getDeviceBank ().getItem (index);
        if (!device.doesExist ())
            return;

        if (this.isButtonCombination (ButtonID.DUPLICATE))
        {
            device.duplicate ();
            return;
        }
        if (this.isButtonCombination (ButtonID.DELETE))
        {
            device.remove ();
            return;
        }
        if (this.isButtonCombination (ButtonID.MUTE))
        {
            device.toggleEnabledState ();
            return;
        }

        if (cursorDevice.getIndex () != index)
        {
            device.select ();
            return;
        }

        final ModeManager modeManager = this.surface.getModeManager ();
        if (!cursorDevice.hasLayers ())
        {
            this.setShowDevices (false);
            return;
        }

        final Optional<?> layer = cursorDevice.getLayerBank ().getSelectedItem ();
        if (layer.isEmpty ())
            cursorDevice.getLayerBank ().getItem (0).select ();
        modeManager.setActive (this.surface.getConfiguration ().getCurrentLayerMixMode ());
    }


    /**
     * Move up the hierarchy.
     */
    protected void moveUp ()
    {
        final ModeManager modeManager = this.surface.getModeManager ();
        if (modeManager.isActive (Modes.DEVICE_CHAINS))
        {
            modeManager.setActive (Modes.DEVICE_PARAMS);
            return;
        }

        // There is no device on the track move upwards to the track view
        final ICursorDevice cd = this.model.getCursorDevice ();
        if (!cd.doesExist ())
        {
            this.surface.getButton (ButtonID.TRACK).trigger (ButtonEvent.DOWN);
            return;
        }

        // Parameter banks are shown -> show devices
        final DeviceParamsMode deviceParamsMode = (DeviceParamsMode) modeManager.get (Modes.DEVICE_PARAMS);
        if (!deviceParamsMode.isShowDevices ())
        {
            deviceParamsMode.setShowDevices (true);
            return;
        }

        // Devices are shown, if nested show the layers otherwise move up to the tracks
        if (cd.isNested ())
        {
            cd.selectParent ();
            this.model.getHost ().scheduleTask (() -> {
                if (cd.hasLayers ())
                    modeManager.setActive (this.surface.getConfiguration ().getCurrentLayerMixMode ());
                else
                    modeManager.setActive (Modes.DEVICE_PARAMS);
                deviceParamsMode.setShowDevices (false);
                cd.selectChannel ();
            }, 300);
            return;
        }

        // Move up to the track
        if (this.model.isCursorDeviceOnMasterTrack ())
            this.surface.getButton (ButtonID.MASTERTRACK).trigger (ButtonEvent.DOWN);
        else
            this.surface.getButton (ButtonID.TRACK).trigger (ButtonEvent.DOWN);
    }


    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        final ICursorDevice cd = this.model.getCursorDevice ();

        int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
        {
            final int offColor = this.colorManager.getColorIndex (PushColorManager.PUSH_BLACK);
            if (this.showDevices)
            {
                final ITrack track = this.model.getCurrentTrackBank ().getItem (index);
                if (!track.doesExist () || !track.isActivated ())
                    return offColor;
                if (track.isRecArm ())
                    return this.colorManager.getColorIndex (PushColorManager.PUSH_RED_HI);
                return this.colorManager.getColorIndex (track.getColor ());
            }

            if (!cd.doesExist ())
                return offColor;

            final int selectedColor = this.colorManager.getColorIndex (PushColorManager.PUSH_ORANGE_HI);
            final int existsColor = this.colorManager.getColorIndex (PushColorManager.PUSH_YELLOW_LO);
            final IParameterPageBank bank = cd.getParameterBank ().getPageBank ();
            final int selectedItemIndex = bank.getSelectedItemIndex ();
            if (bank.getItem (index).isEmpty ())
                return offColor;
            return index == selectedItemIndex ? selectedColor : existsColor;
        }

        index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            if (this.showDevices)
            {
                if (!cd.doesExist ())
                    return PushColorManager.PUSH2_COLOR_BLACK;
                final IDevice device = cd.getDeviceBank ().getItem (index);
                if (!device.doesExist ())
                    return PushColorManager.PUSH2_COLOR_BLACK;
                return index == cd.getIndex () ? PushColorManager.PUSH2_COLOR2_ORANGE : PushColorManager.PUSH2_COLOR2_YELLOW_LO;
            }

            final int white = PushColorManager.PUSH2_COLOR2_WHITE;
            if (!cd.doesExist ())
                return index == 7 ? white : super.getButtonColor (buttonID);

            final int green = PushColorManager.PUSH2_COLOR2_GREEN;
            final int grey = PushColorManager.PUSH2_COLOR2_GREY_LO;
            final int orange = PushColorManager.PUSH2_COLOR2_ORANGE;
            final int turquoise = PushColorManager.PUSH2_COLOR2_TURQUOISE_HI;

            switch (index)
            {
                case 0:
                    return cd.isEnabled () ? green : grey;
                case 1:
                    return cd.isParameterPageSectionVisible () ? orange : white;
                case 2:
                    return cd.isExpanded () ? orange : white;
                case 3:
                    return this.surface.getModeManager ().isActive (Modes.DEVICE_CHAINS) ? orange : white;
                case 4:
                    return this.showDevices ? white : orange;
                case 5:
                    return cd.isPinned () ? turquoise : grey;
                case 6:
                    return cd.isWindowOpen () ? turquoise : grey;
                default:
                case 7:
                    return white;
            }
        }

        return super.getButtonColor (buttonID);
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (this.showDevices)
        {
            this.onDeviceButton (index, event);
            return;
        }

        if (event != ButtonEvent.DOWN)
            return;
        final ICursorDevice device = this.model.getCursorDevice ();
        final ModeManager modeManager = this.surface.getModeManager ();
        switch (index)
        {
            case 0:
                if (device.doesExist ())
                    device.toggleEnabledState ();
                break;
            case 1:
                if (device.doesExist ())
                    device.toggleParameterPageSectionVisible ();
                break;
            case 2:
                if (device.doesExist ())
                    device.toggleExpanded ();
                break;
            case 3:
                if (modeManager.isActive (Modes.DEVICE_CHAINS))
                    modeManager.setActive (Modes.DEVICE_PARAMS);
                else
                    modeManager.setActive (Modes.DEVICE_CHAINS);
                break;
            case 4:
                if (!device.doesExist ())
                    return;
                if (!modeManager.isActive (Modes.DEVICE_PARAMS))
                    modeManager.setActive (Modes.DEVICE_PARAMS);
                this.setShowDevices (!this.showDevices);
                break;
            case 5:
                if (device.doesExist ())
                    device.togglePinned ();
                break;
            case 6:
                if (device.doesExist ())
                    device.toggleWindowOpen ();
                break;
            case 7:
                this.moveUp ();
                break;
            default:
                // Not used
                break;
        }
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        final ICursorDevice cd = this.model.getCursorDevice ();
        final ITrackBank trackBank = this.model.getCurrentTrackBank ();
        final IDeviceBank deviceBank = cd.getDeviceBank ();
        final IParameterBank parameterBank = cd.getParameterBank ();
        final IParameterPageBank parameterPageBank = parameterBank.getPageBank ();
        final IValueChanger valueChanger = this.model.getValueChanger ();
        for (int i = 0; i < parameterBank.getPageSize (); i++)
        {
            final IParameter param = parameterBank.getItem (i);
            final boolean exists = param.doesExist ();
            final String parameterName = exists ? param.getName (16) : "";
            final int parameterValue = valueChanger.toDisplayValue (exists ? param.getValue () : 0);
            final String parameterValueStr = exists ? param.getDisplayedValue (8) : "";
            final boolean parameterIsActive = this.isKnobTouched (i);
            final int parameterModulatedValue = valueChanger.toDisplayValue (exists ? param.getModulatedValue () : -1);

            if (this.showDevices)
            {
                final IDevice device = deviceBank.getItem (i);
                final ITrack track = trackBank.getItem (i);
                display.addParameterElement (device.doesExist () ? device.getName (16) : "", device.doesExist () && i == cd.getIndex (), track.doesExist () ? track.getName (16) : "", track.getType (), track.getColor (), track.isSelected (), parameterName, parameterValue, parameterValueStr, parameterIsActive, parameterModulatedValue);
            }
            else
            {
                final String pageName = parameterPageBank.getItem (i);
                final ITrack selectedTrack = trackBank.getSelectedItem ().orElse (null);
                display.addParameterElementWithPlainMenu (this.hostMenu[i], this.getTopMenuEnablement (cd, true, i), pageName, selectedTrack == null ? null : selectedTrack.getColor (), i == parameterPageBank.getSelectedItemIndex (), parameterName, parameterValue, parameterValueStr, parameterIsActive, parameterModulatedValue);
            }
        }
    }


    /** {@inheritDoc} */
    @Override
    public void selectPreviousItem ()
    {
        if (this.showDevices)
        {
            final ICursorDevice cursorDevice = this.model.getCursorDevice ();
            cursorDevice.getDeviceBank ().selectPreviousItem ();
            return;
        }
        super.selectPreviousItem ();
    }


    /** {@inheritDoc} */
    @Override
    public void selectNextItem ()
    {
        if (this.showDevices)
        {
            final ICursorDevice cursorDevice = this.model.getCursorDevice ();
            cursorDevice.getDeviceBank ().selectNextItem ();
            return;
        }
        super.selectNextItem ();
    }


    /** {@inheritDoc} */
    @Override
    public void selectPreviousItemPage ()
    {
        if (this.showDevices)
        {
            final ICursorDevice cursorDevice = this.model.getCursorDevice ();
            if (this.surface.isShiftPressed ())
                cursorDevice.swapWithPrevious ();
            else
                cursorDevice.getDeviceBank ().selectPreviousPage ();
            return;
        }
        super.selectPreviousItemPage ();
    }


    /** {@inheritDoc} */
    @Override
    public void selectNextItemPage ()
    {
        if (this.showDevices)
        {
            final ICursorDevice cursorDevice = this.model.getCursorDevice ();
            if (this.surface.isShiftPressed ())
                cursorDevice.swapWithNext ();
            else
                cursorDevice.getDeviceBank ().selectNextPage ();
            return;
        }
        super.selectNextItemPage ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasPreviousItem ()
    {
        if (this.showDevices)
            return this.model.getCursorDevice ().getDeviceBank ().canScrollBackwards ();
        return super.hasPreviousItem ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasNextItem ()
    {
        if (this.showDevices)
            return this.model.getCursorDevice ().getDeviceBank ().canScrollForwards ();
        return super.hasNextItem ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasPreviousItemPage ()
    {
        if (this.showDevices)
        {
            final ICursorDevice cursorDevice = this.model.getCursorDevice ();
            if (this.surface.isShiftPressed ())
                return cursorDevice.getIndex () > 0;
            return cursorDevice.getDeviceBank ().canScrollPageBackwards ();
        }
        return super.hasPreviousItemPage ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasNextItemPage ()
    {
        if (this.showDevices)
        {
            final ICursorDevice cursorDevice = this.model.getCursorDevice ();
            if (this.surface.isShiftPressed ())
                return cursorDevice.getIndex () < 7;
            return cursorDevice.getDeviceBank ().canScrollPageForwards ();
        }
        return super.hasNextItemPage ();
    }


    protected boolean checkExists2 (final IGraphicDisplay display, final ICursorDevice cd)
    {
        if (cd.doesExist ())
            return true;
        for (int i = 0; i < 8; i++)
            display.addOptionElement (i == 2 ? "Please select a device or press 'Add Device'..." : "", i == 7 ? "Up" : "", true, "", "", false, true);
        return false;
    }


    protected boolean getTopMenuEnablement (final ICursorDevice cd, final boolean hasPinning, final int index)
    {
        switch (index)
        {
            case 0:
                return cd.isEnabled ();
            case 1:
                return cd.isParameterPageSectionVisible ();
            case 2:
                return cd.isExpanded ();
            case 3:
                return this.surface.getModeManager ().isActive (Modes.DEVICE_CHAINS);
            case 4:
                return !this.surface.getModeManager ().isActive (Modes.DEVICE_CHAINS) && !this.showDevices;
            case 5:
                return hasPinning && cd.isPinned ();
            case 6:
                return cd.isWindowOpen ();
            case 7:
                return true;
            default:
                // Not used
                return false;
        }
    }
}
