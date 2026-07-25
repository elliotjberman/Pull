// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.view;

import java.util.Arrays;
import java.util.Optional;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.constants.Resolution;
import de.mossgrabers.framework.daw.data.IDrumDevice;
import de.mossgrabers.framework.daw.data.IDrumPad;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.IDrumPadBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.midi.ArpeggiatorMode;
import de.mossgrabers.framework.daw.midi.INoteInput;
import de.mossgrabers.framework.daw.midi.INoteRepeat;
import de.mossgrabers.framework.featuregroup.IExpressionView;
import de.mossgrabers.framework.scale.Scales;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.framework.view.Views;
import de.mossgrabers.framework.view.sequencer.AbstractDrumView;


/**
 * A focused 4x4 drum performance view.
 */
public class DrumPadView extends AbstractDrumView<PushControlSurface, PushConfiguration> implements IExpressionView
{
    private static final int      PLAY_COLUMNS        = 4;
    private static final int      PLAY_ROWS           = 4;
    private static final int      NUM_PLAY_PADS       = PLAY_COLUMNS * PLAY_ROWS;
    private static final int      RATE_PAD_START      = PLAY_COLUMNS;
    private static final int      NUM_RATE_PADS       = 4;
    private static final int      PALETTE_SIZE        = 128;
    private static final int      PLAY_COLOR_LOW      = PushColorManager.PUSH2_COLOR2_GREEN_LO;
    private static final int      PLAY_COLOR_HIGH     = PushColorManager.PUSH2_COLOR2_GREEN_HI;
    private static final long     MAX_PLAYBACK_HOLD_NANOS = 250_000_000L;
    private static final long     PLAYBACK_RELEASE_NANOS  = 80_000_000L;
    private static final long     ANIMATION_FRAME_MILLIS  = 20;
    private static final double   ROLL_GATE_RATIO     = 0.5;
    private static final int      RATE_COLOR          = PushColorManager.PUSH2_COLOR2_YELLOW_LO;
    private static final int      RATE_COLOR_HELD     = PushColorManager.PUSH2_COLOR2_YELLOW;
    private static final int      RATE_COLOR_ACTIVE   = PushColorManager.PUSH2_COLOR2_YELLOW_HI;
    private static final Resolution DEFAULT_RATE      = Resolution.RES_1_16;
    private static final Resolution [] RATE_STEPS      =
    {
        Resolution.RES_1_4T,
        Resolution.RES_1_8,
        Resolution.RES_1_8T,
        Resolution.RES_1_16,
        Resolution.RES_1_16T,
        Resolution.RES_1_32,
        Resolution.RES_1_32T
    };
    private static final int [] [] PALETTE_RGB         = createPaletteRGB ();
    private static final int []   VELOCITY_COLORS     = createVelocityColors ();

    private final Object     animationLock        = new Object ();
    private final long []    playbackStartedAt    = new long [NUM_PLAY_PADS];
    private final long []    playbackReleasedAt   = new long [NUM_PLAY_PADS];
    private final int []     playedVelocities     = new int [NUM_PLAY_PADS];
    private final int []     playbackActiveVoices = new int [NUM_PLAY_PADS];
    private final boolean [] ratePadsDown          = new boolean [NUM_RATE_PADS];
    private final long []    ratePadPressOrder     = new long [NUM_RATE_PADS];

    private boolean            velocityOverrideActive;
    private boolean            playbackViewActive;
    private boolean            animationFrameScheduled;
    private int                activeRatePad            = -1;
    private int                activeRatePartner        = -1;
    private int                selectedTrackIndex       = -1;
    private long               ratePadPressCounter;
    private long               animationGeneration;


