// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.track;

import java.util.ArrayList;
import java.util.List;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ISend;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ISendBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.resource.ChannelType;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.utils.Pair;


/**
 * Abstract base mode for all track modes.
 *
 * @author Jürgen Moßgraber
 */
public abstract class AbstractTrackMode extends BaseMode<ITrack>
{
    protected static final int                       GLOBAL_CROSSFADER_MENU = 10;

    protected final List<Pair<String, Boolean>> menu = new ArrayList<> ();
    protected final PushConfiguration           configuration;


    /**
     * Constructor.
     *
     * @param name The name of the mode
     * @param surface The control surface
     * @param model The model
     */
    protected AbstractTrackMode (final String name, final PushControlSurface surface, final IModel model)
    {
        super (name, surface, model, model.getCurrentTrackBank ());

        this.configuration = this.surface.getConfiguration ();

        model.addTrackBankObserver (this::switchBanks);

        for (int i = 0; i < 8; i++)
            this.menu.add (new Pair<> (" ", Boolean.FALSE));
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        this.setTouchedKnob (index, isTouched);

        final IParameter parameter = this.getParameterProvider ().get (index);

        if (isTouched && this.surface.isDeletePressed ())
        {
            this.surface.setTriggerConsumed (ButtonID.DELETE);
            parameter.resetValue ();
        }

        parameter.touchValue (isTouched);
        this.checkStopAutomationOnKnobRelease (isTouched);
    }


