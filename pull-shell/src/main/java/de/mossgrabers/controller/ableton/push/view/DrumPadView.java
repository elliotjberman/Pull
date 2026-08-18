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
        this.delayedUpdateNoteMapping (this.surface.isDrumControllerActive () ? this.scales.getDrumMatrix () : EMPTY_TABLE);
    }


    /** {@inheritDoc} */
    @Override
    public void onOctaveDown (final ButtonEvent event)
    {
        this.changeOctave (event, false, this.surface.isShiftPressed () ? 4 : this.scales.getDrumDefaultOffset ());
    }


    /** {@inheritDoc} */
    @Override
    public void onOctaveUp (final ButtonEvent event)
    {
        this.changeOctave (event, true, this.surface.isShiftPressed () ? 4 : this.scales.getDrumDefaultOffset ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean isOctaveUpButtonOn ()
    {
        return this.surface.isDrumControllerActive () && this.scales.canScrollDrumOctaveUp ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean isOctaveDownButtonOn ()
    {
        return this.surface.isDrumControllerActive () && this.scales.canScrollDrumOctaveDown ();
    }


    /** {@inheritDoc} */
    @Override
    public void resetOctave ()
    {
        if (!this.surface.isDrumControllerActive ())
            return;

        this.keyManager.clearPressedKeys ();
        this.scales.resetDrumOctave ();
        this.updateNoteMapping ();
        this.model.getDrumDevice ().getDrumPadBank ().scrollTo (this.scales.getDrumOffset (), true);
    }


    private void changeOctave (final ButtonEvent event, final boolean isUp, final int offset)
    {
        if (event != ButtonEvent.DOWN || !this.surface.isDrumControllerActive ())
            return;

        this.keyManager.clearPressedKeys ();
        if (isUp)
            this.scales.incDrumOffset (offset);
        else
            this.scales.decDrumOffset (offset);
        this.updateNoteMapping ();
        this.surface.getDisplay ().notify (this.scales.getDrumRangeText ());
        this.model.getDrumDevice ().getDrumPadBank ().scrollTo (this.scales.getDrumOffset (), false);
    }
}
