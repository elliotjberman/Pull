// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.concurrent.atomic.AtomicLong;
import java.util.List;
import static de.mossgrabers.bitwig.framework.daw.DrumTreeHostFake.*;
import static org.junit.jupiter.api.Assertions.*;

class DrumDeviceCacheTest
{
    @Test
    void sharesDiscoveryAndRevisitsFourTracksWithoutAnyNativeReassignment ()
    {
        final var host = new DrumCacheHostFake ();
        host.addTrack ("drums", container ("Rack", drum ("Dry"), drum ("Spread")));
        host.addTrack ("synth");
        host.addTrack ("other", drum ("Other"));
        host.addTrack ("audio", effect ("EQ"));
        final var cache = new DrumDeviceCache (host.controller, () -> host.project);
        final var model = cache.follow (host.model);
        final var selected = cache.follow (host.selected);
        List<String> drums = List.of ();
        for (final String id : List.of ("drums", "synth", "other", "audio"))
        {
            host.select (host.model, id);
            host.select (host.selected, id);
            host.publish ();
            host.settle (model, selected);
            assertEquals (model.machines (), selected.machines (), "both consumers use the same native handles");
            if (id.equals ("drums")) drums = identities (model);
        }
        assertEquals (4, host.trackSelections, "one acquisition per track, not one per consumer");
        final int selections = host.deviceSelections ();
        final int changes = host.deviceTargetChanges ();
        final int trackChanges = host.trackTargetChanges;
        for (final String id : List.of ("drums", "synth", "other", "audio", "drums"))
        {
            host.select (host.model, id);
            host.select (host.selected, id);
            host.publish ();
            assertEquals (model.machines (), selected.machines ());
            if (id.equals ("drums")) assertEquals (drums, identities (model), "warm lookup is immediately usable");
            host.settle (model, selected);
        }
        assertEquals (4, host.trackSelections);
        assertEquals (selections, host.deviceSelections ());
        assertEquals (changes, host.deviceTargetChanges ());
        assertEquals (trackChanges, host.trackTargetChanges);
        assertTrue (host.equalityValues () <= 800, "the complete cache must retain a bounded native comparison budget");
    }

    @Test
    void offscreenAcquisitionAndEvictionPreserveEditorSelection ()
    {
        final var host = new DrumCacheHostFake ();
        host.addTrack ("selected");
        for (final String id : List.of ("a", "b", "c", "d", "e")) host.addTrack (id, drum (id));
        final var cache = new DrumDeviceCache (host.controller, () -> host.project);
        final var model = cache.follow (host.model);
        cache.follow (host.selected);
        host.select (host.selected, "selected");
        host.publish ();
        for (final String id : List.of ("a", "b", "c", "d", "e", "a"))
        {
            host.select (host.model, id);
            host.publish ();
            host.settle (model);
            assertEquals (List.of (id), names (model));
            assertEquals ("selected", host.editorSelection, "private discovery cannot select an offscreen track in the editor");
        }
    }

    @Test
    void trackAcquisitionRequiresLaterReadbackBeforeAnyDeviceSearch ()
    {
        final var host = new DrumCacheHostFake ();
        host.addTrack ("drums", drum ("Drum"));
        final var cache = new DrumDeviceCache (host.controller, () -> host.project);
        final var view = cache.follow (host.selected);
        host.select (host.selected, "drums");
        host.publish ();
        assertTrue (view.machines ().isEmpty ());
        host.applyCommands ();
        assertTrue (view.machines ().isEmpty ());
        assertEquals (0, host.trackSelections, "pin submission is not an acknowledgement");
        host.publish ();
        assertTrue (view.machines ().isEmpty ());
        assertEquals (1, host.trackSelections);
        host.applyCommands ();
        assertTrue (view.machines ().isEmpty ());
        assertEquals (0, host.deviceSelections (), "applied track assignment has not been published");
        host.publish ();
        host.settle (view);
        assertEquals (List.of ("Drum"), names (view));
    }

    @Test
    void editsOnAnOffscreenTrackInvalidateOnlyThatEntry ()
    {
        final var host = new DrumCacheHostFake ();
        final var rack = container ("Rack", drum ("A"), drum ("B"));
        host.addTrack ("drums", rack);
        host.addTrack ("other", drum ("Other"));
        final var cache = new DrumDeviceCache (host.controller, () -> host.project);
        final var view = cache.follow (host.selected);
        selectAndSettle (host, view, "drums");
        final List<String> old = identities (view);
        selectAndSettle (host, view, "other");
        final List<String> other = identities (view);
        rack.layers.get (0).set (0, drum ("A"));
        host.publish ();
        assertEquals (other, identities (view));
        host.select (host.selected, "drums");
        host.publish ();
        assertFalse (identities (view).contains (old.get (0)), "a same-named replacement cannot expose the stale assignment");
        host.settle (view);
        assertEquals (List.of ("A", "B"), names (view));
        assertNotEquals (old.get (0), identities (view).get (0));
        assertEquals (old.get (1), identities (view).get (1));
        assertEquals (2, host.trackSelections, "a dirty tree is refreshed without reassigning its track");
    }

