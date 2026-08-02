// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ParameterTargetId;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Combined generic-remote and managed-helper parameter tests.
 */
class CompositeSelectedTrackParameterHostTest
{
    @Test
    void preservesGenericRemotesAndReservesDrumPitchForTheManagedTarget ()
    {
        final FakeHost remotes = new FakeHost (new SelectedTrackParameterHost.State (3, "track-a", "Pull", List.of (
            slot (0, "Pull", "Other", 0.2),
            slot (1, "Pull", "Drum Pitch", 0.9),
            slot (2, " pull ", " DRUM PITCH ", 0.8),
            slot (3, "Other Page", "Drum Pitch", 0.7))));
        final FakeHost managed = new FakeHost (new SelectedTrackParameterHost.State (5, "track-a", "Pull", List.of (
            slot (0, "Pull", "Drum Pitch", 0.75))));
        final CompositeSelectedTrackParameterHost composite = new CompositeSelectedTrackParameterHost (remotes, managed);

        composite.refresh ();

        assertTrue (composite.state ().slots ().get (0).exists ());
        assertFalse (composite.state ().slots ().get (1).exists ());
        assertFalse (composite.state ().slots ().get (2).exists ());
        assertTrue (composite.state ().slots ().get (3).exists ());
        final SelectedTrackParameterHost.Slot drumPitch = composite.state ().slots ().get (4);
        assertEquals (CompositeSelectedTrackParameterHost.DRUM_PITCH_TARGET, drumPitch.targetId ());
        assertTrue (drumPitch.exists ());
        assertEquals (0.75, drumPitch.normalizedValue ());

        final long generation = composite.state ().generation ();
        composite.setImmediately (generation, new ParameterTargetId (0), 0.4);
        composite.setImmediately (generation, CompositeSelectedTrackParameterHost.DRUM_PITCH_TARGET, 0.6);

        assertEquals (List.of (new Write (3, new ParameterTargetId (0), 0.4)), remotes.writes);
        assertEquals (List.of (new Write (5, new ParameterTargetId (0), 0.6)), managed.writes);
    }


    @Test
    void mismatchedSelectedTracksFailClosedUntilBothChildrenAgree ()
    {
        final FakeHost remotes = new FakeHost (new SelectedTrackParameterHost.State (1, "track-a", "Pull", List.of (slot (0, "Pull", "Other", 0.2))));
        final FakeHost managed = new FakeHost (new SelectedTrackParameterHost.State (1, "track-b", "Pull", List.of (slot (0, "Pull", "Drum Pitch", 0.5))));
        final CompositeSelectedTrackParameterHost composite = new CompositeSelectedTrackParameterHost (remotes, managed);

        composite.refresh ();
        final long splitGeneration = composite.state ().generation ();
        assertEquals ("", composite.state ().trackId ());
        assertTrue (composite.state ().slots ().stream ().noneMatch (SelectedTrackParameterHost.Slot::exists));
        assertThrows (IllegalArgumentException.class, () -> composite.setImmediately (splitGeneration, CompositeSelectedTrackParameterHost.DRUM_PITCH_TARGET, 0.8));

        managed.current = new SelectedTrackParameterHost.State (2, "track-a", "Pull", List.of (slot (0, "Pull", "Drum Pitch", 0.5)));
        composite.refresh ();

        assertEquals ("track-a", composite.state ().trackId ());
        assertTrue (composite.state ().generation () > splitGeneration);
        assertTrue (composite.state ().slots ().stream ().allMatch (SelectedTrackParameterHost.Slot::coherent));
    }


    @Test
    void aFreshChildGenerationFencesPreparedCompositeWrites ()
    {
        final FakeHost remotes = new FakeHost (new SelectedTrackParameterHost.State (1, "track-a", "Pull", List.of (slot (0, "Pull", "Other", 0.2))));
        final FakeHost managed = new FakeHost (new SelectedTrackParameterHost.State (1, "track-a", "Pull", List.of (slot (0, "Pull", "Drum Pitch", 0.5))));
        final CompositeSelectedTrackParameterHost composite = new CompositeSelectedTrackParameterHost (remotes, managed);
        composite.refresh ();
        final long preparedGeneration = composite.state ().generation ();

        managed.current = new SelectedTrackParameterHost.State (2, "track-b", "Pull", List.of (slot (0, "Pull", "Drum Pitch", 0.5)));

        assertThrows (IllegalStateException.class, () -> composite.setImmediately (preparedGeneration, CompositeSelectedTrackParameterHost.DRUM_PITCH_TARGET, 0.8));
        assertTrue (managed.writes.isEmpty ());
        assertTrue (composite.state ().generation () > preparedGeneration);
    }


    private static SelectedTrackParameterHost.Slot slot (final long target, final String page, final String name, final double value)
    {
        return new SelectedTrackParameterHost.Slot (new ParameterTargetId (target), page, name, true, value, true);
    }


    private record Write (long generation, ParameterTargetId targetId, double normalizedValue)
    {
    }


    private static final class FakeHost implements SelectedTrackParameterHost
    {
        private final List<Write> writes = new ArrayList<> ();
        private State current;


        private FakeHost (final State current)
        {
            this.current = current;
        }


        /** {@inheritDoc} */
        @Override
        public boolean refresh ()
        {
            return false;
        }


        /** {@inheritDoc} */
        @Override
        public State state ()
        {
            return this.current;
        }


        /** {@inheritDoc} */
        @Override
        public void setImmediately (final long generation, final ParameterTargetId targetId, final double normalizedValue)
        {
            if (generation != this.current.generation ())
                throw new IllegalArgumentException ("stale generation");
            this.writes.add (new Write (generation, targetId, normalizedValue));
        }
    }
}
