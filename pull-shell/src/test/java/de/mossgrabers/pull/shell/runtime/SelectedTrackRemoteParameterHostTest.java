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
 * Deterministic selected-track remote-control state-machine tests.
 */
class SelectedTrackRemoteParameterHostTest
{
    @Test
    void waitsForAsynchronousTrackAndPageRetargetingThenRequiresTwoSamples ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", true, List.of ("Pull"), 0, "Pull", slots ("Pitch", 0.25)));
        final SelectedTrackRemoteParameterHost host = new SelectedTrackRemoteParameterHost (adapter);

        host.refresh ();
        assertEquals (1, adapter.followRequests);
        assertFalse (host.state ().slots ().get (0).coherent ());

        // A command does not synchronously alter fake host state.
        host.refresh ();
        assertEquals (2, adapter.followRequests);
        assertFalse (host.state ().slots ().get (0).coherent ());

        adapter.current = sample ("track-a", false, List.of ("Other", "Pull"), 0, "Other", slots ("Wrong page", 0.75));
        host.refresh ();
        final long wrongPageGeneration = host.state ().generation ();
        assertEquals (List.of (Integer.valueOf (1)), adapter.pageRequests);
        assertFalse (host.state ().slots ().get (0).coherent ());

        host.refresh ();
        assertEquals (wrongPageGeneration, host.state ().generation ());
        assertEquals (List.of (Integer.valueOf (1), Integer.valueOf (1)), adapter.pageRequests);
        assertFalse (host.state ().slots ().get (0).coherent ());

        adapter.current = sample ("track-a", false, List.of ("Other", "Pull"), 1, "Pull", slots ("Pitch", 0.25));
        host.refresh ();
        final long pullGeneration = host.state ().generation ();
        assertFalse (host.state ().slots ().get (0).coherent ());
        assertTrue (host.refresh ());
        assertEquals (pullGeneration, host.state ().generation ());
        assertTrue (host.state ().slots ().stream ().allMatch (SelectedTrackParameterHost.Slot::coherent));

