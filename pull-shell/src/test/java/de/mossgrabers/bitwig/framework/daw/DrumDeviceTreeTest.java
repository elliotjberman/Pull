// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import static de.mossgrabers.bitwig.framework.daw.DrumTreeHostFake.*;
import static org.junit.jupiter.api.Assertions.*;

class DrumDeviceTreeTest
{
    @Test
    void findsParallelAndNestedMachinesOnceWithoutEditorSelection ()
    {
        final var host = new DrumTreeHostFake ();
        host.root.add (effect ("Note effects"));
        host.root.add (container ("Instrument Layer", drum ("Other"), container ("Spread", drum ("Percussion"))));
        host.root.add (drum ("Top-level"));
        final var tree = new DrumDeviceTree (host.track, "test");
        host.settle (tree);
        assertEquals (List.of ("Other", "Percussion", "Top-level"), names (tree));
        assertEquals (3, tree.machines ().stream ().map (DrumDeviceTree.Slot::identity).distinct ().count ());
        assertFalse (tree.limited ());
        assertEquals (0, host.editorSelections);
        // Bitwig scans all equality objects on every target change, including unrelated tracks.
        assertTrue (host.equalityValues <= 2_000, "tree identity must stay within the native comparison budget");
        final int selections = host.selections;
        host.settle (tree);
        assertEquals (selections, host.selections, "unchanged tree must not churn retained cursors");
    }

    @Test
    void waitsForHostAcknowledgementAndRejectsSameNamedReplacement ()
    {
        final var host = new DrumTreeHostFake ();
        host.root.add (drum ("Kick"));
        host.initiallyPinned = true;
        final var tree = new DrumDeviceTree (host.track, "test");
        host.publish ();
        assertTrue (tree.machines ().isEmpty ());
        assertTrue (tree.machines ().isEmpty (), "submitted selection is not applied host state");
        host.applyCommands ();
        assertTrue (tree.machines ().isEmpty (), "applied selection is not yet published host read-back");
        host.publish ();
        assertTrue (tree.machines ().isEmpty (), "unpin acknowledgement only permits selection");
        assertTrue (tree.machines ().isEmpty (), "additional reads do not acknowledge selection and pinning");
        host.applyCommands ();
        assertTrue (tree.machines ().isEmpty (), "host application still needs subscribed read-back");
        host.publish ();
        assertEquals (1, tree.machines ().size ());
        host.settle (tree);
        final String original = tree.machines ().get (0).identity ();
        host.root.set (0, drum ("Kick"));
        host.publish ();
        assertTrue (tree.machines ().isEmpty ());
        host.settle (tree);
        assertEquals (List.of ("Kick"), names (tree));
        assertNotEquals (original, tree.machines ().get (0).identity ());
    }

    @Test
    void reacquiresSameNamedReplacementBeforeFirstAcknowledgement ()
    {
        final var host = new DrumTreeHostFake ();
        host.root.add (drum ("Kick"));
        final AtomicLong time = new AtomicLong ();
        final var tree = new DrumDeviceTree (host.track, "test", time::get);
        host.publish ();
        assertTrue (tree.machines ().isEmpty ());
        host.advance ();
        assertTrue (tree.machines ().isEmpty (), "selection and pinning are only submitted");
        host.root.set (0, drum ("Kick"));
        host.applyCommands ();
        assertTrue (tree.machines ().isEmpty (), "host application is not published read-back");
        host.publish ();
        assertTrue (tree.machines ().isEmpty (), "the acknowledged cursor points at the removed device");
        host.settle (tree);
        assertTrue (tree.machines ().isEmpty (), "reads and unchanged host observations do not expire acquisition");
        assertEquals (1, host.selections);
        time.set (3_000_000_000L);
        assertTrue (tree.machines ().isEmpty (), "expiry only permits retry, never publication");
        host.settle (tree);
        assertEquals (List.of ("Kick"), names (tree));
        assertFalse (tree.limited ());
    }

    @Test
    void waitsForDelayedEqualityWithoutResubmittingOrAcceptingPinningAlone ()
    {
        final var host = new DrumTreeHostFake ();
        host.root.add (drum ("Kick"));
        host.holdEqualityObservations = true;
        final AtomicLong time = new AtomicLong ();
        final var tree = new DrumDeviceTree (host.track, "test", time::get);
        host.settle (tree);
        assertTrue (tree.machines ().isEmpty (), "pin read-back cannot acknowledge native identity");
        assertEquals (1, host.selections);
        time.set (1_000_000_000L);
        host.settle (tree);
        assertTrue (tree.machines ().isEmpty ());
        assertEquals (1, host.selections, "reads and host samples before the deadline do not retry");
        host.holdEqualityObservations = false;
        host.publish ();
        assertEquals (List.of ("Kick"), names (tree));
        assertEquals (1, host.selections);
    }

