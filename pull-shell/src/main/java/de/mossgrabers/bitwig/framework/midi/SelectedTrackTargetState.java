// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.midi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.IntegerValue;
import com.bitwig.extension.controller.api.PlayingNote;
import com.bitwig.extension.controller.api.PlayingNoteArrayValue;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.SettableColorValue;
import com.bitwig.extension.controller.api.SettableEnumValue;
import com.bitwig.extension.controller.api.SettableRangedValue;
import com.bitwig.extension.controller.api.SoloValue;
import com.bitwig.extension.controller.api.StringValue;

import de.mossgrabers.bitwig.framework.daw.PrimaryDrumDeviceCanopy;
import de.mossgrabers.bitwig.framework.daw.PrimaryDrumDeviceCanopy.Candidate;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.daw.midi.SelectedTrackMonitorMode;
import de.mossgrabers.framework.daw.midi.SelectedTrackNoteTargetSnapshot;


/**
 * Authoritative capability state for the privately owned selected-track observer.
 *
 * <p>The private cursor owns a fixed primary-drum proxy canopy and the stable actuator for a direct
 * Push Pads note route. Pull never exposes or pins its track or device cursor.</p>
 */
final class SelectedTrackTargetState implements ISelectedTrackNoteTarget
{
    static final String DRUM_DEVICE_CURSOR_ID   = "PULL_PADS_DRUM_DEVICE";
    static final String DRUM_DEVICE_CURSOR_NAME = "Pull Pads Drum Device";

    private final CursorTrack target;
    private final StringValue targetID;
    private final BooleanValue targetExists;
    private final StringValue targetName;
    private final SettableColorValue targetColor;
    private final StringValue targetType;
    private final IntegerValue targetPosition;
    private final SettableBooleanValue targetCanHoldNotes;
    private final SettableBooleanValue targetCanHoldAudio;
    private final BooleanValue targetGroup;
    private final SettableBooleanValue targetGroupExpanded;
    private final SettableBooleanValue targetActivated;
    private final SettableBooleanValue targetArmed;
    private final SettableEnumValue targetMonitorMode;
    private final SettableBooleanValue targetMuted;
    private final SoloValue targetSoloed;
    private final BooleanValue targetMutedBySolo;
    private final BooleanValue targetStopped;
    private final SettableRangedValue targetVolume;
    private final SettableRangedValue targetPan;
    private final PlayingNoteArrayValue targetPlayingNotes;
    private final NoteInputImpl noteInput;
    private final List<DrumCandidateState> drumMachines = new ArrayList<> (PrimaryDrumDeviceCanopy.NUM_CANDIDATES);
    private final int [] playingVelocities = new int [128];

    private boolean identityInitialized;
    private String identityTrackID = "";
    private boolean identityExists;
    private long generation;
    private boolean noteInputRouteActive;