    /** {@inheritDoc} */
    @Override
    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event == ButtonEvent.DOWN)
            return;

        final ITrackBank tb = this.model.getCurrentTrackBank ();
        final ITrack track = tb.getItem (index);

        if (event == ButtonEvent.UP)
        {
            if (this.surface.isPressed (ButtonID.DUPLICATE))
            {
                this.surface.setTriggerConsumed (ButtonID.DUPLICATE);
                track.duplicate ();
                return;
            }

            if (this.surface.isPressed (ButtonID.DELETE))
            {
                this.surface.setTriggerConsumed (ButtonID.DELETE);
                track.remove ();
                return;
            }

            if (this.surface.isPressed (ButtonID.RECORD))
            {
                this.surface.setTriggerConsumed (ButtonID.RECORD);
                track.toggleRecArm ();
                return;
            }

            if (this.surface.isSelectPressed ())
            {
                this.surface.setTriggerConsumed (ButtonID.SELECT);
                track.toggleMultiSelect ();
                return;
            }
            else if (!track.isSelected ())
            {
                track.select ();
                return;
            }

            // If it is a group display child channels of group, otherwise jump into device
            // mode
            if (track.isGroup ())
            {
                if (this.surface.isShiftPressed ())
                    track.toggleGroupExpanded ();
                else
                {
                    track.setGroupExpanded (true);
                    track.enter ();
                }
            }
            else
                this.surface.getButton (ButtonID.DEVICE).trigger (ButtonEvent.DOWN);
            return;
        }

        // LONG press, go out of group
        this.model.getTrackBank ().selectParent ();
        this.surface.setTriggerConsumed (ButtonID.get (ButtonID.ROW1_1, index));
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.DOWN)
            return;

        final ITrackBank tb = this.model.getCurrentTrackBank ();
        final ITrack track = tb.getItem (index);

        final ModeManager modeManager = this.surface.getModeManager ();
        switch (index)
        {
            case 0:
                this.activateGlobalMixMode (modeManager, Modes.VOLUME);
                break;

            case 1:
                this.activateGlobalMixMode (modeManager, Modes.PAN);
                break;

            case 7:
                this.activateGlobalMixMode (modeManager, Modes.CROSSFADER);
                break;

            default:
                final boolean hasAdditionalSends = this.hasAdditionalSends ();
                final int sendOffset = hasAdditionalSends ? this.configuration.getMixSendOffset () : 0;
                if (!hasAdditionalSends)
                    this.configuration.setMixSendOffset (0);
                if (hasAdditionalSends && (sendOffset == 0 && index == 6 || sendOffset > 0 && index == 2))
                {
                    this.configuration.setMixSendOffset (sendOffset == 0 ? 4 : 0);
                    break;
                }

                final int sendIndex = sendOffset == 0 ? index - 2 : index - 3 + sendOffset;
                if (sendIndex >= 0 && sendIndex < 8)
                    this.activateGlobalMixMode (modeManager, Modes.get (Modes.SEND1, sendIndex));
                break;
        }
    }


    private void activateGlobalMixMode (final ModeManager modeManager, final Modes mode)
    {
        this.configuration.setGlobalMixMode (mode);
        modeManager.setActive (mode);
    }


    /** {@inheritDoc} */
    @Override
    public void selectPreviousItemPage ()
    {
        if (this.surface.isShiftPressed ())
            this.model.getCursorTrack ().swapWithPrevious ();
        else
            super.selectPreviousItemPage ();
    }


    /** {@inheritDoc} */
    @Override
    public void selectNextItemPage ()
    {
        if (this.surface.isShiftPressed ())
            this.model.getCursorTrack ().swapWithNext ();
        else
            super.selectNextItemPage ();
    }


    /**
     * Handle the selection of a send effect.
     *
     * @param sendIndex The index of the send
     */
    protected void handleSendEffect (final int sendIndex)
    {
        final ITrackBank tb = this.model.getCurrentTrackBank ();
        if (tb == null || !tb.canEditSend (sendIndex))
            return;
        final Modes si = Modes.get (Modes.SEND1, sendIndex);
        final ModeManager modeManager = this.surface.getModeManager ();
        modeManager.setActive (modeManager.isActive (si) ? Modes.TRACK : si);
    }


    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        final ITrackBank tb = this.model.getCurrentTrackBank ();

        int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
        {
            final ITrack track = tb.getItem (index);
            if (!track.doesExist () || !track.isActivated ())
                return this.colorManager.getColorIndex (PushColorManager.PUSH_BLACK);

            if (track.isRecArm ())
                return this.colorManager.getColorIndex (PushColorManager.PUSH_RED_HI);

            return this.colorManager.getColorIndex (track.getColor ());
        }

        index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            final ModeManager modeManager = this.surface.getModeManager ();
            this.updateMenuItems (this.getGlobalControlIndex (modeManager.getActiveID ()));
            final Pair<String, Boolean> menuItem = this.menu.get (index);
            return menuItem.getValue ().booleanValue () || "<".equals (menuItem.getKey ()) || ">".equals (menuItem.getKey ()) ? PushColorManager.PUSH2_COLOR2_WHITE : PushColorManager.PUSH2_COLOR_BLACK;
        }

        return super.getButtonColor (buttonID);
    }


    protected void updateMenuItems (final int selectedMenu)
    {
        for (int i = 0; i < 8; i++)
            this.menu.get (i).set (" ", Boolean.FALSE);

        this.menu.get (0).set ("Volume", Boolean.valueOf (selectedMenu == 0));
        this.menu.get (1).set ("Pan", Boolean.valueOf (selectedMenu == 1));
        this.menu.get (7).set ("Crossfader", Boolean.valueOf (selectedMenu == GLOBAL_CROSSFADER_MENU));

        final boolean hasAdditionalSends = this.hasAdditionalSends ();
        final int sendOffset = hasAdditionalSends ? this.configuration.getMixSendOffset () : 0;
        if (!hasAdditionalSends)
            this.configuration.setMixSendOffset (0);
        if (!hasAdditionalSends)
        {
            for (int i = 0; i < 5; i++)
                this.setSendMenuItem (i + 2, i, selectedMenu);
            return;
        }

        if (sendOffset == 0)
        {
            for (int i = 0; i < 4; i++)
                this.setSendMenuItem (i + 2, i, selectedMenu);
            this.menu.get (6).set (">", Boolean.FALSE);
            return;
        }

        this.menu.get (2).set ("<", Boolean.FALSE);
        for (int i = 0; i < 4; i++)
            this.setSendMenuItem (i + 3, sendOffset + i, selectedMenu);
    }


    private void setSendMenuItem (final int menuIndex, final int sendIndex, final int selectedMenu)
    {
        final ISendBank sendBank = this.model.getCursorTrack ().getSendBank ();
        final ISend send = sendIndex < sendBank.getPageSize () ? sendBank.getItem (sendIndex) : null;
        final boolean exists = send != null && send.doesExist ();
        this.menu.get (menuIndex).set (exists ? send.getName () : " ", Boolean.valueOf (exists && selectedMenu == sendIndex + 2));
    }


    protected boolean hasAdditionalSends ()
    {
        final ISendBank sendBank = this.model.getCursorTrack ().getSendBank ();
        return sendBank.getPageSize () > 5 && sendBank.getItem (5).doesExist ();
    }


    private int getGlobalControlIndex (final Modes mode)
    {
        if (mode == Modes.VOLUME)
            return 0;
        if (mode == Modes.PAN)
            return 1;
        if (mode == Modes.CROSSFADER)
            return GLOBAL_CROSSFADER_MENU;
        if (mode != null && mode.ordinal () >= Modes.SEND1.ordinal () && mode.ordinal () <= Modes.SEND8.ordinal ())
            return 2 + mode.ordinal () - Modes.SEND1.ordinal ();
        return -1;
    }


    protected String formatPanValue (final int value)
    {
        final double bipolarValue = 2.0 * this.model.getValueChanger ().toNormalizedValue (value) - 1.0;
        final int amount = (int) Math.round (100.0 * Math.abs (bipolarValue));
        if (amount == 0)
            return "C";
        return (bipolarValue < 0 ? "L " : "R ") + amount;
    }


    /**
     * Update the group type, if it is an opened group.
     *
     * @param track The track for which to get the type
     * @return The type
     */
    protected ChannelType updateType (final ITrack track)
    {
        final ChannelType type = track.getType ();
        return type == ChannelType.GROUP && track.isGroupExpanded () ? ChannelType.GROUP_OPEN : type;
    }
}
