// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.parameterprovider;

import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.parameterprovider.track.PanParameterProvider;


/**
 * Provides center-detented pan parameters for the visible track bank.
 */
public class PushPanParameterProvider extends PanParameterProvider
{
    private final IValueChanger valueChanger;


    /**
     * Constructor.
     *
     * @param model The model
     */
    public PushPanParameterProvider (final IModel model)
    {
        super (model);

        this.valueChanger = model.getValueChanger ();
    }


    /** {@inheritDoc} */
    @Override
    public IParameter get (final int index)
    {
        return new PushPanParameter (super.get (index), this.valueChanger);
    }
}