    /**
     * Constructor.
     *
     * @param host The Bitwig controller host
     * @param target The private selection-following target
     * @param noteInput The permanent controller note input
     */
    SelectedTrackTargetState (final ControllerHost host, final CursorTrack target, final NoteInputImpl noteInput)
    {
        final CursorTrack checkedTarget = Objects.requireNonNull (target, "target");

        this.target = checkedTarget;
        this.noteInput = Objects.requireNonNull (noteInput, "noteInput");
        this.targetID = checkedTarget.channelId ();
        this.targetExists = checkedTarget.exists ();
        this.targetName = checkedTarget.name ();
        this.targetColor = checkedTarget.color ();
        this.targetType = checkedTarget.trackType ();
        this.targetPosition = checkedTarget.position ();
        this.targetCanHoldNotes = checkedTarget.canHoldNoteData ();
        this.targetCanHoldAudio = checkedTarget.canHoldAudioData ();
        this.targetGroup = checkedTarget.isGroup ();
        this.targetGroupExpanded = checkedTarget.isGroupExpanded ();
        this.targetActivated = checkedTarget.isActivated ();
        this.targetArmed = checkedTarget.arm ();
        this.targetMonitorMode = checkedTarget.monitorMode ();
        this.targetMuted = checkedTarget.mute ();
        this.targetSoloed = checkedTarget.solo ();
        this.targetMutedBySolo = checkedTarget.isMutedBySolo ();
        this.targetStopped = checkedTarget.isStopped ();
        this.targetVolume = checkedTarget.volume ().value ();
        this.targetPan = checkedTarget.pan ().value ();
        this.targetPlayingNotes = checkedTarget.playingNotes ();

        final List<Candidate> drumMachines = PrimaryDrumDeviceCanopy.create (host, checkedTarget, DRUM_DEVICE_CURSOR_ID, DRUM_DEVICE_CURSOR_NAME, 0);
        for (final Candidate candidate: drumMachines)
        {
            final Device drumMachine = candidate.device ();
            final BooleanValue exists = drumMachine.exists ();
            final BooleanValue hasPads = drumMachine.hasDrumPads ();
            exists.markInterested ();
            hasPads.markInterested ();
            this.drumMachines.add (new DrumCandidateState (exists, hasPads));
        }

        this.targetID.markInterested ();
        this.targetExists.markInterested ();
        this.targetName.markInterested ();
        this.targetColor.markInterested ();
        this.targetType.markInterested ();
        this.targetPosition.markInterested ();
        this.targetCanHoldNotes.markInterested ();
        this.targetCanHoldAudio.markInterested ();
        this.targetGroup.markInterested ();
        this.targetGroupExpanded.markInterested ();
        this.targetActivated.markInterested ();
        this.targetArmed.markInterested ();
        this.targetMonitorMode.markInterested ();
        this.targetMuted.markInterested ();
        this.targetSoloed.markInterested ();
        this.targetMutedBySolo.markInterested ();
        this.targetStopped.markInterested ();
        this.targetVolume.markInterested ();
        this.targetPan.markInterested ();
        this.targetPlayingNotes.markInterested ();
        this.targetID.addValueObserver (trackID -> this.refreshIdentity (valueOrEmpty (trackID), isResolved (valueOrEmpty (trackID), this.targetExists.get ())));
        this.targetExists.addValueObserver (exists -> {
            final String trackID = this.readTrackID ();
            this.refreshIdentity (trackID, isResolved (trackID, exists));
        });
        this.targetPlayingNotes.addValueObserver (this::handlePlayingNotes);
    }


    /** {@inheritDoc} */
    @Override
    public void setNoteInputRouteActive (final boolean active)
    {
        if (active == this.noteInputRouteActive)
            return;
        if (active)
            this.noteInput.routeDirectlyTo (this.target);
        else
            this.noteInput.removeDirectRouteFrom (this.target);
        this.noteInputRouteActive = active;
    }


    /** {@inheritDoc} */
    @Override
    public SelectedTrackNoteTargetSnapshot snapshot ()
    {
        final String trackID = this.readTrackID ();
        final boolean exists = isResolved (trackID, this.targetExists.get ());
        final long currentGeneration = this.refreshIdentity (trackID, exists);
        final boolean stopped = !exists || this.targetStopped.get ();

        return new SelectedTrackNoteTargetSnapshot (currentGeneration, trackID, exists, valueOrEmpty (this.targetName.get ()), this.targetColor.red (), this.targetColor.green (), this.targetColor.blue (), valueOrEmpty (this.targetType.get ()), this.targetPosition.get (), this.targetCanHoldNotes.get (), this.targetCanHoldAudio.get (), this.targetGroup.get (), this.targetGroupExpanded.get (), this.targetActivated.get (), this.targetArmed.get (), this.readMonitorMode (), this.targetMuted.get (), this.targetSoloed.get (), this.targetMutedBySolo.get (), !stopped, stopped, this.targetVolume.get (), this.targetPan.get ());
    }


    /** {@inheritDoc} */
    @Override
    public long getGeneration ()
    {
        final String trackID = this.readTrackID ();
        return this.refreshIdentity (trackID, isResolved (trackID, this.targetExists.get ()));
    }


