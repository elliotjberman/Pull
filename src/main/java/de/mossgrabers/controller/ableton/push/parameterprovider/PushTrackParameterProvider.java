// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.parameterprovider;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IChannel;
import de.mossgrabers.framework.daw.data.empty.EmptyParameter;
import de.mossgrabers.framework.observer.ISettingObserver;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.parameterprovider.track.SelectedTrackParameterProvider;


/**
 * Extends channel parameter provider with the specific layout of Push 2.
 *
 * @author Jürgen Moßgraber
 */
public class PushTrackParameterProvider extends SelectedTrackParameterProvider implements ISettingObserver
{
    private final PushConfiguration configuration;
    private final IValueChanger     valueChanger;


    /**
     * Constructor.
     *
     * @param model Uses the current channel bank from this model to get the parameters
     * @param configuration The configuration
     */
    public PushTrackParameterProvider (final IModel model, final PushConfiguration configuration)
    {
        super (model);

        this.configuration = configuration;
        this.valueChanger = model.getValueChanger ();
    }


    /** {@inheritDoc} */
    @Override
    protected IParameter getInternal (final int index, final IChannel selectedChannel)
    {
        if (index == 0)
            return new PushVolumeParameter (super.getInternal (index, selectedChannel), this.valueChanger);
        if (index == 1)
            return new PushPanParameter (super.getInternal (index, selectedChannel), this.valueChanger);

        final int sendIndex = this.configuration.getTrackMixSendOffset () + index - 2;
        return sendIndex < selectedChannel.getSendBank ().getPageSize () ? this.getSend (sendIndex, selectedChannel) : EmptyParameter.INSTANCE;
    }


    /** {@inheritDoc} */
    @Override
    public void hasChanged ()
    {
        // Sends are toggled...
        this.notifyParametersObservers ();
    }
}
