// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.view;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.featuregroup.AbstractView;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.TransposeView;
import de.mossgrabers.framework.view.Views;


/**
 * Full-grid host for the specialized drum performance, rate and fill controls.
 */
public final class DrumPadView extends AbstractView<PushControlSurface, PushConfiguration> implements TransposeView
{
    private final DrumPadControls controls;


    /**
     * Constructor.
     *
     * @param surface The surface
     * @param model The model
     * @param controls The drum performance controls
     */
    public DrumPadView (final PushControlSurface surface, final IModel model, final DrumPadControls controls)
    {
        super (Views.NAME_DRUM_PAD, surface, model);

        this.controls = controls;
        final ITrackBank trackBank = model.getTrackBank ();
        trackBank.addNoteObserver (this.keyManager);
        trackBank.addSelectionObserver ( (index, isSelected) -> this.keyManager.clearPressedKeys ());
    }


    /** {@inheritDoc} */
    @Override
    public void onActivate ()
    {
        this.controls.activate ();
        super.onActivate ();
    }


    /** {@inheritDoc} */
    @Override
    public void onDeactivate ()
    {
        this.controls.deactivate ();
        super.onDeactivate ();
    }


    /** {@inheritDoc} */
    @Override
    public void onGridNote (final int note, final int velocity)
    {
        // Inert permanent binding: reloadable views own controller semantics while the permanent
        // NoteInput independently carries translated musical notes to Bitwig.
    }


    /** {@inheritDoc} */
    @Override
    public void onGridPressure (final int note, final int value)
    {
        // Reloadable DrumPlayPadView owns all playable-pad and aggregate pressure policy.
    }


    /** {@inheritDoc} */
    @Override
    public void drawGrid ()
    {
        final IPadGrid padGrid = this.surface.getPadGrid ();
        for (int y = 0; y < padGrid.getRows (); y++)
        {
            for (int x = 0; x < padGrid.getCols (); x++)
                padGrid.lightEx (x, y, IPadGrid.GRID_OFF);
        }
    }


    /** {@inheritDoc} */
    @Override
    public void updateNoteMapping ()
    {
        // Neutral legacy baseline. The core's complete native table owns the playable mapping.
        super.updateNoteMapping ();
    }


    /** {@inheritDoc} */
    @Override
    public void onOctaveDown (final ButtonEvent event)
    {
        // Inert permanent command: DrumOctaveView owns every Drum octave gesture.
    }


    /** {@inheritDoc} */
    @Override
    public void onOctaveUp (final ButtonEvent event)
    {
        // Inert permanent command: DrumOctaveView owns every Drum octave gesture.
    }


    /** {@inheritDoc} */
    @Override
    public boolean isOctaveUpButtonOn ()
    {
        return false;
    }


    /** {@inheritDoc} */
    @Override
    public boolean isOctaveDownButtonOn ()
    {
        return false;
    }


    /** {@inheritDoc} */
    @Override
    public void resetOctave ()
    {
        // This framework callback has no caller for the specialized Drum Pad view.
    }
}