    @Test
    void capsAtEightAndRecoversWhenAnEarlierMachineIsRemoved ()
    {
        final var host = new DrumTreeHostFake ();
        for (int i = 0; i < 9; i++) host.root.add (drum ("Drum " + i));
        final var tree = new DrumDeviceTree (host.track, "test");
        host.settle (tree);
        assertEquals (8, tree.machines ().size ());
        assertTrue (tree.limited ());
        host.root.remove (0);
        host.publish ();
        host.settle (tree);
        assertEquals ("Drum 1", names (tree).get (0));
        assertEquals ("Drum 8", names (tree).get (7));
        assertFalse (tree.limited ());
        host.root.add (0, drum ("New first"));
        host.publish ();
        host.settle (tree);
        assertEquals ("New first", names (tree).get (0));
        assertEquals ("Drum 7", names (tree).get (7));
        assertTrue (tree.limited ());
    }

    @Test
    void selectionChangeRevokesOldAssignmentsAndEmptyTrackStaysEmpty ()
    {
        final var host = new DrumTreeHostFake ();
        host.root.add (container ("Layer", drum ("Old")));
        final var tree = new DrumDeviceTree (host.track, "test");
        host.settle (tree);
        final var old = tree.machines ().get (0);
        host.trackId = "track-b";
        host.root.clear ();
        host.publish ();
        assertTrue (tree.machines ().isEmpty ());
        assertFalse (old.available ());
        host.root.add (container ("New layer", drum ("New")));
        host.publish ();
        host.settle (tree);
        assertEquals (List.of ("New"), names (tree));
    }

    @Test
    void preservesSelectedFxSlotAndStopsAtDrumPadBoundary ()
    {
        final var host = new DrumTreeHostFake ();
        final var outer = effect ("Container with slot");
        outer.fx.add (drum ("Slot drum"));
        final var drum = drum ("Outer drum");
        drum.layers.add (List.of (drum ("Inside pad")));
        host.root.addAll (List.of (outer, drum));
        final var tree = new DrumDeviceTree (host.track, "test");
        host.settle (tree);
        assertEquals (List.of ("Slot drum", "Outer drum"), names (tree));
    }

    @Test
    void reportsFiniteChainLayerAndContainerCoverage ()
    {
        final var host = new DrumTreeHostFake ();
        for (int i = 0; i < DrumDeviceTree.DEVICES_PER_CHAIN + 1; i++) host.root.add (effect ("FX"));
        final var tree = new DrumDeviceTree (host.track, "test");
        host.settle (tree);
        assertTrue (tree.limited ());
        host.root.clear ();
        final var wide = container ("Wide");
        for (int i = 0; i < DrumDeviceTree.LAYERS_PER_CONTAINER + 1; i++) wide.layers.add (List.of (effect ("FX")));
        wide.layers.set (DrumDeviceTree.LAYERS_PER_CONTAINER, List.of (drum ("Outside layer window")));
        host.root.add (wide);
        host.publish ();
        host.settle (tree);
        assertTrue (tree.limited ());
        assertTrue (tree.machines ().isEmpty ());
        host.root.clear ();
        var nested = drum ("Deep drum");
        for (int i = 0; i < DrumDeviceTree.MAX_CONTAINERS + 1; i++) nested = container ("Layer", nested);
        host.root.add (nested);
        host.publish ();
        host.settle (tree);
        assertTrue (tree.limited ());
        assertTrue (tree.machines ().isEmpty ());
    }

    @Test
    void selectedSlotAliasDoesNotDuplicateMachinesOrConsumeTheEightMachineLimit ()
    {
        final var host = new DrumTreeHostFake ();
        final var rack = container ("Rack");
        for (int i = 0; i < 8; i++) rack.layers.add (new java.util.ArrayList<> (List.of (drum ("Drum " + i))));
        rack.fx.add (rack.layers.get (0).get (0));
        host.root.add (rack);
        final var tree = new DrumDeviceTree (host.track, "test");
        // Observe every acquisition frame: a pending alias must not cause a false overflow warning.
        for (int i = 0; i < 50; i++)
        {
            tree.machines ();
            assertFalse (tree.limited ());
            host.advance ();
        }
        assertEquals (8, tree.machines ().size ());
        rack.layers.remove (0);
        rack.fx.clear ();
        rack.fx.add (rack.layers.get (0).get (0));
        host.publish ();
        host.settle (tree);
        assertEquals (List.of ("Drum 1", "Drum 2", "Drum 3", "Drum 4", "Drum 5", "Drum 6", "Drum 7"), names (tree));
        assertFalse (tree.limited ());
        rack.fx.clear ();
        rack.fx.add (drum ("Real FX-slot drum"));
        host.publish ();
        host.settle (tree);
        assertEquals (8, tree.machines ().size ());
        assertEquals ("Real FX-slot drum", names (tree).get (7));
        assertFalse (tree.limited ());
    }

    @Test
    void removingALayerRetainsTheIdentityOfTheMachineThatMovesIntoItsPlace ()
    {
        final var host = new DrumTreeHostFake ();
        final var rack = container ("Rack", drum ("Dry"), drum ("Spread"));
        host.root.add (rack);
        final var tree = new DrumDeviceTree (host.track, "test");
        host.settle (tree);
        final String spread = tree.machines ().get (1).identity ();
        rack.layers.remove (0);
        host.publish ();
        host.settle (tree);
        assertEquals (List.of ("Spread"), names (tree));
        assertEquals (spread, tree.machines ().get (0).identity ());
    }

    private static List<String> names (final DrumDeviceTree tree)
    {
        return tree.machines ().stream ().map (slot -> slot.device ().name ().get ()).toList ();
    }
}