    /**
     * Constructor.
     *
     * @param surface The surface
     * @param model The model
     */
    public DrumPadView (final PushControlSurface surface, final IModel model)
    {
        // Keep the normal Push drum-pad position in the lower-left quadrant. The four nominal
        // sequencer rows are deliberately disabled below.
        super (Views.NAME_DRUM_PAD, surface, model, 4, PLAY_ROWS, true);

        final ITrackBank trackBank = model.getTrackBank ();
        trackBank.addNotePlaybackObserver (this::onNativeTrackNote);
        trackBank.addSelectionObserver ( (index, isSelected) -> {
            if (isSelected)
                this.selectPlaybackTrack (index);
        });
    }


    /** {@inheritDoc} */
    @Override
    public void onActivate ()
    {
        super.onActivate ();
        this.resetTransientPadState ();
        this.refreshSelectedTrack ();
        this.resetMomentaryRatePads ();
        synchronized (this.animationLock)
        {
            this.playbackViewActive = true;
            this.animationFrameScheduled = false;
            this.animationGeneration++;
        }
        this.surface.setVelocityTranslationTable (Scales.getIdentityMatrix ());
        this.velocityOverrideActive = true;

        final INoteInput noteInput = this.surface.getMidiInput ().getDefaultNoteInput ();
        if (noteInput != null)
        {
            final INoteRepeat noteRepeat = noteInput.getNoteRepeat ();
            this.configuration.setNoteRepeatActive (true);
            this.configuration.setNoteRepeatMode (ArpeggiatorMode.UP);
            noteRepeat.setActive (true);
            noteRepeat.setMode (ArpeggiatorMode.UP);
            noteRepeat.setOctaves (0);
            noteRepeat.setPeriod (DEFAULT_RATE.getValue ());
            noteRepeat.setNoteLength (ROLL_GATE_RATIO);
            noteRepeat.setLatchActive (false);
            if (noteRepeat.isFreeRunning ())
                noteRepeat.toggleIsFreeRunning ();
            if (!noteRepeat.usePressure ())
                noteRepeat.toggleUsePressure ();
            if (!noteRepeat.isShuffle ())
                noteRepeat.toggleShuffle ();
        }
    }


    /** {@inheritDoc} */
    @Override
    public void onDeactivate ()
    {
        synchronized (this.animationLock)
        {
            this.playbackViewActive = false;
            this.animationFrameScheduled = false;
            this.animationGeneration++;
            this.clearPlaybackState ();
        }
        super.onDeactivate ();
        this.resetMomentaryRatePads ();

        final INoteInput noteInput = this.surface.getMidiInput ().getDefaultNoteInput ();
        if (noteInput != null)
        {
            final INoteRepeat noteRepeat = noteInput.getNoteRepeat ();
            noteRepeat.setOctaves (this.configuration.getNoteRepeatOctave ());
            noteRepeat.setPeriod (this.configuration.getNoteRepeatPeriod ().getValue ());
            noteRepeat.setNoteLength (this.configuration.getNoteRepeatLength ().getValue ());
        }

        if (!this.velocityOverrideActive)
            return;

        final int [] velocityTable = Scales.getIdentityMatrix ();
        if (this.configuration.isAccentActive ())
        {
            Arrays.fill (velocityTable, Math.min (127, Math.max (0, this.configuration.getFixedAccentValue ())));
            velocityTable[0] = 0;
        }
        this.surface.setVelocityTranslationTable (velocityTable);
        this.velocityOverrideActive = false;
    }


    /** {@inheritDoc} */
    @Override
    public void onGridNote (final int note, final int velocity)
    {
        final int ratePadIndex = this.getRatePadIndex (note);
        if (ratePadIndex >= 0)
            this.onRatePad (ratePadIndex, velocity > 0);
    }


    /** {@inheritDoc} */
    @Override
    public void executeAftertouchCommand (final int note, final int value)
    {
        if (note >= 0 && this.getRatePadIndex (note) >= 0)
            return;
        super.executeAftertouchCommand (note, value);
    }


