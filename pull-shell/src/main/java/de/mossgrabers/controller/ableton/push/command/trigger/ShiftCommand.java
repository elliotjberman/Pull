// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.controller.ableton.push.controller.PushControllerPageManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * Command to handle the shift button.
 *
 * @author Jürgen Moßgraber
 */
public class ShiftCommand extends AbstractTriggerCommand<PushControlSurface, PushConfiguration>
{
    private PushControllerPageManager.TemporaryRequest layoutPage;

    /**
     * Constructor.
     *
     * @param model The model
     * @param surface The surface
     */
    public ShiftCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
    }


    /** {@inheritDoc} */
    @Override
    public void execute (final ButtonEvent event, final int velocity)
    {
        if (event == ButtonEvent.DOWN)
            this.layoutPage = this.surface.getModeManager ().beginTemporary (Modes.SCALE_LAYOUT, Modes.SCALES);
        else if (event == ButtonEvent.UP && this.layoutPage != null)
        {
            this.layoutPage.close ();
            this.layoutPage = null;
        }

        this.surface.setKnobSensitivityIsSlow (this.surface.isShiftPressed ());
    }
}
