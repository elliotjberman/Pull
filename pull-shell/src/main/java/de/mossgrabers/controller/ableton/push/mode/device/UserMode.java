// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.device;

import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.BaseMode;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.bank.IParameterBank;
import de.mossgrabers.framework.daw.data.bank.IParameterPageBank;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.parameterprovider.device.BankParameterProvider;
import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * Mode for editing user control parameters.
 *
 * @author Jürgen Moßgraber
 */
public class UserMode extends BaseMode<IParameter>
{
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
        // current-bank footer instead of the old parameter-page action.
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


    /** Existing selected parameter bank; observed by the core display bridge. */
    public boolean isProjectMode () { return this.isProjectMode; }

    private void setMode (final boolean isProjectMode)
    {
        this.isProjectMode = isProjectMode;
        this.switchBanks (this.isProjectMode ? this.model.getProject ().getParameterBank () : this.model.getCursorTrack ().getParameterBank ());
        this.setParameterProvider (this.isProjectMode ? this.projectParameterProvider : this.trackParameterProvider);
        this.bindControls ();
    }


}
