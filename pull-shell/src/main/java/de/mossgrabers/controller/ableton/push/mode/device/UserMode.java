// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;

import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.IParameterBank;
import de.mossgrabers.framework.daw.data.bank.IParameterPageBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.parameterprovider.device.BankParameterProvider;
import de.mossgrabers.framework.utils.ButtonEvent;

import java.util.Optional;


/**
 * Mode for editing user control parameters.
 *
 * @author Jürgen Moßgraber
 */
public class UserMode extends BaseMode<IParameter>
{
    private static final String []      TOP_MENU      =
    {
        "Project",
        " ",
        " ",
        " ",
        " ",
        " ",
        " ",
        " "
    };

    private final BankParameterProvider projectParameterProvider;
    private final BankParameterProvider trackParameterProvider;

    private boolean                     isProjectMode = true;


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public UserMode (final PushControlSurface surface, final IModel model)
    {
        super ("Project/Track Controls", surface, model, model.getProject ().getParameterBank ());

        this.projectParameterProvider = new BankParameterProvider (model.getProject ().getParameterBank ());
        this.trackParameterProvider = new BankParameterProvider (model.getCursorTrack ().getParameterBank ());
        this.setParameterProvider (this.projectParameterProvider);
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        this.setTouchedKnob (index, isTouched);

        final IParameter param = this.bank.getItem (index);
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
        // TODO(Pull views API): The view/session model should declare the rendered bottom menu and
        // its button actions together. Until then, explicitly keep this row aligned with the track
        // menu rendered by updateDisplay2 instead of the old parameter-page action.
        super.onFirstRow (index, event);
    }


    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        final int offColor = PushColorManager.PUSH2_COLOR_BLACK;

        int index = this.isButtonRow (0, buttonID);
        if (index >= 0)
        {
            final int selectedColor = PushColorManager.PUSH2_COLOR_ORANGE_HI;
            final int existsColor = PushColorManager.PUSH2_COLOR_YELLOW_LO;

            final IParameterPageBank parameterPageBank = ((IParameterBank) this.bank).getPageBank ();
            if (parameterPageBank.getItem (index).isBlank ())
                return offColor;

            final int selectedPage = parameterPageBank.getSelectedItemIndex ();
            return index == selectedPage ? selectedColor : existsColor;
        }

        index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            if (index > 1)
                return offColor;

            final int selectedColor = PushColorManager.PUSH2_COLOR2_WHITE;
            final int existsColor = PushColorManager.PUSH2_COLOR2_GREY_LO;
            return index == 0 && this.isProjectMode || index == 1 && !this.isProjectMode ? selectedColor : existsColor;
        }

        return super.getButtonColor (buttonID);
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event == ButtonEvent.UP && index <= 1)
            this.setMode (index == 0);
    }


    private void setMode (final boolean isProjectMode)
    {
        this.isProjectMode = isProjectMode;
        this.switchBanks (this.isProjectMode ? this.model.getProject ().getParameterBank () : this.model.getCursorTrack ().getParameterBank ());
        this.setParameterProvider (this.isProjectMode ? this.projectParameterProvider : this.trackParameterProvider);
        this.bindControls ();
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay2 (final IGraphicDisplay display)
    {
        final IValueChanger valueChanger = this.model.getValueChanger ();
        final ITrackBank trackBank = this.model.getCurrentTrackBank ();
        final Optional<ITrack> selectedTrack = trackBank.getSelectedItem ();
        final String trackHeader = selectedTrack.isEmpty () ? "None" : selectedTrack.get ().getName ();

        for (int i = 0; i < this.bank.getPageSize (); i++)
        {
            final IParameter param = this.bank.getItem (i);
            final boolean exists = param.doesExist ();
            final String parameterName = exists ? param.getName (16) : "";
            final int parameterValue = valueChanger.toDisplayValue (exists ? param.getValue () : 0);
            final String parameterValueStr = exists ? param.getDisplayedValue (8) : "";
            final boolean parameterIsActive = this.isKnobTouched (i);
            final int parameterModulatedValue = valueChanger.toDisplayValue (exists ? param.getModulatedValue () : -1);

            final ITrack track = trackBank.getItem (i);
            final String bottomMenu = track.doesExist () ? track.getName (16) : "";
            final boolean isTopMenuSelected = i == 0 && this.isProjectMode || i == 1 && !this.isProjectMode;

            display.addParameterElement (i == 1 ? trackHeader : TOP_MENU[i], isTopMenuSelected, bottomMenu, track.getType (), track.getColor (), track.isSelected (), parameterName, parameterValue, parameterValueStr, parameterIsActive, parameterModulatedValue);
        }
    }
}
