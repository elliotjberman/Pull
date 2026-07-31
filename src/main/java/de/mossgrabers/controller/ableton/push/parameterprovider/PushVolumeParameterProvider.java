// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.parameterprovider;

import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.parameterprovider.track.VolumeParameterProvider;


/**
 * Provides Push-curved volume parameters for the visible track bank.
 */
public class PushVolumeParameterProvider extends VolumeParameterProvider
{
    private final IValueChanger valueChanger;


    /**
     * Constructor.
     *
     * @param model The model
     */
    public PushVolumeParameterProvider (final IModel model)
    {
        super (model);

        this.valueChanger = model.getValueChanger ();
    }


    /** {@inheritDoc} */
    @Override
    public IParameter get (final int index)
    {
        return new PushVolumeParameter (super.get (index), this.valueChanger);
    }
}
