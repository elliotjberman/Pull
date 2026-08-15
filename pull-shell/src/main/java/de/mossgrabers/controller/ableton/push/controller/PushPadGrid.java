// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.grid.LightInfo;
import de.mossgrabers.framework.controller.grid.PadColor;
import de.mossgrabers.framework.controller.grid.PadGridImpl;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.api.output.PadGridPosition;
import de.mossgrabers.pull.core.api.output.RgbColor;


/**
 * Push 2 pad grid with support for its firmware-rendered pad fade.
 */
final class PushPadGrid extends PadGridImpl
{
    private static final int NO_PENDING_FADE         = -1;
    /** Push's clocked 1/16 one-shot transition channel. */
    private static final int SIXTEENTH_FADE_CHANNEL  = 2;

    private final int [] pendingFadeTargets = new int [NUM_NOTES];
    private final LightInfo [] frozenPadStates = new LightInfo [NUM_NOTES];
    private final LightInfo [] overlayPadStates = new LightInfo [NUM_NOTES];
    private final PadColor [] requestedOverlayColors = new PadColor [NUM_NOTES];
    private final int [] resolvedOverlayColors = new int [NUM_NOTES];

    private Supplier<ControllerPadGridOverlay> overlaySupplier = ControllerPadGridOverlay::inactive;
    private boolean overlayActive;


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
        for (int note = 0; note < NUM_NOTES; note++)
        {
            this.frozenPadStates[note] = new LightInfo ();
            this.overlayPadStates[note] = new LightInfo ();
        }
    }


    /** Install the permanent supplier for the reloadable sparse grid-overlay plane. */
    void setOverlaySupplier (final Supplier<ControllerPadGridOverlay> overlaySupplier)
    {
        this.overlaySupplier = Objects.requireNonNull (overlaySupplier, "overlaySupplier");
    }


    /** {@inheritDoc} */
    @Override
    public LightInfo getLightInfo (final int note)
    {
        return this.displayState (note);
    }


    /** {@inheritDoc} */
    @Override
    public void sendState (final int note)
    {
        final LightInfo state = this.displayState (note);
        final int [] translated = this.translateToController (note);
        final int channel = translated[0] < 0 ? 0 : translated[0];
        this.sendNoteState (channel, translated[1], state.getColor ());
        final int blinkColor = state.getBlinkColor ();
        if (blinkColor > 0 && blinkColor < 128)
            this.sendBlinkState (channel, translated[1], blinkColor, state.isFast ());
    }


    /**
     * Fade the pad to the expected target color the next time that color is sent.
     *
     * @param note The physical Push pad note
     * @param targetColor The expected target color
     */
    void requestFade (final int note, final PadColor targetColor)
    {
        if (note < this.startNote || note > this.endNote)
            throw new IllegalArgumentException ("Pad note is outside the Push grid.");

        synchronized (this.pendingFadeTargets)
        {
            this.pendingFadeTargets[note] = this.resolveColor (targetColor);
        }
    }


    private LightInfo displayState (final int note)
    {
        final ControllerPadGridOverlay overlay = Objects.requireNonNull (this.overlaySupplier.get (), "pad-grid overlay");
        if (!overlay.active ())
        {
            this.overlayActive = false;
            return super.getLightInfo (note);
        }

        if (!this.overlayActive)
        {
            this.captureFrozenFrame ();
            this.overlayActive = true;
        }

        final int index = note - this.startNote;
        final PadGridPosition position = new PadGridPosition (index % this.columns, index / this.columns);
        final Map<PadGridPosition, RgbColor> colors = overlay.colors ();
        final RgbColor color = colors.get (position);
        if (color == null)
            return this.frozenPadStates[note];

        final int colorIndex = this.resolveOverlayColor (note, PadColor.rgbOrOff (ColorEx.fromRGB (color.red (), color.green (), color.blue ())));
        final LightInfo overlayState = this.overlayPadStates[note];
        overlayState.setColors (colorIndex, 0, false);
        return overlayState;
    }


    private int resolveOverlayColor (final int note, final PadColor color)
    {
        if (!color.equals (this.requestedOverlayColors[note]))
        {
            final int resolvedColor = this.resolveColor (color);
            this.requestedOverlayColors[note] = color;
            this.resolvedOverlayColors[note] = resolvedColor;
        }
        return this.resolvedOverlayColors[note];
    }


    private void captureFrozenFrame ()
    {
        for (int note = this.startNote; note <= this.endNote; note++)
        {
            final LightInfo base = super.getLightInfo (note);
            this.frozenPadStates[note].setColors (base.getColor (), base.getBlinkColor (), base.isFast ());
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
