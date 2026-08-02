// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ParameterTargetId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/**
 * Combines the generic selected-track remote canopy with the managed Drum Pitch helper.
 *
 * <p>Remote slots retain target IDs {@code 0..7}; the managed helper owns target ID {@code 8}.
 * The exact {@code Pull / Drum Pitch} remote identity is reserved for the managed target, so an
 * old session Macro cannot become an ambiguous or accidental fallback. All other tagged remotes
 * remain available to reloadable cores.</p>
 */
final class CompositeSelectedTrackParameterHost implements SelectedTrackParameterHost
{
    static final ParameterTargetId DRUM_PITCH_TARGET = new ParameterTargetId (SelectedTrackRemoteParameterHost.SLOT_COUNT);

    private final SelectedTrackParameterHost remoteHost;
    private final SelectedTrackParameterHost drumPitchHost;

    private State state = State.empty ();
    private CompositeIdentity observedIdentity;
    private long generation;


    /**
     * Constructor.
     *
     * @param remoteHost Generic tagged-remote host
     * @param drumPitchHost Managed Bend host
     */
    CompositeSelectedTrackParameterHost (final SelectedTrackParameterHost remoteHost, final SelectedTrackParameterHost drumPitchHost)
    {
        this.remoteHost = Objects.requireNonNull (remoteHost, "remoteHost");
        this.drumPitchHost = Objects.requireNonNull (drumPitchHost, "drumPitchHost");
    }


    /** {@inheritDoc} */
    @Override
    public boolean refresh ()
    {
        final State previous = this.state;
        this.remoteHost.refresh ();
        this.drumPitchHost.refresh ();
        this.rebuild ();
        return !previous.equals (this.state);
    }


    /** {@inheritDoc} */
    @Override
    public State state ()
    {
        return this.state;
    }


    /** {@inheritDoc} */
    @Override
    public void setImmediately (final long catalogGeneration, final ParameterTargetId targetId, final double normalizedValue)
    {
        Objects.requireNonNull (targetId, "targetId");
        if (catalogGeneration != this.generation)
            throw new IllegalArgumentException ("Parameter-catalog generation is stale");

        final Slot target = this.state.slots ().stream ()
            .filter (slot -> targetId.equals (slot.targetId ()))
            .findFirst ()
            .orElseThrow ( () -> new IllegalArgumentException ("Parameter target is unavailable"));
        if (!target.exists () || !target.coherent ())
            throw new IllegalArgumentException ("Parameter target is not coherent and available");

        final CompositeIdentity expectedIdentity = this.observedIdentity;
        if (expectedIdentity == null)
            throw new IllegalStateException ("Selected-track parameter identity is unavailable");

        if (DRUM_PITCH_TARGET.equals (targetId))
        {
            final State child = this.drumPitchHost.state ();
            if (child.generation () != expectedIdentity.drumPitchGeneration () || !this.state.trackId ().equals (child.trackId ()))
            {
                this.rebuild ();
                throw new IllegalStateException ("Managed parameter identity changed before write");
            }
            this.drumPitchHost.setImmediately (expectedIdentity.drumPitchGeneration (), new ParameterTargetId (0), normalizedValue);
            return;
        }

        final long value = targetId.value ();
        if (value < 0 || value >= SelectedTrackRemoteParameterHost.SLOT_COUNT)
            throw new IllegalArgumentException ("Parameter target is outside the stable canopy");
        final State child = this.remoteHost.state ();
        if (child.generation () != expectedIdentity.remoteGeneration () || !this.state.trackId ().equals (child.trackId ()))
        {
            this.rebuild ();
            throw new IllegalStateException ("Remote parameter identity changed before write");
        }
        this.remoteHost.setImmediately (expectedIdentity.remoteGeneration (), new ParameterTargetId (value), normalizedValue);
    }


    private void rebuild ()
    {
        final State remote = this.remoteHost.state ();
        final State drumPitch = this.drumPitchHost.state ();
        final CompositeIdentity identity = new CompositeIdentity (remote.generation (), remote.trackId (), drumPitch.generation (), drumPitch.trackId ());
        if (!identity.equals (this.observedIdentity))
        {
            this.observedIdentity = identity;
            this.generation = Math.incrementExact (this.generation);
        }

        final boolean sameTrack = !remote.trackId ().isEmpty () && remote.trackId ().equals (drumPitch.trackId ());
        final List<Slot> slots = new ArrayList<> (SelectedTrackRemoteParameterHost.SLOT_COUNT + 1);
        for (final Slot remoteSlot: remote.slots ())
        {
            final boolean reserved = SelectedTrackDrumPitchHost.PAGE_NAME.equalsIgnoreCase (remoteSlot.pageName ().trim ()) && SelectedTrackDrumPitchHost.PARAMETER_NAME.equalsIgnoreCase (remoteSlot.name ().trim ());
            slots.add (new Slot (
                new ParameterTargetId (remoteSlot.targetId ().value ()),
                remoteSlot.pageName (),
                remoteSlot.name (),
                sameTrack && !reserved && remoteSlot.exists (),
                remoteSlot.normalizedValue (),
                sameTrack && remoteSlot.coherent ()));
        }

        if (!drumPitch.slots ().isEmpty ())
        {
            final Slot managed = drumPitch.slots ().getFirst ();
            slots.add (new Slot (
                DRUM_PITCH_TARGET,
                managed.pageName (),
                managed.name (),
                sameTrack && managed.exists (),
                managed.normalizedValue (),
                sameTrack && managed.coherent ()));
        }
        this.state = new State (this.generation, sameTrack ? remote.trackId () : "", "", slots);
    }


    private record CompositeIdentity (long remoteGeneration, String remoteTrackId, long drumPitchGeneration, String drumPitchTrackId)
    {
        private CompositeIdentity
        {
            remoteTrackId = Objects.requireNonNull (remoteTrackId, "remoteTrackId");
            drumPitchTrackId = Objects.requireNonNull (drumPitchTrackId, "drumPitchTrackId");
        }
    }
}
