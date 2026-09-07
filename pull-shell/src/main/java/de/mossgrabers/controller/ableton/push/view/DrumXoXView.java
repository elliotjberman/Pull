// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.view;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.controller.grid.PadColor;
import de.mossgrabers.framework.controller.grid.PadLight;
import de.mossgrabers.framework.daw.data.ISlot;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.clip.INoteClip;
import de.mossgrabers.framework.daw.clip.NotePosition;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.framework.view.sequencer.AbstractDrumXoXView;


/**
 * The Drum XoX view.
 *
 * @author Jürgen Moßgraber
 */
public class DrumXoXView extends AbstractDrumXoXView<PushControlSurface, PushConfiguration>
{
    /**
     * Constructor.
     *
     * @param surface The surface
     * @param model The model
     */
    public DrumXoXView (final PushControlSurface surface, final IModel model)
    {
        super (Views.NAME_DRUM_XOX, surface, model, 8);
    }


    /** Retained legacy sequencer clip feedback, independent of the migrated Session adapter. */
    @Override
    protected void drawPages (final INoteClip clip, final boolean isActive)
    {
        final IPadGrid padGrid = this.surface.getPadGrid ();
        final boolean isRecArmed = this.model.getCursorTrack ().isRecArm ();
        for (int x = 0; x < this.slotBank.getPageSize (); x++)
        {
            final PadLight color = this.clipLight (this.slotBank.getItem (x), isRecArmed);
            padGrid.lightEx (x % this.numColumns, x / this.numColumns, color.color (), color.blinkColor (), color.fast ());
        }
    }


    private PadLight clipLight (final ISlot slot, final boolean isArmed)
    {
        final PadColor clipColor = PadColor.rgb (slot.getColor ());
        if (slot.isRecordingQueued ())
            return new PadLight (PadColor.indexed (PushColorManager.PUSH2_COLOR2_ROSE), PadColor.indexed (0), true);
        if (slot.isRecording ())
            return new PadLight (clipColor, PadColor.indexed (PushColorManager.PUSH2_COLOR2_ROSE), false);
        if (slot.isPlayingQueued () || slot.isStopQueued ())
            return new PadLight (clipColor, PadColor.indexed (PushColorManager.PUSH2_COLOR2_GREEN), true);
        if (slot.isPlaying ())
            return new PadLight (clipColor, PadColor.indexed (PushColorManager.PUSH2_COLOR2_GREEN), false);
        if (slot.hasContent ())
        {
            if (slot.isMuted ())
                return new PadLight (PadColor.indexed (PushColorManager.PUSH2_COLOR2_GREY_LO));
            return new PadLight (clipColor, slot.isSelected () ? PadColor.indexed (PushColorManager.PUSH2_COLOR2_WHITE) : null, false);
        }
        return new PadLight (PadColor.indexed (slot.doesExist () && isArmed && this.surface.getConfiguration ().isDrawRecordStripe () ? PushColorManager.PUSH2_COLOR2_RECORD_ARMED_DIM : 0));
    }


    /** {@inheritDoc} */
    @Override
    public void onGridNoteLongPress (final int note)
    {
        if (!this.isActive ())
            return;

        final int index = note - this.surface.getPadGrid ().getStartNote ();
        final int x = index % this.numColumns;
        final int y = index / this.numColumns;

        // Sequencer steps
        if (y < this.numStepRows)
        {
            this.surface.getButton (ButtonID.get (ButtonID.PAD1, index)).setConsumed ();

            final int offsetY = this.scales.getDrumOffset ();
            final NotePosition notePosition = new NotePosition (this.configuration.getMidiEditChannel (), (this.numStepRows - 1 - y) * this.numColumns + x, offsetY + this.selectedPad);
            this.editNote (this.getClip (), notePosition, false);
        }
    }


    /** {@inheritDoc} */
    @Override
    protected boolean handleSequencerAreaButtonCombinations (final INoteClip clip, final NotePosition notePosition, final int velocity)
    {
        final boolean isSelectPressed = this.surface.isSelectPressed ();

        if (this.surface.isShiftPressed ())
        {
            if (velocity > 0)
                this.handleSequencerAreaRepeatOperator (clip, notePosition, velocity, !isSelectPressed);
            return true;
        }

        if (isSelectPressed)
        {
            this.surface.setTriggerConsumed (ButtonID.SELECT);
            if (velocity > 0)
                this.editNote (clip, notePosition, true);
            return true;
        }

        return super.handleSequencerAreaButtonCombinations (clip, notePosition, velocity);
    }
}