// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.framework.command.trigger.transport.RecordCommand;
import de.mossgrabers.framework.configuration.Configuration;
import de.mossgrabers.framework.controller.IControlSurface;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ISlot;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.utils.ButtonEvent;

import java.util.Optional;


/**
 * Push record-button behavior. A plain press arms the selected track, Shift+Record toggles launcher
 * overdub, and Select+Record creates a new clip.
 *
 * @param <S> The type of the control surface
 * @param <C> The type of the configuration
 */
public class PushRecordArmCommand<S extends IControlSurface<C>, C extends Configuration> extends RecordCommand<S, C>
{
    /**
     * Constructor.
     *
     * @param model The model
     * @param surface The surface
     */
    public PushRecordArmCommand (final IModel model, final S surface)
    {
        super (model, surface);
    }


    /** {@inheritDoc} */
    @Override
    protected void executeRecord (final ButtonEvent event)
    {
        if (event == ButtonEvent.UP)
            this.toggleSelectedTrackArm ();
    }


    /** {@inheritDoc} */
    @Override
    protected void executeLauncherOverdub (final ButtonEvent event)
    {
        if (event == ButtonEvent.UP)
            this.toggleLauncherOverdub ();
    }


    /** {@inheritDoc} */
    @Override
    public void executeNormal (final ButtonEvent event)
    {
        if (event == ButtonEvent.DOWN)
            this.toggleSelectedTrackArm ();
    }


    /** {@inheritDoc} */
    @Override
    public void executeShifted (final ButtonEvent event)
    {
        if (event == ButtonEvent.DOWN)
            this.toggleLauncherOverdub ();
    }


    private void toggleSelectedTrackArm ()
    {
        this.model.getCurrentTrackBank ().getSelectedItem ().ifPresent (ITrack::toggleRecArm);
    }


    private void toggleLauncherOverdub ()
    {
        final Optional<ITrack> selectedTrack = this.model.getCurrentTrackBank ().getSelectedItem ();
        if (selectedTrack.isPresent ())
        {
            final Optional<ISlot> selectedSlot = selectedTrack.get ().getSlotBank ().getSelectedItem ();
            if (selectedSlot.isPresent () && selectedSlot.get ().isRecording ())
            {
                selectedSlot.get ().launch (true, false);
                return;
            }
        }

        this.transport.toggleLauncherOverdub ();
    }
}
