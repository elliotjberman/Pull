// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.daw.GrooveParameterID;
import de.mossgrabers.framework.daw.IGroove;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IItem;
import de.mossgrabers.framework.daw.data.empty.EmptyParameter;
import de.mossgrabers.framework.featuregroup.AbstractFeatureGroup;
import de.mossgrabers.framework.featuregroup.AbstractMode;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.parameterprovider.special.FixedParameterProvider;
import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * Editing of groove parameters.
 *
 * @author Jürgen Moßgraber
 */
public class GrooveMode extends BaseMode<IItem>
{
    private static final String TAG_GROOVE = "Groove";

    final IParameter []         params     = new IParameter [8];


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public GrooveMode (final PushControlSurface surface, final IModel model)
    {
        super (TAG_GROOVE, surface, model);

        final IGroove groove = this.model.getGroove ();

        this.params[2] = groove.getParameter (GrooveParameterID.SHUFFLE_AMOUNT);
        this.params[3] = groove.getParameter (GrooveParameterID.SHUFFLE_RATE);

        this.params[5] = groove.getParameter (GrooveParameterID.ACCENT_AMOUNT);
        this.params[6] = groove.getParameter (GrooveParameterID.ACCENT_PHASE);
        this.params[7] = groove.getParameter (GrooveParameterID.ACCENT_RATE);

        for (int i = 0; i < this.params.length; i++)
        {
            if (this.params[i] == null)
                this.params[i] = EmptyParameter.INSTANCE;
        }

        this.setParameterProvider (new FixedParameterProvider (this.params));
    }


    /** {@inheritDoc} */
    @Override
    public void onActivate ()
    {
        super.onActivate ();

        this.setActive (true);
    }


    /** {@inheritDoc} */
    @Override
    public void onDeactivate ()
    {
        super.onDeactivate ();

        this.setActive (false);
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        if (isTouched && this.surface.isDeletePressed ())
        {
            this.surface.setTriggerConsumed (ButtonID.DELETE);
            this.params[index].resetValue ();
        }

        this.params[index].touchValue (isTouched);
    }


    /** {@inheritDoc} */
    @Override
    public void onFirstRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;

        if (index == 0)
        {
            final IParameter parameter = this.model.getGroove ().getParameter (GrooveParameterID.ENABLED);
            parameter.setNormalizedValue (parameter.getValue () > 0 ? 0 : 1);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.UP)
            return;

        if (index == 0)
            this.surface.getModeManager ().setActive (Modes.REC_ARM);
    }


    /** {@inheritDoc} */
    @Override
    public String getButtonColorID (final ButtonID buttonID)
    {
        int index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            if (index == 0)
                return AbstractFeatureGroup.BUTTON_COLOR_ON;
            if (index == 1)
                return AbstractMode.BUTTON_COLOR_HI;
        }
        else
        {
            index = this.isButtonRow (0, buttonID);
            if (index == 0)
            {
                final IParameter parameter = this.model.getGroove ().getParameter (GrooveParameterID.ENABLED);
                if (parameter != null)
                    return parameter.getValue () > 0 ? AbstractMode.BUTTON_COLOR_HI : AbstractFeatureGroup.BUTTON_COLOR_ON;
            }
        }
        return AbstractFeatureGroup.BUTTON_COLOR_OFF;
    }


    private void setActive (final boolean enable)
    {
        final IGroove groove = this.model.getGroove ();
        groove.enableObservers (enable);
        groove.setIndication (enable);
    }
}
