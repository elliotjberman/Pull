// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.command.trigger;

import java.util.Optional;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.command.core.AbstractTriggerCommand;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.featuregroup.ModeManager;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;


/**
 * Command to edit track parameters.
 *
 * @author Jürgen Moßgraber
 */
public class TrackCommand extends AbstractTriggerCommand<PushControlSurface, PushConfiguration>
{
    private Modes   previousMode;
    private boolean switchedMode;
    private boolean restoreOnRelease;


    /**
     * Constructor.
     *
     * @param model The model
     * @param surface The surface
     */
    public TrackCommand (final IModel model, final PushControlSurface surface)
    {
        super (model, surface);
    }


    /** {@inheritDoc} */
    @Override
    public void execute (final ButtonEvent event, final int velocity)
    {
        final ModeManager modeManager = this.surface.getModeManager ();
        if (event == ButtonEvent.LONG)
        {
            if (this.switchedMode)
                this.restoreOnRelease = true;
            return;
        }

        if (event == ButtonEvent.UP)
        {
            if (this.switchedMode && this.restoreOnRelease && this.previousMode != null)
                modeManager.setActive (this.previousMode);
            this.previousMode = null;
            this.switchedMode = false;
            this.restoreOnRelease = false;
            return;
        }

        if (event != ButtonEvent.DOWN)
            return;

        this.previousMode = null;
        this.switchedMode = false;
        this.restoreOnRelease = false;

        final PushConfiguration config = this.surface.getConfiguration ();

        if (this.surface.isShiftPressed ())
        {
            config.setVUMetersEnabled (!config.isEnableVUMeters ());
            return;
        }

        final Modes currentMode = modeManager.getActiveID ();

        if (Modes.TRACK.equals (currentMode))
            modeManager.setActive (config.getGlobalMixMode ());
        else if (isGlobalMixMode (currentMode))
            modeManager.setActive (Modes.TRACK);
        else
        {
            this.previousMode = modeManager.getActiveIDIgnoreTemporary ();
            modeManager.setActive (Modes.TRACK);
            this.switchedMode = true;
        }

        final ITrackBank tb = this.model.getCurrentTrackBank ();
        final Optional<ITrack> track = tb.getSelectedItem ();
        if (track.isEmpty ())
            tb.getItem (0).select ();
    }


    private static boolean isGlobalMixMode (final Modes mode)
    {
        return mode == Modes.VOLUME || mode == Modes.PAN || mode == Modes.CROSSFADER || Modes.isSendMode (mode);
    }
}