    /** {@inheritDoc} */
    @Override
    public String getButtonColorID (final ButtonID buttonID)
    {
        if (ButtonID.isSceneButton (buttonID) && this.surface.isPressed (ButtonID.REPEAT) && this.surface.isShiftPressed ())
            return NoteRepeatSceneHelper.getButtonColorID (this.surface, buttonID);
        return super.getButtonColorID (buttonID);
    }


    /** {@inheritDoc} */
    @Override
    public void onButton (final ButtonID buttonID, final ButtonEvent event, final int velocity)
    {
        if (!ButtonID.isSceneButton (buttonID) || event != ButtonEvent.DOWN)
            return;

        final int index = buttonID.ordinal () - ButtonID.SCENE1.ordinal ();
        if (this.surface.isPressed (ButtonID.REPEAT) && this.surface.isShiftPressed ())
        {
            NoteRepeatSceneHelper.handleNoteRepeatSelection (this.surface, 7 - index);
            this.enforceRollOctaveRange ();
        }
    }


    /** {@inheritDoc} */
    @Override
    public void changeOctave (final ButtonEvent event, final boolean isUp, final int offset, final boolean adjustPage, final boolean notify)
    {
        final int previousOffset = this.scales.getDrumOffset ();
        super.changeOctave (event, isUp, offset, adjustPage, notify);
        if (previousOffset != this.scales.getDrumOffset ())
            this.resetTransientPadState ();
    }


    /** {@inheritDoc} */
    @Override
    public void resetOctave ()
    {
        final int previousOffset = this.scales.getDrumOffset ();
        super.resetOctave ();
        if (previousOffset != this.scales.getDrumOffset ())
            this.resetTransientPadState ();
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
        synchronized (this.animationLock)
        {
            this.drawRatePads (padGrid);
        }

        if (!this.model.canSelectedTrackHoldNotes ())
            return;

        final IDrumDevice drumDevice = this.getDrumDevice ();
        if (!drumDevice.hasDrumPads ())
            return;

        final IDrumPadBank drumPads = drumDevice.getDrumPadBank ();
        final int restingColor = this.getTrackColorIndex ();
        final long now = System.nanoTime ();
        synchronized (this.animationLock)
        {
            for (int y = 0; y < PLAY_ROWS; y++)
            {
                for (int x = 0; x < PLAY_COLUMNS; x++)
                {
                    final int padIndex = y * PLAY_COLUMNS + x;
                    final IDrumPad drumPad = drumPads.getItem (padIndex);
                    if (!drumPad.doesExist ())
                        continue;

                    final int color = this.getAnimatedColor (padIndex, restingColor, now);
                    padGrid.lightEx (x, this.allRows - 1 - y, color);
                }
            }
        }
    }


    private void onNativeTrackNote (final int trackIndex, final int note, final int velocity)
    {
        if (!this.isVisibleDrumNote (note))
            return;

        final Optional<ITrack> selectedTrack = this.model.getTrackBank ().getSelectedItem ();
        final int currentSelectedTrackIndex = selectedTrack.isPresent () ? selectedTrack.get ().getIndex () : -1;

        final int padIndex = note - this.scales.getDrumOffset ();
        final long now = System.nanoTime ();
        boolean startAnimation = false;
        synchronized (this.animationLock)
        {
            if (this.selectedTrackIndex != currentSelectedTrackIndex)
            {
                this.selectedTrackIndex = currentSelectedTrackIndex;
                this.clearPlaybackState ();
            }
            if (!this.playbackViewActive || this.selectedTrackIndex != trackIndex)
                return;

            if (velocity > 0)
            {
                if (this.playbackActiveVoices[padIndex] < Integer.MAX_VALUE)
                    this.playbackActiveVoices[padIndex]++;
                this.playbackStartedAt[padIndex] = now;
                this.playbackReleasedAt[padIndex] = 0;
                this.playedVelocities[padIndex] = velocity;
                startAnimation = true;
            }
            else if (this.playbackActiveVoices[padIndex] > 0)
            {
                this.playbackActiveVoices[padIndex]--;
                if (this.playbackActiveVoices[padIndex] == 0)
                {
                    this.playbackReleasedAt[padIndex] = now;
                    startAnimation = true;
                }
            }
        }
        if (startAnimation)
            this.requestPlaybackAnimation ();
    }


