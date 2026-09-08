// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.mode.track;

import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.clip.IClip;
import de.mossgrabers.framework.daw.clip.INoteClip;
import de.mossgrabers.framework.featuregroup.IView;
import de.mossgrabers.framework.featuregroup.ViewManager;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.ColorSelectMode;
import de.mossgrabers.framework.view.ColorView;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.framework.view.sequencer.AbstractSequencerView;


/**
 * Mode for editing the parameters of a clip.
 *
 * @author Jürgen Moßgraber
 */
public class ClipMode extends AbstractTrackMode
{
    private static final String PLEASE_SELECT_A_CLIP_PUSH2 = "Please select a clip.";

    private boolean             displayMidiNotes           = false;
    private final ITransport    transport;


    /**
     * Constructor.
     *
     * @param surface The control surface
     * @param model The model
     */
    public ClipMode (final PushControlSurface surface, final IModel model)
    {
        super ("Clip", surface, model);

        this.transport = this.model.getTransport ();
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobTouch (final int index, final boolean isTouched)
    {
        this.setTouchedKnob (index, isTouched);

        if (index == 7 && isTouched && this.surface.isDeletePressed ())
        {
            this.surface.setTriggerConsumed (ButtonID.DELETE);
            final IClip clip = this.getMidiClip ();
            if (clip.doesExist ())
                clip.resetAccent ();
        }
    }


    /** {@inheritDoc} */
    @Override
    public void onKnobValue (final int index, final int value)
    {
        if (!this.increaseKnobMovement ())
            return;

        final IClip clip = this.getMidiClip ();
        if (!clip.doesExist ())
            return;

        final boolean shiftPressed = this.surface.isShiftPressed ();

        switch (index)
        {
            case 0:
                clip.changePlayStart (value, shiftPressed);
                break;
            case 1:
                clip.changePlayEnd (value, shiftPressed);
                break;
            case 2:
                clip.changeLoopStart (value, shiftPressed);
                break;
            case 3:
                clip.changeLoopLength (value, shiftPressed);
                break;
            case 4:
                clip.setLoopEnabled (value <= 61);
                break;
            case 6:
                clip.setShuffleEnabled (value <= 61);
                break;
            case 7:
                clip.changeAccent (value, shiftPressed);
                break;
            default:
                // Intentionally empty
                break;
        }
    }


    /** {@inheritDoc} */
    @Override
    public void updateDisplay ()
    {
        if (!this.displayMidiNotes)
        {
            super.updateDisplay ();
            return;
        }
        // TODO: Elliot never used this so deferring how to migrate it to the new framework
        final IGraphicDisplay display = this.surface.getGraphicsDisplay ();
        final IClip clip = this.getMidiClip ();
        if (!clip.doesExist ())
        {
            display.addEmptyElement ();
            display.notify (PLEASE_SELECT_A_CLIP_PUSH2);
        }
        else
            display.setMidiClipElement ((INoteClip) clip, this.transport.getQuartersPerMeasure (), null);
        display.send ();
    }


    /** Existing editor selection, exposed only for bounded observation. */
    public boolean isDisplayingMidiNotes () { return this.displayMidiNotes; }
    public IClip getObservedClip () { return this.getMidiClip (); }


    /** {@inheritDoc} */
    @Override
    public void onSecondRow (final int index, final ButtonEvent event)
    {
        if (event != ButtonEvent.DOWN)
            return;

        if (this.displayMidiNotes)
        {
            this.displayMidiNotes = false;
            return;
        }

        if (index == 0)
        {
            final IClip clip = this.getMidiClip ();
            if (clip instanceof final INoteClip noteClip)
                noteClip.togglePinned ();
            return;
        }

        if (index == 7)
        {
            final ViewManager viewManager = this.surface.getViewManager ();
            ((ColorView<?, ?>) viewManager.get (Views.COLOR)).setMode (ColorSelectMode.MODE_CLIP);
            viewManager.setActive (Views.COLOR);
        }
    }


    /** {@inheritDoc} */
    @Override
    public int getButtonColor (final ButtonID buttonID)
    {
        final int index = this.isButtonRow (1, buttonID);
        if (index >= 0)
        {
            if (index == 0)
            {
                final IClip clip = this.getMidiClip ();
                final boolean isPinned = clip instanceof final INoteClip noteClip && noteClip.isPinned ();
                return isPinned ? PushColorManager.PUSH2_COLOR2_GREEN : PushColorManager.PUSH2_COLOR2_WHITE;
            }
            if (index == 7)
                return this.displayMidiNotes ? PushColorManager.PUSH2_COLOR_BLACK : PushColorManager.PUSH2_COLOR2_WHITE;
            return PushColorManager.PUSH2_COLOR_BLACK;
        }

        return super.getButtonColor (buttonID);
    }


    /**
     * Toggles the clip parameter with the piano roll display.
     */
    public void togglePianoRoll ()
    {
        this.displayMidiNotes = !this.displayMidiNotes;
    }


    private IClip getMidiClip ()
    {
        if (this.displayMidiNotes)
        {
            final IView activeView = this.surface.getViewManager ().getActive ();
            if (activeView instanceof final AbstractSequencerView<?, ?> sequencerView)
                return sequencerView.getClip ();
            return this.model.getNoteClip (8, 128);
        }

        return this.model.getCursorClip ();
    }
}
