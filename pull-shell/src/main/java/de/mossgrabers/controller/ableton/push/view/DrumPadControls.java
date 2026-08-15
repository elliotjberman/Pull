// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.view;

import java.util.Arrays;
import java.util.Optional;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.controller.grid.PadColor;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.IDrumDevice;
import de.mossgrabers.framework.daw.data.IDrumPad;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.IDrumPadBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.featuregroup.IView;
import de.mossgrabers.framework.scale.Scales;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.shell.runtime.ReloadableControllerRuntime;


/**
 * The performance controls for the 4x4 drum-pad block, four momentary roll-rate pads and twelve
 * reloadable fill pads.
 * <p>
 * This class owns only its pad regions. A full-grid view or a composite view is responsible for
 * drawing everything outside those regions.
 */
public final class DrumPadControls
{
    private static final int          PLAY_COLUMNS      = 4;
    private static final int          PLAY_ROWS         = 4;
    private static final int          NUM_PLAY_PADS     = PLAY_COLUMNS * PLAY_ROWS;
    private static final int          RATE_PAD_START    = PLAY_COLUMNS;
    private static final int          NUM_RATE_PADS     = 4;
    private static final int          PLAY_COLOR_LOW    = PushColorManager.PUSH2_COLOR2_GREEN_LO;
    private static final int          PLAY_COLOR_MEDIUM = PushColorManager.PUSH2_COLOR2_GREEN;
    private static final int          PLAY_COLOR_HIGH   = PushColorManager.PUSH2_COLOR2_GREEN_HI;
    private static final int          PAD_OFF_COLOR     = PushColorManager.PUSH2_COLOR2_BLACK;
    private static final int []       PLAY_COLOR_STEPS  =
    {
        PLAY_COLOR_LOW,
        PLAY_COLOR_MEDIUM,
        PLAY_COLOR_HIGH
    };
    private static final int [] []    PLAY_COLOR_RGB    = createPlayColorRGB ();
    private static final int []       VELOCITY_COLORS   = createVelocityColors ();

    private final PushControlSurface          surface;
    private final PushConfiguration           configuration;
    private final IModel                      model;
    private final Scales                      scales;
    private final ReloadableControllerRuntime reloadableRuntime;
    private final int []                      fillPadNotes         = ReloadableControllerRuntime.fillPadNotes ();
    private final int []                      playingVelocities   = new int [NUM_PLAY_PADS];
    private boolean                           active;
    private boolean                           controllerEngaged;
    private IDrumDevice                       engagedDrumDevice;
    private int                               selectedTrackIndex   = -1;
    private long                              fillOutputGeneration = Long.MIN_VALUE;


    /**
     * Constructor.
     *
     * @param surface The Push surface
     * @param model The model
     * @param reloadableRuntime The stable reloadable-core runtime
     */
    public DrumPadControls (final PushControlSurface surface, final IModel model, final ReloadableControllerRuntime reloadableRuntime)
    {
        this.surface = surface;
        this.configuration = surface.getConfiguration ();
        this.model = model;
        this.scales = model.getScales ();
        this.reloadableRuntime = reloadableRuntime;

        final ITrackBank trackBank = model.getTrackBank ();
        trackBank.addNoteObserver (this::onTrackNote);
        trackBank.addSelectionObserver ( (index, isSelected) -> {
            if (isSelected)
                this.selectPlaybackTrack (index);
        });
    }


    /**
     * Activate the performance controls.
     */
    public void activate ()
    {
        if (this.active)
            return;

        this.active = true;
        this.reconcileControllerState ();
    }


    /**
     * Deactivate the performance controls and restore the repeat and velocity state they
     * temporarily override.
     */
    public void deactivate ()
    {
        if (!this.active)
            return;

        this.active = false;
        this.reconcileControllerState ();
    }


    /**
     * Test whether the controls are active in their current host view.
     *
     * @return True if active
     */
    public boolean isActive ()
    {
        return this.active;
    }


    /**
     * Test whether capability read-back has been reconciled and the controller is engaged.
     *
     * @return True after the engage transition and before the disengage transition
     */
    public boolean isControllerEngaged ()
    {
        return this.controllerEngaged;
    }


