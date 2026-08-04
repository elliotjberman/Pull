// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.featuregroup.ViewManager;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.Views;


/**
 * Command to select the session view.
 *
 * @author Jürgen Moßgraber
 */
public class SelectSessionViewCommand extends AbstractTriggerCommand<PushControlSurface, PushConfiguration>
{
    private boolean switchedView;
    private boolean restoreOnRelease;


    /**
     * Constructor.
     *
     * @param model The model
     * @param surface The surface
     */
    public SelectSessionViewCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
    }


    /**
     * Activate temporary display of session view.
     */
    public void setTemporary ()
    {
        if (this.switchedView)
            this.restoreOnRelease = true;
    }


    /** {@inheritDoc} */
    @Override
    public void execute (final ButtonEvent event, final int velocity)
    {
        final ViewManager viewManager = this.surface.getViewManager ();

        if (event == ButtonEvent.LONG)
        {
            if (this.switchedView)
                this.restoreOnRelease = true;
            return;
        }

        if (event == ButtonEvent.UP)
        {
            if (this.switchedView && this.restoreOnRelease)
                viewManager.restore ();
            this.switchedView = false;
            this.restoreOnRelease = false;
            return;
        }

        if (event == ButtonEvent.DOWN)
        {
            this.switchedView = false;
            this.restoreOnRelease = false;
            if (Views.isSessionView (viewManager.getActiveID ()))
                return;

            viewManager.setActive (Views.SESSION);
            this.switchedView = true;
        }
    }
}
