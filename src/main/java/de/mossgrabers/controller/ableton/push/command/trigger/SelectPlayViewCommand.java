// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IDrumDevice;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.featuregroup.ViewManager;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.Views;


/**
 * Command to switch the pad grid to Note mode.
 *
 * @author Jürgen Moßgraber
 */
public class SelectPlayViewCommand extends AbstractTriggerCommand<PushControlSurface, PushConfiguration>
{
    private boolean switchedView;
    private boolean restoreOnRelease;


    /**
     * Constructor.
     *
     * @param model The model
     * @param surface The surface
     */
    public SelectPlayViewCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
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

        if (event != ButtonEvent.DOWN)
            return;

        this.switchedView = false;
        this.restoreOnRelease = false;
        if (this.surface.isShiftPressed ())
        {
            viewManager.setActive (Views.DRUM_PAD);
            return;
        }

        if (!Views.isSessionView (viewManager.getActiveID ()))
            return;

        final Views previousView = viewManager.getActiveID ();
        this.selectDefaultNoteView (viewManager);
        this.switchedView = viewManager.getActiveID () != previousView;
    }


    private void selectDefaultNoteView (final ViewManager viewManager)
    {
        final ITrack track = this.model.getCursorTrack ();
        if (!track.doesExist ())
            return;

        if (viewManager.getPreferredView (track.getPosition ()) == null)
        {
            final IDrumDevice drumDevice = this.model.getDrumDevice ();
            if (drumDevice.doesExist () && drumDevice.hasDrumPads ())
            {
                viewManager.setActive (Views.DRUM_PAD);
                return;
            }
        }

        this.surface.recallPreferredView (track);
    }
}
