// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.midi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.StringValue;

import de.mossgrabers.bitwig.framework.daw.PrimaryDrumDeviceCanopy;
import de.mossgrabers.bitwig.framework.daw.PrimaryDrumDeviceCanopy.Candidate;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;


/**
 * Authoritative capability state for the privately owned selected-track note target.
 *
 * <p>The same private cursor receives the Push Pads note input and owns a fixed primary-drum proxy
 * canopy. Pull never exposes or pins its track or device cursor.</p>
 */
final class SelectedTrackTargetState implements ISelectedTrackNoteTarget
{
    static final String DRUM_DEVICE_CURSOR_ID   = "PULL_PADS_DRUM_DEVICE";
    static final String DRUM_DEVICE_CURSOR_NAME = "Pull Pads Drum Device";

    private final StringValue targetID;
    private final BooleanValue targetExists;
    private final BooleanValue targetCanHoldNotes;
    private final List<DrumCandidateState> drumMachines = new ArrayList<> (PrimaryDrumDeviceCanopy.NUM_CANDIDATES);


    /**
     * Constructor.
     *
     * @param host The Bitwig controller host
     * @param target The private selection-following target
     */
    SelectedTrackTargetState (final ControllerHost host, final CursorTrack target)
    {
        final CursorTrack checkedTarget = Objects.requireNonNull (target, "target");

        this.targetID = checkedTarget.channelId ();
        this.targetExists = checkedTarget.exists ();
        this.targetCanHoldNotes = checkedTarget.canHoldNoteData ();

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
        this.targetCanHoldNotes.markInterested ();
    }


    /** {@inheritDoc} */
    @Override
    public String getChannelID ()
    {
        return this.targetID.get ();
    }


    /** {@inheritDoc} */
    @Override
    public boolean doesExist ()
    {
        return this.targetExists.get ();
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


    private record DrumCandidateState (BooleanValue exists, BooleanValue hasPads)
    {
        private boolean isReady ()
        {
            return this.exists.get () && this.hasPads.get ();
        }
    }
}