    /**
     * Reconcile the performance controls with authoritative target capability and model identity.
     * This is polled from the controller flush so asynchronous proxy changes and Track Pin changes
     * cannot leave the controls engaged against a stale framework cursor.
     */
    public void reconcileControllerState ()
    {
        final boolean shouldEngage = this.active && this.surface.isDrumControllerApplicable ();
        final IDrumDevice candidate = shouldEngage ? this.model.getDrumDevice () : null;
        final boolean candidateChanged = shouldEngage && candidate != this.engagedDrumDevice;
        if (shouldEngage == this.controllerEngaged && !candidateChanged)
            return;

        final boolean wasEngaged = this.controllerEngaged;
        this.clearPlaybackFeedback ();

        if (this.engagedDrumDevice != null && this.engagedDrumDevice != candidate)
            this.engagedDrumDevice.getDrumPadBank ().setIndication (false);
        if (candidate != null)
        {
            candidate.getDrumPadBank ().scrollTo (this.scales.getDrumOffset (), false);
            candidate.getDrumPadBank ().setIndication (true);
        }

        this.controllerEngaged = shouldEngage;
        this.engagedDrumDevice = candidate;

        if (shouldEngage)
        {
            this.refreshSelectedTrack ();
            if (!wasEngaged)
                this.surface.setVelocityTranslationTable (Scales.getIdentityMatrix ());
        }
        else
            this.restoreVelocityTranslation ();

        if (wasEngaged != shouldEngage)
        {
            final IView activeView = this.surface.getViewManager ().getActive ();
            if (activeView != null)
                activeView.updateNoteMapping ();
        }
    }


    /**
     * Test whether this component owns a physical grid note.
     *
     * @param note The physical grid note
     * @return True if the note belongs to the drum block, rate controls or fill controls
     */
    public boolean ownsGridNote (final int note)
    {
        final int index = note - this.surface.getPadGrid ().getStartNote ();
        if (index < 0)
            return false;

        final int x = index % this.surface.getPadGrid ().getCols ();
        final int y = index / this.surface.getPadGrid ().getCols ();
        return y < PLAY_ROWS && x < PLAY_COLUMNS || y == 0 && x >= RATE_PAD_START && x < RATE_PAD_START + NUM_RATE_PADS || this.isFillPad (note);
    }


    /**
     * Handle a physical pad event owned by this component.
     * <p>
     * Play-pad notes are delivered to Bitwig through the note-input translation table. Only the
     * four rate pads require a view-side action.
     *
     * @param note The physical grid note
     * @param velocity The pad velocity
     */
    public void onGridNote (final int note, final int velocity)
    {
        // Inert permanent binding: core input routes own all drum-controller pad behavior.
    }


    /**
     * Test whether a physical grid note belongs to the playable 4x4 drum block.
     *
     * @param note The physical grid note
     * @return True for a playable drum pad
     */
    public boolean isPlayPad (final int note)
    {
        final int index = note - this.surface.getPadGrid ().getStartNote ();
        if (index < 0)
            return false;
        final int x = index % this.surface.getPadGrid ().getCols ();
        final int y = index / this.surface.getPadGrid ().getCols ();
        return x < PLAY_COLUMNS && y < PLAY_ROWS;
    }


    /**
     * Test whether a physical grid note is one of the reloadable drum-fill controls.
     *
     * @param note The physical grid note
     * @return True if this is a fill pad
     */
    private boolean isFillPad (final int note)
    {
        return ReloadableControllerRuntime.isFillPad (note);
    }


    /**
     * Draw only the pads owned by this component.
     *
     * @param padGrid The pad grid
     */
    public void drawOwnedPads (final IPadGrid padGrid)
    {
        if (!this.controllerEngaged)
        {
            this.drawInactivePads (padGrid);
            return;
        }

        this.drawRatePads (padGrid);

        final boolean canPlayNotes = this.model.canSelectedTrackHoldNotes ();
        final IDrumDevice drumDevice = this.model.getDrumDevice ();
        final boolean hasDrumPads = canPlayNotes && drumDevice.hasDrumPads ();
        final IDrumPadBank drumPads = hasDrumPads ? drumDevice.getDrumPadBank () : null;
        final PadColor restingColor = this.getTrackColor ();

        for (int y = 0; y < PLAY_ROWS; y++)
        {
            for (int x = 0; x < PLAY_COLUMNS; x++)
            {
                final int padIndex = y * PLAY_COLUMNS + x;
                final boolean padExists = drumPads != null && drumPads.getItem (padIndex).doesExist ();
                final PadColor color = padExists ? this.getPlayPadColor (padIndex, restingColor) : PadColor.indexed (PAD_OFF_COLOR);
                padGrid.lightEx (x, padGrid.getRows () - 1 - y, color);
            }
        }
        this.drawFillPads (padGrid);
    }