    /** {@inheritDoc} */
    @Override
    public String getChannelID ()
    {
        return this.readTrackID ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean doesExist ()
    {
        final String trackID = this.readTrackID ();
        return isResolved (trackID, this.targetExists.get ());
    }


    /** {@inheritDoc} */
    @Override
    public boolean canHoldNotes ()
    {
        return this.targetCanHoldNotes.get ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean hasDrumDevice ()
    {
        for (final DrumCandidateState drumMachine: this.drumMachines)
        {
            if (drumMachine.isReady ())
                return true;
        }
        return false;
    }


    /** {@inheritDoc} */
    @Override
    public int getPlayingVelocity (final int note)
    {
        if (note < 0 || note >= this.playingVelocities.length)
            throw new IllegalArgumentException ("note must be in the range 0..127");
        return this.playingVelocities[note];
    }


    /** {@inheritDoc} */
    @Override
    public void setActivated (final boolean activated)
    {
        if (this.doesExist ())
            this.targetActivated.set (activated);
    }


    /** {@inheritDoc} */
    @Override
    public void setGroupExpanded (final boolean expanded)
    {
        if (this.doesExist () && this.targetGroup.get ())
            this.targetGroupExpanded.set (expanded);
    }


    /** {@inheritDoc} */
    @Override
    public void setArmed (final boolean armed)
    {
        if (this.doesExist ())
            this.targetArmed.set (armed);
    }


    /** {@inheritDoc} */
    @Override
    public void setMonitorMode (final SelectedTrackMonitorMode mode)
    {
        if (this.doesExist ())
            this.targetMonitorMode.set (Objects.requireNonNull (mode, "mode").name ());
    }


    /** {@inheritDoc} */
    @Override
    public void setMuted (final boolean muted)
    {
        if (this.doesExist ())
            this.targetMuted.set (muted);
    }


    /** {@inheritDoc} */
    @Override
    public void setSoloed (final boolean soloed)
    {
        if (this.doesExist ())
            this.targetSoloed.set (soloed);
    }


    /** {@inheritDoc} */
    @Override
    public void setVolume (final double normalizedVolume)
    {
        requireNormalized (normalizedVolume, "normalizedVolume");
        if (this.doesExist ())
            this.targetVolume.set (normalizedVolume);
    }


    /** {@inheritDoc} */
    @Override
    public void setPan (final double normalizedPan)
    {
        requireNormalized (normalizedPan, "normalizedPan");
        if (this.doesExist ())
            this.targetPan.set (normalizedPan);
    }


    /** {@inheritDoc} */
    @Override
    public void stop ()
    {
        if (this.doesExist ())
            this.target.stop ();
    }


    /** {@inheritDoc} */
    @Override
    public void returnToArrangement ()
    {
        if (this.doesExist ())
            this.target.returnToArrangement ();
    }


    private String readTrackID ()
    {
        return valueOrEmpty (this.targetID.get ());
    }


    private long refreshIdentity (final String trackID, final boolean exists)
    {
        if (!this.identityInitialized || exists != this.identityExists || !trackID.equals (this.identityTrackID))
        {
            this.generation = Math.incrementExact (this.generation);
            this.identityTrackID = trackID;
            this.identityExists = exists;
            this.identityInitialized = true;
        }
        return this.generation;
    }


    private static boolean isResolved (final String trackID, final boolean exists)
    {
        return exists && !trackID.isBlank ();
    }


    private SelectedTrackMonitorMode readMonitorMode ()
    {
        final String value = valueOrEmpty (this.targetMonitorMode.get ());
        try
        {
            return SelectedTrackMonitorMode.valueOf (value.toUpperCase (Locale.US));
        }
        catch (final IllegalArgumentException ex)
        {
            return SelectedTrackMonitorMode.OFF;
        }
    }


    private void handlePlayingNotes (final PlayingNote [] notes)
    {
        Arrays.fill (this.playingVelocities, 0);
        if (notes == null)
            return;

        for (final PlayingNote note: notes)
        {
            final int pitch = note.pitch ();
            if (pitch >= 0 && pitch < this.playingVelocities.length)
                this.playingVelocities[pitch] = Math.max (this.playingVelocities[pitch], note.velocity ());
        }
    }


    private static String valueOrEmpty (final String value)
    {
        return value == null ? "" : value;
    }


    private static void requireNormalized (final double value, final String name)
    {
        if (!Double.isFinite (value) || value < 0.0 || value > 1.0)
            throw new IllegalArgumentException (name + " must be finite and normalized");
    }


    private record DrumCandidateState (BooleanValue exists, BooleanValue hasPads)
    {
        private boolean isReady ()
        {
            return this.exists.get () && this.hasPads.get ();
        }
    }
}
