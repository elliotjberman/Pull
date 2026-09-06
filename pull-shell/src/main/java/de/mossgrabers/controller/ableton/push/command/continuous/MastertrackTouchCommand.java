// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.command.continuous;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IMasterTrack;
import de.mossgrabers.controller.ableton.push.controller.PushControllerPageManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * Command for touching the master track.
 *
 * @author Jürgen Moßgraber
 */
public class MastertrackTouchCommand extends AbstractTriggerCommand<PushControlSurface, PushConfiguration>
{
    private PushControllerPageManager.TemporaryRequest masterPage;

    /**
     * Constructor.
     *
     * @param model The model
     * @param surface The surface
     */
    public MastertrackTouchCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
    }


    /** {@inheritDoc} */
    @Override
    public void execute (final ButtonEvent event, final int velocity)
    {
        final boolean isTouched = event != ButtonEvent.UP;
        final PushControllerPageManager.TemporaryRequest released = isTouched ? null : this.masterPage;
        if (!isTouched) this.masterPage = null;

        // Native Browser activity can precede the asynchronously projected controller page.
        // Preserve the existing Browser guard against its authoritative host state.
        final PushControllerPageManager modeManager = this.surface.getModeManager ();
        if (this.model.getBrowser ().isActive ())
        {
            if (released != null) released.cancel ();
            return;
        }

        final IMasterTrack masterTrack = this.model.getMasterTrack ();
        masterTrack.touchVolume (isTouched);

        if (this.surface.isDeletePressed ())
        {
            this.surface.setTriggerConsumed (ButtonID.DELETE);
            masterTrack.resetVolume ();
            if (released != null) released.cancel ();
            return;
        }

        final boolean isMasterMode = modeManager.isActive (Modes.MASTER);
        if (isTouched && isMasterMode)
            return;

        if (isTouched)
            this.masterPage = modeManager.beginTemporary (Modes.MASTER_TEMP);
        else if (released != null)
        {
            if (isMasterMode) released.cancel ();
            else released.close ();
        }
    }
}