    private void drawFillPads (final IPadGrid padGrid)
    {
        final long outputGeneration = this.reloadableRuntime.outputGeneration ();
        final boolean replayOutput = outputGeneration != this.fillOutputGeneration;
        if (replayOutput)
            this.fillOutputGeneration = outputGeneration;

        for (int index = 0; index < this.fillPadNotes.length; index++)
        {
            final int note = this.fillPadNotes[index];
            final RgbColor color = this.reloadableRuntime.fillLightColor (note);
            padGrid.light (note, PadColor.rgbOrOff (ColorEx.fromRGB (color.red (), color.green (), color.blue ())));
            if (replayOutput)
            {
                // Replay only the migrated outputs. Going through the ordinary whole-surface
                // forceFlush path blanks every Push light for 100 ms.
                padGrid.sendState (note);
            }
        }
    }


    /**
     * Clear playback feedback after the visible drum range changes.
     */
    public void onDrumOffsetChanged ()
    {
        this.clearPlaybackFeedback ();
    }


    private void restoreVelocityTranslation ()
    {
        final int [] velocityTable = Scales.getIdentityMatrix ();
        if (this.configuration.isAccentActive ())
        {
            Arrays.fill (velocityTable, Math.min (127, Math.max (0, this.configuration.getFixedAccentValue ())));
            velocityTable[0] = 0;
        }
        this.surface.setVelocityTranslationTable (velocityTable);
    }


    private void onTrackNote (final int trackIndex, final int note, final int velocity)
    {
        if (!this.controllerEngaged || this.selectedTrackIndex != trackIndex)
            return;

        final int padIndex = note - this.scales.getDrumOffset ();
        if (padIndex < 0 || padIndex >= NUM_PLAY_PADS)
            return;

        final int previousVelocity = this.playingVelocities[padIndex];
        if (velocity > 0)
        {
            if (previousVelocity == velocity)
                return;
            this.playingVelocities[padIndex] = velocity;
            this.surface.cancelPadFade (this.toPhysicalPadNote (padIndex));
        }
        else
        {
            if (previousVelocity == 0)
                return;
            this.playingVelocities[padIndex] = 0;
            this.surface.requestPadFade (this.toPhysicalPadNote (padIndex), this.getRestingPadColor (padIndex));
        }
        this.surface.flush ();
    }


    private void drawRatePads (final IPadGrid padGrid)
    {
        for (int index = 0; index < NUM_RATE_PADS; index++)
        {
            final int note = padGrid.getStartNote () + RATE_PAD_START + index;
            final RgbColor color = this.reloadableRuntime.lightColor (de.mossgrabers.pull.core.api.PushControlIds.pad (RATE_PAD_START + index + 1));
            padGrid.light (note, PadColor.rgbOrOff (ColorEx.fromRGB (color.red (), color.green (), color.blue ())));
        }
    }


    private void drawInactivePads (final IPadGrid padGrid)
    {
        final int bottomRow = padGrid.getRows () - 1;
        for (int x = 0; x < PLAY_COLUMNS + NUM_RATE_PADS; x++)
            padGrid.lightEx (x, bottomRow, PAD_OFF_COLOR);

        for (int y = 1; y < PLAY_ROWS; y++)
        {
            for (int x = 0; x < PLAY_COLUMNS; x++)
                padGrid.lightEx (x, bottomRow - y, PAD_OFF_COLOR);
        }

        for (final int fillPadNote: this.fillPadNotes)
            padGrid.light (fillPadNote, PAD_OFF_COLOR);
    }


