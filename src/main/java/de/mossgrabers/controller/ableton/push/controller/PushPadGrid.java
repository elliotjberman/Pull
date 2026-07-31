// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import java.util.Arrays;

import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.grid.PadGridImpl;
import de.mossgrabers.framework.daw.midi.IMidiOutput;


/**
 * Push 2 pad grid with support for its firmware-rendered pad fade.
 */
final class PushPadGrid extends PadGridImpl
{
    private static final int NO_PENDING_FADE         = -1;
    /** Push's clocked 1/16 one-shot transition channel. */
    private static final int SIXTEENTH_FADE_CHANNEL  = 2;

    private final int [] pendingFadeTargets = new int [NUM_NOTES];


    /**
     * Constructor.
     *
     * @param colorManager The color manager
     * @param output The MIDI output
     */
    PushPadGrid (final ColorManager colorManager, final IMidiOutput output)
    {
        super (colorManager, output);

        Arrays.fill (this.pendingFadeTargets, NO_PENDING_FADE);
    }


    /**
     * Fade the pad to the expected target color the next time that color is sent.
     *
     * @param note The physical Push pad note
     * @param targetColor The expected target color
     */
    void requestFade (final int note, final int targetColor)
    {
        if (note < this.startNote || note > this.endNote)
            throw new IllegalArgumentException ("Pad note is outside the Push grid.");
        if (targetColor < 0 || targetColor > 127)
            throw new IllegalArgumentException ("Pad color must be in the range 0-127.");

        synchronized (this.pendingFadeTargets)
        {
            this.pendingFadeTargets[note] = targetColor;
        }
    }


    /**
     * Cancel a pending fade which has not yet reached the hardware.
     *
     * @param note The physical Push pad note
     */
    void cancelFade (final int note)
    {
        if (note < this.startNote || note > this.endNote)
            return;

        synchronized (this.pendingFadeTargets)
        {
            this.pendingFadeTargets[note] = NO_PENDING_FADE;
        }
    }


    /** {@inheritDoc} */
    @Override
    protected void sendNoteState (final int channel, final int note, final int color)
    {
        final int fadeTarget;
        synchronized (this.pendingFadeTargets)
        {
            fadeTarget = this.pendingFadeTargets[note];
            this.pendingFadeTargets[note] = NO_PENDING_FADE;
        }
        super.sendNoteState (fadeTarget == color ? SIXTEENTH_FADE_CHANNEL : channel, note, color);
    }
}
