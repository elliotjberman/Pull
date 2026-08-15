// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.grid.LightInfo;
import de.mossgrabers.framework.controller.grid.PadColor;
import de.mossgrabers.framework.controller.grid.PadGridImpl;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.api.output.ControllerLight;
import de.mossgrabers.pull.core.api.output.LightBlinkRate;
import de.mossgrabers.pull.core.api.output.PadGridPosition;
import de.mossgrabers.pull.core.api.output.RgbColor;


/**
 * Push 2 pad grid with explicit core-light and temporary-overlay planes.
 */
final class PushPadGrid extends PadGridImpl
{
    private final LightInfo [] frozenPadStates = new LightInfo [NUM_NOTES];
    private final LightInfo [] overlayPadStates = new LightInfo [NUM_NOTES];
    private final PadColor [] requestedOverlayColors = new PadColor [NUM_NOTES];
    private final int [] resolvedOverlayColors = new int [NUM_NOTES];
    private final LightInfo [] corePadStates = new LightInfo [NUM_NOTES];
    private final PadColor [] requestedCoreColors = new PadColor [NUM_NOTES];
    private final PadColor [] requestedCoreBlinkColors = new PadColor [NUM_NOTES];
    private final int [] resolvedCoreColors = new int [NUM_NOTES];
    private final int [] resolvedCoreBlinkColors = new int [NUM_NOTES];

    private Supplier<ControllerPadGridOverlay> overlaySupplier = ControllerPadGridOverlay::inactive;
    private Predicate<ControlId> coreLightOwner = ignored -> false;
    private Function<ControlId, ControllerLight> coreLight = ignored -> ControllerLight.steady (new RgbColor (0, 0, 0));
    private boolean overlayActive;
    private int debugObservedNote = -1;
    private boolean debugObservedSend;
    private long debugTransmissionRevision;
    private Transmission debugBaseTransmission = Transmission.NONE;
    private Transmission debugBlinkTransmission = Transmission.NONE;
    private DebugSurfaceObserver debugSurfaceObserver;


    /**
     * Constructor.
     *
     * @param colorManager The color manager
     * @param output The MIDI output
     */
    PushPadGrid (final ColorManager colorManager, final IMidiOutput output)
    {
        super (colorManager, output);

        for (int note = 0; note < NUM_NOTES; note++)
        {
            this.frozenPadStates[note] = new LightInfo ();
            this.overlayPadStates[note] = new LightInfo ();
            this.corePadStates[note] = new LightInfo ();
        }
    }


    /** Install the permanent supplier for the reloadable sparse grid-overlay plane. */
    void setOverlaySupplier (final Supplier<ControllerPadGridOverlay> overlaySupplier)
    {
        this.overlaySupplier = Objects.requireNonNull (overlaySupplier, "overlaySupplier");
    }


    /** Install the opt-in observer for complete successful pad transmissions. */
    void setDebugSurfaceObserver (final DebugSurfaceObserver debugSurfaceObserver)
    {
        this.debugSurfaceObserver = Objects.requireNonNull (debugSurfaceObserver, "debugSurfaceObserver");
    }


    /** Install the permanent explicit core-light ownership plane beneath temporary overlays. */
    void setCoreLightSupplier (final Predicate<ControlId> owner, final Function<ControlId, ControllerLight> light)
    {
        this.coreLightOwner = Objects.requireNonNull (owner, "owner");
        this.coreLight = Objects.requireNonNull (light, "light");
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
        this.debugObservedSend = note == this.debugObservedNote;
        try
        {
            this.sendNoteState (channel, translated[1], state.getColor ());
            final int blinkColor = state.getBlinkColor ();
            if (blinkColor > 0 && blinkColor < 128)
                this.sendBlinkState (channel, translated[1], blinkColor, state.isFast ());
        }
        finally
        {
            this.debugObservedSend = false;
        }
        if (this.debugSurfaceObserver != null)
            this.debugSurfaceObserver.observe (note - this.startNote + 1, state.getColor (), state.getBlinkColor (), state.isFast ());
    }


    /** Begin the single bounded debug transmission observation lane. */
    void beginDebugObservation (final int note)
    {
        if (note < this.startNote || note > this.endNote)
            throw new IllegalArgumentException ("Pad note is outside the Push grid.");
        if (this.debugObservedNote >= 0)
            throw new IllegalStateException ("A Push pad transmission observation is already active.");
        this.debugObservedNote = note;
        this.debugBaseTransmission = Transmission.NONE;
        this.debugBlinkTransmission = Transmission.NONE;
    }