    private boolean isVisibleDrumNote (final int note)
    {
        final int drumOffset = this.scales.getDrumOffset ();
        return note >= drumOffset && note < drumOffset + NUM_PLAY_PADS;
    }


    private int getRatePadIndex (final int note)
    {
        final int index = note - this.surface.getPadGrid ().getStartNote ();
        if (index < 0)
            return -1;

        final int x = index % GRID_COLUMNS;
        final int y = index / GRID_COLUMNS;
        return y == 0 && x >= RATE_PAD_START && x < RATE_PAD_START + NUM_RATE_PADS ? x - RATE_PAD_START : -1;
    }


    private void onRatePad (final int ratePadIndex, final boolean isDown)
    {
        final Resolution rate;
        synchronized (this.animationLock)
        {
            if (this.ratePadsDown[ratePadIndex] == isDown)
                return;

            this.ratePadsDown[ratePadIndex] = isDown;
            this.ratePadPressOrder[ratePadIndex] = isDown ? ++this.ratePadPressCounter : 0;
            this.activeRatePad = this.findActiveRatePad ();
            this.activeRatePartner = this.findActiveRatePartner (this.activeRatePad);
            rate = this.getActiveRate ();
        }
        this.setNoteRepeatRate (rate);
    }


    private int findActiveRatePad ()
    {
        int activeIndex = -1;
        long newestPress = 0;
        for (int index = 0; index < this.ratePadsDown.length; index++)
        {
            if (this.ratePadsDown[index] && this.ratePadPressOrder[index] > newestPress)
            {
                activeIndex = index;
                newestPress = this.ratePadPressOrder[index];
            }
        }
        return activeIndex;
    }


    private int findActiveRatePartner (final int ratePadIndex)
    {
        if (ratePadIndex < 0)
            return -1;

        final int left = ratePadIndex - 1;
        final int right = ratePadIndex + 1;
        if (left < 0 || !this.ratePadsDown[left])
            return right < this.ratePadsDown.length && this.ratePadsDown[right] ? right : -1;
        if (right >= this.ratePadsDown.length || !this.ratePadsDown[right])
            return left;
        return this.ratePadPressOrder[left] > this.ratePadPressOrder[right] ? left : right;
    }


    private Resolution getActiveRate ()
    {
        if (this.activeRatePad < 0)
            return DEFAULT_RATE;
        if (this.activeRatePartner < 0)
            return RATE_STEPS[this.activeRatePad * 2];
        return RATE_STEPS[Math.min (this.activeRatePad, this.activeRatePartner) * 2 + 1];
    }


    private void setNoteRepeatRate (final Resolution resolution)
    {
        final INoteInput noteInput = this.surface.getMidiInput ().getDefaultNoteInput ();
        if (noteInput != null)
        {
            final INoteRepeat noteRepeat = noteInput.getNoteRepeat ();
            noteRepeat.setPeriod (resolution.getValue ());
            noteRepeat.setNoteLength (ROLL_GATE_RATIO);
        }
    }


    private void drawRatePads (final IPadGrid padGrid)
    {
        final int row = padGrid.getRows () - 1;
        for (int index = 0; index < NUM_RATE_PADS; index++)
        {
            final boolean isActive = index == this.activeRatePad || index == this.activeRatePartner;
            final int color = isActive ? RATE_COLOR_ACTIVE : this.ratePadsDown[index] ? RATE_COLOR_HELD : RATE_COLOR;
            padGrid.lightEx (RATE_PAD_START + index, row, color);
        }
    }