    @Test
    void aCachedEmptyTrackDetectsNewNestedDrums ()
    {
        final var host = new DrumCacheHostFake ();
        final var root = host.addTrack ("empty");
        host.addTrack ("other", drum ("Other"));
        final var cache = new DrumDeviceCache (host.controller, () -> host.project);
        final var view = cache.follow (host.selected);
        selectAndSettle (host, view, "empty");
        assertTrue (view.machines ().isEmpty ());
        selectAndSettle (host, view, "other");
        root.add (container ("New rack", drum ("New drum")));
        host.publish ();
        selectAndSettle (host, view, "empty");
        assertEquals (List.of ("New drum"), names (view));
        assertEquals (2, host.trackSelections);
    }

    @Test
    void evictionProtectsBothLiveConsumersAndRetiresOldIdentities ()
    {
        final var host = new DrumCacheHostFake ();
        for (final String id : List.of ("pinned", "a", "b", "c", "d")) host.addTrack (id, drum (id));
        final var cache = new DrumDeviceCache (host.controller, () -> host.project);
        final var model = cache.follow (host.model);
        final var selected = cache.follow (host.selected);
        host.select (host.model, "pinned");
        host.publish ();
        host.settle (model);
        final var pinned = model.machines ().get (0);
        final String pinnedId = pinned.identity ();
        selectAndSettle (host, selected, "a");
        final List<String> a = identities (selected);
        for (final String id : List.of ("b", "c", "d")) selectAndSettle (host, selected, id);
        assertEquals (pinnedId, pinned.identity (), "even an unread but still selected model target is protected");
        assertTrue (model.contains (pinned));
        assertFalse (selected.contains (pinned), "retention alone does not make a target applicable to the other consumer");
        selectAndSettle (host, selected, "a");
        assertEquals (List.of ("a"), names (selected));
        assertNotEquals (a, identities (selected), "an evicted assignment must never be resurrected");
        assertEquals (6, host.trackSelections);
    }

    @Test
    void projectChangeRejectsCachedHandlesEvenWhenTrackUuidsRepeat ()
    {
        final var host = new DrumCacheHostFake ();
        host.addTrack ("drums", drum ("Old project"));
        final var cache = new DrumDeviceCache (host.controller, () -> host.project);
        final var view = cache.follow (host.selected);
        selectAndSettle (host, view, "drums");
        final var old = view.machines ().get (0);
        final String identity = old.identity ();
        host.project = "project-b";
        host.addTrack ("drums", drum ("New project"));
        assertFalse (old.available (), "old handles fail closed before another cache lookup");
        assertTrue (view.machines ().isEmpty ());
        host.settle (view);
        assertEquals (List.of ("New project"), names (view));
        assertNotEquals (identity, identities (view).get (0));
        host.project = "project-a";
        host.addTrack ("drums", drum ("Reopened project"));
        host.settle (view);
        assertNotEquals (identity, identities (view).get (0));
    }

    @ParameterizedTest
    @ValueSource (booleans = {false, true})
    void rapidSelectionCancelsPendingAcquisitionWithoutFollowingAnOldSource (final boolean applied)
    {
        final var host = new DrumCacheHostFake ();
        host.addTrack ("a", drum ("A"));
        host.addTrack ("b", drum ("B"));
        final var cache = new DrumDeviceCache (host.controller, () -> host.project);
        final var view = cache.follow (host.selected);
        host.select (host.selected, "a");
        host.publish ();
        view.machines ();
        if (applied) host.applyCommands ();
        host.select (host.selected, "b");
        host.publish ();
        host.advance ();
        assertTrue (host.resources.stream ().allMatch (r -> r.observedPinned), "departure must park without another cache lookup");
        assertEquals (0, host.trackSelections);
        assertTrue (view.machines ().isEmpty ());
        host.settle (view);
        assertEquals (List.of ("B"), names (view));
        assertEquals (1, host.trackSelections, "no selection may be submitted for the retired source");
        assertTrue (host.resources.stream ().allMatch (r -> r.observedPinned));
    }