        adapter.current = sample ("track-b", false, List.of ("Other", "Pull"), 1, "Pull", slots ("Pitch", 0.25));
        host.refresh ();
        assertEquals (pullGeneration + 1, host.state ().generation ());
        assertEquals ("track-b", host.state ().trackId ());
        assertTrue (host.state ().slots ().stream ().noneMatch (SelectedTrackParameterHost.Slot::coherent));
        host.refresh ();
        assertTrue (host.state ().slots ().stream ().allMatch (SelectedTrackParameterHost.Slot::coherent));
    }


    @Test
    void duplicateParameterNamesKeepDistinctStableSlotIds ()
    {
        final List<SelectedTrackRemoteParameterHost.SlotSample> slots = slots ("Drum Pitch", 0.2);
        slots.set (1, new SelectedTrackRemoteParameterHost.SlotSample ("Drum Pitch", true, 0.8));
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", false, List.of ("Pull"), 0, "Pull", slots));
        final SelectedTrackRemoteParameterHost host = coherentHost (adapter);

        assertEquals (new ParameterTargetId (0), host.state ().slots ().get (0).targetId ());
        assertEquals (new ParameterTargetId (1), host.state ().slots ().get (1).targetId ());
        assertEquals ("Drum Pitch", host.state ().slots ().get (0).name ());
        assertEquals ("Drum Pitch", host.state ().slots ().get (1).name ());

        host.setImmediately (host.state ().generation (), new ParameterTargetId (1), 0.6);
        assertEquals (List.of (new Write (1, 0.6)), adapter.writes);
    }


    @Test
    void structuralRetargetsFenceStaleGenerationsAndWrites ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", false, List.of ("Pull"), 0, "Pull", slots ("Pitch", 0.25)));
        final SelectedTrackRemoteParameterHost host = coherentHost (adapter);
        final long oldGeneration = host.state ().generation ();

        assertThrows (IllegalArgumentException.class, () -> host.setImmediately (oldGeneration - 1, new ParameterTargetId (0), 0.5));
        assertTrue (adapter.writes.isEmpty ());

        adapter.current = sample ("track-a", false, List.of ("Pull"), 0, "Pull", slots ("Transpose", 0.25));
        host.refresh ();
        assertEquals (oldGeneration + 1, host.state ().generation ());
        assertFalse (host.state ().slots ().get (0).coherent ());
        assertThrows (IllegalArgumentException.class, () -> host.setImmediately (oldGeneration, new ParameterTargetId (0), 0.5));
        assertThrows (IllegalArgumentException.class, () -> host.setImmediately (host.state ().generation (), new ParameterTargetId (0), 0.5));

        host.refresh ();
        assertTrue (host.state ().slots ().get (0).coherent ());
        host.setImmediately (host.state ().generation (), new ParameterTargetId (0), 0.5);
        assertEquals (List.of (new Write (0, 0.5)), adapter.writes);

        final long renamedGeneration = host.state ().generation ();
        final List<SelectedTrackRemoteParameterHost.SlotSample> missing = slots ("Transpose", 0.25);
        missing.set (0, new SelectedTrackRemoteParameterHost.SlotSample ("Transpose", false, 0.25));
        adapter.current = sample ("track-a", false, List.of ("Pull"), 0, "Pull", missing);
        host.refresh ();
        host.refresh ();
        assertEquals (renamedGeneration + 1, host.state ().generation ());
        assertTrue (host.state ().slots ().get (0).coherent ());
        assertFalse (host.state ().slots ().get (0).exists ());
        assertThrows (IllegalArgumentException.class, () -> host.setImmediately (host.state ().generation (), new ParameterTargetId (0), 0.5));
    }


    @Test
    void writeStateChangesOnlyAfterAuthoritativeReadback ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", false, List.of ("Pull"), 0, "Pull", slots ("Pitch", 0.25)));
        final SelectedTrackRemoteParameterHost host = coherentHost (adapter);
        final long generation = host.state ().generation ();

        host.setImmediately (generation, new ParameterTargetId (0), 0.9);
        assertEquals (List.of (new Write (0, 0.9)), adapter.writes);
        assertEquals (0.25, host.state ().slots ().get (0).normalizedValue ());

        assertFalse (host.refresh ());
        assertEquals (0.25, host.state ().slots ().get (0).normalizedValue ());

        adapter.current = sample ("track-a", false, List.of ("Pull"), 0, "Pull", slots ("Pitch", 0.9));
        assertTrue (host.refresh ());
        assertEquals (generation, host.state ().generation ());
        assertEquals (0.9, host.state ().slots ().get (0).normalizedValue ());
        assertTrue (host.state ().slots ().get (0).coherent ());
    }


    @Test
    void freshPrewriteSampleRejectsAnUnrefreshedIdentityChange ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", false, List.of ("Pull"), 0, "Pull", slots ("Pitch", 0.25)));
        final SelectedTrackRemoteParameterHost host = coherentHost (adapter);
        final long oldGeneration = host.state ().generation ();

        adapter.current = sample ("track-b", false, List.of ("Pull"), 0, "Pull", slots ("Pitch", 0.25));
        assertThrows (IllegalStateException.class, () -> host.setImmediately (oldGeneration, new ParameterTargetId (0), 0.8));
        assertTrue (adapter.writes.isEmpty ());
        assertEquals (oldGeneration + 1, host.state ().generation ());
        assertEquals ("track-b", host.state ().trackId ());
        assertFalse (host.state ().slots ().get (0).coherent ());
    }


    @Test
    void duplicatePullPagesNeverBecomeCoherent ()
    {
        final FakeAdapter adapter = new FakeAdapter (sample ("track-a", false, List.of ("Pull", "Pull"), 0, "Pull", slots ("Pitch", 0.25)));
        final SelectedTrackRemoteParameterHost host = new SelectedTrackRemoteParameterHost (adapter);

        host.refresh ();
        host.refresh ();
        host.refresh ();

        assertTrue (host.state ().slots ().stream ().noneMatch (SelectedTrackParameterHost.Slot::coherent));
        assertTrue (adapter.pageRequests.isEmpty ());
        assertThrows (IllegalArgumentException.class, () -> host.setImmediately (host.state ().generation (), new ParameterTargetId (0), 0.5));
    }


    @Test
    void incompletePageNameSnapshotsNeverBecomeCoherent ()
    {
        final FakeAdapter adapter = new FakeAdapter (new SelectedTrackRemoteParameterHost.RemoteSample (
            true,
            "track-a",
            false,
            2,
            List.of ("Pull"),
            0,
            "Pull",
            slots ("Pitch", 0.25)));
        final SelectedTrackRemoteParameterHost host = new SelectedTrackRemoteParameterHost (adapter);

        host.refresh ();
        host.refresh ();
        host.refresh ();

        assertTrue (host.state ().slots ().stream ().noneMatch (SelectedTrackParameterHost.Slot::coherent));
    }


    private static SelectedTrackRemoteParameterHost coherentHost (final FakeAdapter adapter)
    {
        final SelectedTrackRemoteParameterHost host = new SelectedTrackRemoteParameterHost (adapter);
        host.refresh ();
        host.refresh ();
        assertTrue (host.state ().slots ().stream ().allMatch (SelectedTrackParameterHost.Slot::coherent));
        return host;
    }


    private static SelectedTrackRemoteParameterHost.RemoteSample sample (final String trackId, final boolean pinned, final List<String> pageNames, final int selectedPageIndex, final String pageName, final List<SelectedTrackRemoteParameterHost.SlotSample> slots)
    {
        return new SelectedTrackRemoteParameterHost.RemoteSample (true, trackId, pinned, pageNames.size (), pageNames, selectedPageIndex, pageName, slots);
    }


    private static List<SelectedTrackRemoteParameterHost.SlotSample> slots (final String firstName, final double firstValue)
    {
        final List<SelectedTrackRemoteParameterHost.SlotSample> result = new ArrayList<> (SelectedTrackRemoteParameterHost.SLOT_COUNT);
        result.add (new SelectedTrackRemoteParameterHost.SlotSample (firstName, true, firstValue));
        for (int index = 1; index < SelectedTrackRemoteParameterHost.SLOT_COUNT; index++)
            result.add (new SelectedTrackRemoteParameterHost.SlotSample ("", false, 0));
        return result;
    }


    private record Write (int slotIndex, double normalizedValue)
    {
    }


    private static final class FakeAdapter implements SelectedTrackRemoteParameterHost.Adapter
    {
        private final List<Integer> pageRequests = new ArrayList<> ();
        private final List<Write> writes = new ArrayList<> ();

        private SelectedTrackRemoteParameterHost.RemoteSample current;
        private int followRequests;


        private FakeAdapter (final SelectedTrackRemoteParameterHost.RemoteSample current)
        {
            this.current = current;
        }


        /** {@inheritDoc} */
        @Override
        public SelectedTrackRemoteParameterHost.RemoteSample sample ()
        {
            return this.current;
        }


        /** {@inheritDoc} */
        @Override
        public void followSelection ()
        {
            this.followRequests++;
        }


        /** {@inheritDoc} */
        @Override
        public void selectPage (final int pageIndex)
        {
            this.pageRequests.add (Integer.valueOf (pageIndex));
        }


        /** {@inheritDoc} */
        @Override
        public void setImmediately (final int slotIndex, final double normalizedValue)
        {
            this.writes.add (new Write (slotIndex, normalizedValue));
        }
    }
}