    private void resetMomentaryRatePads ()
    {
        synchronized (this.animationLock)
        {
            Arrays.fill (this.ratePadsDown, false);
            Arrays.fill (this.ratePadPressOrder, 0);
            this.activeRatePad = -1;
            this.activeRatePartner = -1;
            this.ratePadPressCounter = 0;
        }
    }


    private int getTrackColorIndex ()
    {
        final ColorEx trackColor = this.model.getCursorTrack ().getColor ();
        int closestIndex = PushColorManager.DAW_COLOR_FIRST;
        double closestDistance = Double.MAX_VALUE;
        for (int index = PushColorManager.DAW_COLOR_FIRST; index <= PushColorManager.DAW_COLOR_LAST; index++)
        {
            final double distance = ColorEx.calcDistance (trackColor, PushColorManager.getPaletteColor (index), true);
            if (distance < closestDistance)
            {
                closestIndex = index;
                closestDistance = distance;
            }
        }
        return closestIndex;
    }


    private int getAnimatedColor (final int padIndex, final int restingColor, final long now)
    {
        final int playingColor = getVelocityColor (this.playedVelocities[padIndex]);
        if (this.playbackActiveVoices[padIndex] > 0)
        {
            final long startedAt = this.playbackStartedAt[padIndex];
            if (now - startedAt < MAX_PLAYBACK_HOLD_NANOS)
                return playingColor;

            this.playbackActiveVoices[padIndex] = 0;
            this.playbackReleasedAt[padIndex] = startedAt + MAX_PLAYBACK_HOLD_NANOS;
        }

        final long releasedAt = this.playbackReleasedAt[padIndex];
        if (releasedAt == 0)
            return restingColor;

        final double elapsed = (double) (now - releasedAt) / PLAYBACK_RELEASE_NANOS;
        if (elapsed >= 1.0)
        {
            this.clearPlaybackPad (padIndex);
            return restingColor;
        }

        final double weight = cubicBezierFade (Math.max (0.0, elapsed));
        if (weight < 0.04)
        {
            this.clearPlaybackPad (padIndex);
            return restingColor;
        }

        final int [] restingRGB = PALETTE_RGB[restingColor];
        final int [] playingRGB = PALETTE_RGB[playingColor];
        final int red = interpolate (restingRGB[0], playingRGB[0], weight);
        final int green = interpolate (restingRGB[1], playingRGB[1], weight);
        final int blue = interpolate (restingRGB[2], playingRGB[2], weight);
        return findClosestPaletteColor (red, green, blue);
    }


    private void requestPlaybackAnimation ()
    {
        long generation = 0;
        boolean scheduleFrame = false;
        synchronized (this.animationLock)
        {
            if (!this.playbackViewActive)
                return;

            if (!this.animationFrameScheduled)
            {
                this.animationFrameScheduled = true;
                generation = this.animationGeneration;
                scheduleFrame = true;
            }
        }

        if (scheduleFrame)
        {
            this.surface.flush ();
            final long currentGeneration = generation;
            this.surface.scheduleTask ( () -> this.animatePlayback (currentGeneration), ANIMATION_FRAME_MILLIS);
        }
    }


    private void animatePlayback (final long generation)
    {
        final boolean continueAnimation;
        synchronized (this.animationLock)
        {
            if (!this.playbackViewActive || generation != this.animationGeneration)
                return;

            continueAnimation = this.hasActivePlaybackEnvelope (System.nanoTime ());
            if (!continueAnimation)
                this.animationFrameScheduled = false;
        }

        this.surface.flush ();
        if (continueAnimation)
            this.surface.scheduleTask ( () -> this.animatePlayback (generation), ANIMATION_FRAME_MILLIS);
    }


    private boolean hasActivePlaybackEnvelope (final long now)
    {
        for (int padIndex = 0; padIndex < NUM_PLAY_PADS; padIndex++)
        {
            final long startedAt = this.playbackStartedAt[padIndex];
            if (this.playbackActiveVoices[padIndex] > 0 && startedAt > 0 && now - startedAt < MAX_PLAYBACK_HOLD_NANOS + PLAYBACK_RELEASE_NANOS)
                return true;
            final long releasedAt = this.playbackReleasedAt[padIndex];
            if (releasedAt > 0 && now - releasedAt < PLAYBACK_RELEASE_NANOS)
                return true;
        }
        return false;
    }