    @ParameterizedTest
    @CsvSource ({"false,false", "false,true", "true,false", "true,true"})
    void departureParksPendingChildrenWithoutAnotherDrumRead (final boolean applied, final boolean referenceParent)
    {
        final var host = new DrumCacheHostFake ();
        final var first = effect ("First");
        final var second = effect ("Second");
        final var rack = container ("Rack", drum ("Original"));
        host.addTrack ("drums", referenceParent ? rack : drum ("Drum"), first, second);
        host.addTrack ("other");
        final var cache = new DrumDeviceCache (host.controller, () -> host.project);
        final var view = cache.follow (host.selected);
        selectAndSettle (host, view, "drums");
        final String original = identities (view).get (0);
        final var devices = host.resources.get (0).devices;
        if (referenceParent) rack.layers.add (new java.util.ArrayList<> (List.of (drum ("Replacement"))));
        else devices.root.set (0, drum ("Replacement"));
        requestChildUnpin (host, view, devices);
        if (applied) host.applyCommands ();
        host.select (host.selected, "other");
        host.publish ();
        host.advance ();
        final int changes = host.deviceTargetChanges ();
        devices.cursorSelection = first;
        host.publish ();
        devices.cursorSelection = second;
        host.publish ();
        assertEquals (changes, host.deviceTargetChanges (), "offscreen child cursors must stay parked without polling");
        selectAndSettle (host, view, "drums");
        assertEquals (referenceParent ? List.of ("Original", "Replacement") : List.of ("Replacement"), names (view));
        if (referenceParent) assertEquals (original, identities (view).get (0), "ready assignments survive a sibling's cancellation");
        assertEquals (1, host.trackSelections, "the retained root survives cancelled child discovery");
    }

    @Test
    void projectObserversRetirePendingChildrenEvenWhenTrackUuidDoesNotChange ()
    {
        final var host = new DrumCacheHostFake ();
        host.addTrack ("drums", drum ("Old"));
        final var cache = new HostImpl (host.controller).getDrumDeviceCache ();
        final var view = cache.follow (host.selected);
        selectAndSettle (host, view, "drums");
        final var devices = host.resources.get (0).devices;
        devices.root.set (0, drum ("Replacement"));
        requestChildUnpin (host, view, devices);
        host.project = "project-b";
        host.publish ();
        host.advance ();
        final int changes = host.deviceTargetChanges ();
        devices.cursorSelection = devices.root.get (0);
        host.publish ();
        assertEquals (changes, host.deviceTargetChanges (), "project cleanup cannot depend on a future drum read");
    }

    @Test
    void acquisitionDeadlineRetriesRejectedPinningWithoutAcknowledgingIt ()
    {
        final var host = new DrumCacheHostFake ();
        host.addTrack ("drums", drum ("Drum"));
        final AtomicLong time = new AtomicLong ();
        final var cache = new DrumDeviceCache (host.controller, () -> host.project, time::get);
        final var view = cache.follow (host.selected);
        host.rejectPin = true;
        host.select (host.selected, "drums");
        host.publish ();
        host.settle (view);
        final int requests = host.trackPinRequests;
        host.settle (view);
        assertTrue (view.machines ().isEmpty ());
        assertEquals (requests, host.trackPinRequests, "reads alone do not expire the acquisition");
        time.set (3_000_000_000L);
        assertTrue (view.machines ().isEmpty ());
        assertTrue (host.trackPinRequests > requests, "deadline permits another native pin request");
        assertEquals (0, host.trackSelections, "expiry is not a native identity acknowledgement");
        host.rejectPin = false;
        host.advance ();
        host.settle (view);
        assertEquals (List.of ("Drum"), names (view));
    }

    @Test
    void everyCacheEntryPreservesTheEightMachineLimit ()
    {
        final var host = new DrumCacheHostFake ();
        for (int track = 0; track < DrumDeviceCache.TRACK_CAPACITY; track++)
        {
            final var root = host.addTrack ("track-" + track);
            final var rack = container ("Rack");
            for (int machine = 0; machine < 8; machine++) rack.layers.add (new java.util.ArrayList<> (List.of (drum ("Drum " + machine))));
            root.add (rack);
        }
        final var cache = new DrumDeviceCache (host.controller, () -> host.project);
        final var view = cache.follow (host.selected);
        for (int track = 0; track < DrumDeviceCache.TRACK_CAPACITY; track++)
        {
            selectAndSettle (host, view, "track-" + track);
            assertEquals (8, view.machines ().size ());
            assertEquals (8, identities (view).stream ().distinct ().count ());
            assertFalse (view.limited ());
        }
    }

    private static void requestChildUnpin (final DrumCacheHostFake host, final DrumDeviceCache.View view, final DrumTreeHostFake devices)
    {
        final int requests = devices.unpinRequests;
        host.publish ();
        for (int i = 0; i < 4 && devices.unpinRequests == requests; i++) view.machines ();
        assertTrue (devices.unpinRequests > requests, "the dangerous native unpin must actually be submitted before departure");
    }

    private static void selectAndSettle (final DrumCacheHostFake host, final DrumDeviceCache.View view, final String id)
    {
        host.select (host.selected, id);
        host.publish ();
        host.settle (view);
    }

    private static List<String> identities (final DrumDeviceCache.View view) { return view.machines ().stream ().map (DrumDeviceTree.Slot::identity).toList (); }
    private static List<String> names (final DrumDeviceCache.View view) { return view.machines ().stream ().map (slot -> slot.device ().name ().get ()).toList (); }
}