    private PadColor getRestingPadColor (final int padIndex)
    {
        if (!this.controllerEngaged || !this.model.canSelectedTrackHoldNotes ())
            return PadColor.indexed (PAD_OFF_COLOR);

        final IDrumDevice drumDevice = this.model.getDrumDevice ();
        if (!drumDevice.hasDrumPads ())
            return PadColor.indexed (PAD_OFF_COLOR);

        final IDrumPad drumPad = drumDevice.getDrumPadBank ().getItem (padIndex);
        return drumPad.doesExist () ? this.getTrackColor () : PadColor.indexed (PAD_OFF_COLOR);
    }


    private PadColor getTrackColor ()
    {
        return PadColor.rgb (this.model.getCursorTrack ().getColor ());
    }


    private PadColor getPlayPadColor (final int padIndex, final PadColor restingColor)
    {
        final int velocity = this.playingVelocities[padIndex];
        return velocity > 0 ? PadColor.indexed (getVelocityColor (velocity)) : restingColor;
    }


    private int toPhysicalPadNote (final int padIndex)
    {
        final IPadGrid padGrid = this.surface.getPadGrid ();
        return padGrid.getStartNote () + padIndex / PLAY_COLUMNS * padGrid.getCols () + padIndex % PLAY_COLUMNS;
    }


    private void clearPlaybackFeedback ()
    {
        Arrays.fill (this.playingVelocities, 0);
        for (int padIndex = 0; padIndex < NUM_PLAY_PADS; padIndex++)
            this.surface.cancelPadFade (this.toPhysicalPadNote (padIndex));
    }


    private void refreshSelectedTrack ()
    {
        final Optional<ITrack> selectedTrack = this.model.getTrackBank ().getSelectedItem ();
        this.selectPlaybackTrack (selectedTrack.isPresent () ? selectedTrack.get ().getIndex () : -1);
    }


    private void selectPlaybackTrack (final int trackIndex)
    {
        if (this.selectedTrackIndex == trackIndex)
            return;

        this.selectedTrackIndex = trackIndex;
        this.clearPlaybackFeedback ();
        if (this.active)
            this.surface.flush ();
    }


    private static int interpolate (final int restingValue, final int playingValue, final double weight)
    {
        return (int) Math.round (restingValue + (playingValue - restingValue) * weight);
    }


    private static int [] [] createPlayColorRGB ()
    {
        final int [] [] colors = new int [PLAY_COLOR_STEPS.length] [];
        for (int index = 0; index < colors.length; index++)
            colors[index] = PushColorManager.getPaletteColorRGB (PLAY_COLOR_STEPS[index]);
        return colors;
    }


    private static int [] createVelocityColors ()
    {
        final int [] colors = new int [128];
        final int [] low = PLAY_COLOR_RGB[0];
        final int [] high = PLAY_COLOR_RGB[PLAY_COLOR_RGB.length - 1];
        for (int velocity = 1; velocity < colors.length; velocity++)
        {
            final double weight = (double) (velocity - 1) / (colors.length - 2);
            final int red = interpolate (low[0], high[0], weight);
            final int green = interpolate (low[1], high[1], weight);
            final int blue = interpolate (low[2], high[2], weight);
            colors[velocity] = findClosestPlayColor (red, green, blue);
        }
        colors[0] = colors[1];
        return colors;
    }


    private static int getVelocityColor (final int velocity)
    {
        return VELOCITY_COLORS[Math.max (0, Math.min (127, velocity))];
    }


    private static int findClosestPlayColor (final int red, final int green, final int blue)
    {
        int closestStep = 0;
        long closestDistance = Long.MAX_VALUE;
        for (int index = 0; index < PLAY_COLOR_RGB.length; index++)
        {
            final int [] color = PLAY_COLOR_RGB[index];
            final long redDistance = red - color[0];
            final long greenDistance = green - color[1];
            final long blueDistance = blue - color[2];
            final long distance = redDistance * redDistance + greenDistance * greenDistance + blueDistance * blueDistance;
            if (distance < closestDistance)
            {
                closestStep = index;
                closestDistance = distance;
            }
        }
        return PLAY_COLOR_STEPS[closestStep];
    }


}