    private static double cubicBezierFade (final double progress)
    {
        final double inverse = 1.0 - progress;
        return inverse * inverse * inverse + 3.0 * inverse * inverse * progress;
    }


    private static int interpolate (final int restingValue, final int playingValue, final double weight)
    {
        return (int) Math.round (restingValue + (playingValue - restingValue) * weight);
    }


    private static int [] [] createPaletteRGB ()
    {
        final int [] [] palette = new int [PALETTE_SIZE] [];
        for (int index = 0; index < palette.length; index++)
            palette[index] = PushColorManager.getPaletteColorRGB (index);
        return palette;
    }


    private static int [] createVelocityColors ()
    {
        final int [] colors = new int [128];
        final int [] low = PALETTE_RGB[PLAY_COLOR_LOW];
        final int [] high = PALETTE_RGB[PLAY_COLOR_HIGH];
        for (int velocity = 1; velocity < colors.length; velocity++)
        {
            final double weight = (double) (velocity - 1) / (colors.length - 2);
            final int red = interpolate (low[0], high[0], weight);
            final int green = interpolate (low[1], high[1], weight);
            final int blue = interpolate (low[2], high[2], weight);
            colors[velocity] = findClosestPaletteColor (red, green, blue);
        }
        colors[0] = colors[1];
        return colors;
    }


    private static int getVelocityColor (final int velocity)
    {
        return VELOCITY_COLORS[Math.max (0, Math.min (127, velocity))];
    }


    private static int findClosestPaletteColor (final int red, final int green, final int blue)
    {
        int closestIndex = 0;
        long closestDistance = Long.MAX_VALUE;
        for (int index = 0; index < PALETTE_SIZE; index++)
        {
            final int [] color = PALETTE_RGB[index];
            final long redDistance = red - color[0];
            final long greenDistance = green - color[1];
            final long blueDistance = blue - color[2];
            final long distance = redDistance * redDistance + greenDistance * greenDistance + blueDistance * blueDistance;
            if (distance < closestDistance)
            {
                closestIndex = index;
                closestDistance = distance;
            }
        }
        return closestIndex;
    }


    private void resetTransientPadState ()
    {
        synchronized (this.animationLock)
        {
            this.clearPlaybackState ();
        }
    }


    private void refreshSelectedTrack ()
    {
        final Optional<ITrack> selectedTrack = this.model.getTrackBank ().getSelectedItem ();
        this.selectPlaybackTrack (selectedTrack.isPresent () ? selectedTrack.get ().getIndex () : -1);
    }


    private void selectPlaybackTrack (final int trackIndex)
    {
        synchronized (this.animationLock)
        {
            if (this.selectedTrackIndex == trackIndex)
                return;
            this.selectedTrackIndex = trackIndex;
            this.clearPlaybackState ();
        }
    }


    private void clearPlaybackState ()
    {
        Arrays.fill (this.playbackStartedAt, 0);
        Arrays.fill (this.playbackReleasedAt, 0);
        Arrays.fill (this.playedVelocities, 0);
        Arrays.fill (this.playbackActiveVoices, 0);
    }


    private void clearPlaybackPad (final int padIndex)
    {
        this.playbackStartedAt[padIndex] = 0;
        this.playbackReleasedAt[padIndex] = 0;
        this.playedVelocities[padIndex] = 0;
        this.playbackActiveVoices[padIndex] = 0;
    }


    private void enforceRollOctaveRange ()
    {
        final INoteInput noteInput = this.surface.getMidiInput ().getDefaultNoteInput ();
        if (noteInput != null)
            noteInput.getNoteRepeat ().setOctaves (0);
    }
}
