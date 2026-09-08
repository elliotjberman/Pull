// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.track;


import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.parameterprovider.track.CrossfadeParameterProvider;


/**
 * Mode for editing the cross-fade setting of all tracks.
 *
 * @author Jürgen Moßgraber
 */
public class CrossfadeMode extends AbstractTrackMode
{
    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public CrossfadeMode (final PushControlSurface surface, final IModel model)
    {
        super (Modes.NAME_CROSSFADE, surface, model);

        this.setParameterProvider (new CrossfadeParameterProvider (model));
    }


}