    /** Snapshot resolved light state and matching successful MIDI transmissions. */
    DebugObservation debugObservation (final int note)
    {
        if (note != this.debugObservedNote)
            throw new IllegalStateException ("The requested Push pad is not being observed.");
        if (this.debugBaseTransmission.revision () == 0)
            this.sendState (note);
        final LightInfo light = this.displayState (note);
        return new DebugObservation (
            light.getColor (), light.getBlinkColor (), light.isFast (),
            this.debugBaseTransmission, this.debugBlinkTransmission);
    }


    /** End the bounded debug transmission observation lane. */
    void endDebugObservation (final int note)
    {
        if (note == this.debugObservedNote)
        {
            this.debugObservedNote = -1;
            this.debugObservedSend = false;
            this.debugBaseTransmission = Transmission.NONE;
            this.debugBlinkTransmission = Transmission.NONE;
        }
    }


    private LightInfo displayState (final int note)
    {
        final ControllerPadGridOverlay overlay = Objects.requireNonNull (this.overlaySupplier.get (), "pad-grid overlay");
        if (!overlay.active ())
        {
            this.overlayActive = false;
            return this.baseState (note);
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

        final int colorIndex = this.resolveCachedColor (note, toPadColor (color), this.requestedOverlayColors, this.resolvedOverlayColors);
        final LightInfo overlayState = this.overlayPadStates[note];
        overlayState.setColors (colorIndex, 0, false);
        return overlayState;
    }


    private LightInfo baseState (final int note)
    {
        final int index = note - this.startNote;
        final ControlId control = PushControlIds.pad (index + 1);
        if (!this.coreLightOwner.test (control))
            return super.getLightInfo (note);

        final ControllerLight light = Objects.requireNonNull (this.coreLight.apply (control), "core light");
        final int color = this.resolveCachedColor (note, toPadColor (light.color ()), this.requestedCoreColors, this.resolvedCoreColors);
        final int blinkColor = light.blinkRate () == LightBlinkRate.NONE ? 0 : this.resolveCachedColor (note, toPadColor (light.blinkColor ()), this.requestedCoreBlinkColors, this.resolvedCoreBlinkColors);
        final LightInfo coreState = this.corePadStates[note];
        coreState.setColors (color, blinkColor, light.blinkRate () == LightBlinkRate.FAST);
        return coreState;
    }


    private int resolveCachedColor (final int note, final PadColor color, final PadColor [] requestedColors, final int [] resolvedColors)
    {
        if (!color.equals (requestedColors[note]))
        {
            final int resolvedColor = this.resolveColor (color);
            requestedColors[note] = color;
            resolvedColors[note] = resolvedColor;
        }
        return resolvedColors[note];
    }


    private static PadColor toPadColor (final RgbColor color)
    {
        return PadColor.rgbOrOff (ColorEx.fromRGB (color.red (), color.green (), color.blue ()));
    }


    private void captureFrozenFrame ()
    {
        for (int note = this.startNote; note <= this.endNote; note++)
        {
            final LightInfo base = this.baseState (note);
            this.frozenPadStates[note].setColors (base.getColor (), base.getBlinkColor (), base.isFast ());
        }
    }


    /** {@inheritDoc} */
    @Override
    protected void sendNoteState (final int channel, final int note, final int color)
    {
        super.sendNoteState (channel, note, color);
        if (this.debugObservedSend)
            this.debugBaseTransmission = new Transmission (++this.debugTransmissionRevision, channel, note, color);
    }


    /** {@inheritDoc} */
    @Override
    protected void sendBlinkState (final int channel, final int note, final int blinkColor, final boolean fast)
    {
        super.sendBlinkState (channel, note, blinkColor, fast);
        if (this.debugObservedSend)
            this.debugBlinkTransmission = new Transmission (++this.debugTransmissionRevision, fast ? 14 : 10, note, blinkColor);
    }


    record DebugObservation (int color, int blinkColor, boolean fast, Transmission base, Transmission blink)
    {
    }


    record Transmission (long revision, int channel, int note, int color)
    {
        private static final Transmission NONE = new Transmission (0, -1, -1, -1);
    }


    @FunctionalInterface
    interface DebugSurfaceObserver
    {
        void observe (int oneBasedPad, int color, int blinkColor, boolean fast);
    }
}
