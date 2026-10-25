package de.mossgrabers.bitwig.framework.midi;

import com.bitwig.extension.controller.api.*;
import de.mossgrabers.bitwig.framework.daw.HostImpl;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.IDrumDevice;
import de.mossgrabers.pull.core.api.DesiredDrumTarget;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.function.Supplier;
import static de.mossgrabers.pull.shell.testing.TestProxies.*;
import static org.junit.jupiter.api.Assertions.*;

class GroupDrumTargetTest
{
    @Test void childAssignmentNeedsReadbackAndNeverChangesEditorSelection ()
    {
        final Fixture f = new Fixture ();
        final var scan = f.target.snapshot ();
        final var request = new DesiredDrumTarget (scan.selectionGeneration (), scan.selectionId (), scan.revision (), "drums");
        f.target.refresh (request);
        f.advance ();
        f.target.refresh (request);
        assertFalse (f.target.valid ());
        assertEquals ("group", f.rootId, "selectChannel is only a request");
        f.advance ();
        f.target.refresh (request);
        assertTrue (f.target.valid ());
        assertEquals ("group", f.selectedId);
        assertEquals ("drums", f.target.target ().snapshot ().trackID ());
        assertFalse (f.target.snapshot ().hasDrums (), "root assignment does not acknowledge descendant delivery");
        f.hasDrums = true; // Later device read-back arrives without a track/catalog change.
        f.target.refresh (request);
        assertTrue (f.target.valid ());
        f.target.target ().submitNoteInputRoute (true);
        assertEquals (List.of ("drums:on"), f.routes);
        assertTrue (f.target.snapshot ().hasDrums ());

        f.childId = "replacement";
        assertFalse (f.target.valid (), "same position with a new UUID revokes the old target immediately");
        assertFalse (f.target.target ().doesExist ());
        f.target.target ().submitNoteInputRoute (false);
        assertEquals (List.of ("drums:on", "drums:off"), f.routes);
        f.target.refresh (request);
        assertFalse (f.target.active (), "a stale catalog request cannot retarget the replacement");
    }

    @Test void inactiveTargetReleasesRestoredPinAndResumesSelectionFollowing ()
    {
        final Fixture f = new Fixture ();
        f.pinned = true;
        f.rootId = "old-child";
        f.target.refresh (DesiredDrumTarget.inactive ());
        assertTrue (f.pinned, "unpin is only a submission until host advancement");
        assertEquals ("old-child", f.rootId);
        f.advance ();
        assertFalse (f.pinned);
        assertEquals ("group", f.rootId);
        f.target.refresh (DesiredDrumTarget.inactive ());
        assertTrue (f.submitted.isEmpty (), "an acknowledged idle cursor does not churn pin writes");
    }

    @Test void leavingGroupRevokesChildEvenBeforeTheNextRefresh ()
    {
        final Fixture f = new Fixture ();
        final var scan = f.target.snapshot ();
        final var request = new DesiredDrumTarget (scan.selectionGeneration (), scan.selectionId (), scan.revision (), "drums");
        f.target.refresh (request); f.advance (); f.target.refresh (request); f.advance (); f.target.refresh (request);
        assertTrue (f.target.target ().doesExist ());
        f.selectedId = "other";
        assertFalse (f.target.target ().doesExist ());
        assertEquals ("", f.target.snapshot ().scannedTrackId ());
    }

    @Test void delayedChildBankCannotBeRelabeledAsTheNewGroup ()
    {
        final Fixture f = new Fixture ();
        f.hasDrums = true;
        final var old = f.target.snapshot ();
        final var request = new DesiredDrumTarget (old.selectionGeneration (), old.selectionId (), old.revision (), "drums");
        f.target.refresh (request); f.advance (); f.target.refresh (request); f.advance (); f.target.refresh (request);
        assertTrue (f.target.snapshot ().hasDrums ());

        f.selectedId = "new-group"; // Selection arrives before the child bank or retained root.
        final var delayed = f.target.snapshot ();
        assertTrue (delayed.children ().isEmpty (), "old children cannot be stamped with the new selection");
        final var stale = new DesiredDrumTarget (delayed.selectionGeneration (), delayed.selectionId (), delayed.revision (), "drums");
        for (int i = 0; i < 3; i++) f.target.refresh (stale);
        assertFalse (f.target.valid ());
        assertFalse (f.target.snapshot ().hasDrums ());
        assertEquals ("", f.target.snapshot ().scannedTrackId ());
        f.advance ();
        assertEquals ("new-group", f.selectedId);

        f.childId = "new-drums";
        f.childParentId = "new-group"; // Later host delivery establishes the new child owner.
        final var current = f.target.snapshot ();
        assertEquals (List.of ("new-drums"), current.children ().stream ().map (c -> c.channelId ()).toList ());
        final var next = new DesiredDrumTarget (current.selectionGeneration (), current.selectionId (), current.revision (), "new-drums");
        f.target.refresh (next); f.advance (); f.target.refresh (next); f.advance (); f.target.refresh (next);
        assertTrue (f.target.valid ());
        assertEquals ("new-drums", f.target.snapshot ().scannedTrackId ());
        assertEquals ("new-group", f.selectedId);

        f.childParentId = "elsewhere"; // Moving the child away revokes it even if its UUID is unchanged.
        assertFalse (f.target.target ().doesExist ());
        assertFalse (f.target.snapshot ().hasDrums ());
    }

    @Test void eachRowAndTheRetainedRootNeedTheirOwnParentReadback ()
    {
        final Fixture f = new Fixture ();
        f.hasDrums = true;
        final var old = f.target.snapshot ();
        final var oldRequest = new DesiredDrumTarget (old.selectionGeneration (), old.selectionId (), old.revision (), "drums");
        f.target.refresh (oldRequest); f.advance (); f.target.refresh (oldRequest); f.advance (); f.target.refresh (oldRequest);
        assertTrue (f.target.valid ());

        f.selectedId = f.childParentId = "new-group"; // Row parent arrives before its UUID.
        final var partial = f.target.snapshot ();
        final var request = new DesiredDrumTarget (partial.selectionGeneration (), partial.selectionId (), partial.revision (), "drums");
        f.target.refresh (request); f.target.refresh (request); f.target.refresh (request);
        assertFalse (f.target.valid (), "the old root cannot acknowledge a newly scoped request");
        f.advance ();
        f.target.refresh (request);
        assertFalse (f.target.valid (), "even a submitted stale row still resolves to its old parent");

        f.childId = "new-drums";
        f.secondChildId = "old-samples";
        f.secondParentId = "group";
        assertTrue (f.target.snapshot ().children ().isEmpty (), "one updated row cannot vouch for another stale row");
        f.secondChildId = ""; // New group has only one child.
        final var current = f.target.snapshot ();
        final var next = new DesiredDrumTarget (current.selectionGeneration (), current.selectionId (), current.revision (), "new-drums");
        f.target.refresh (next); f.advance (); f.target.refresh (next); f.advance ();
        f.rootParentId = "group"; // Root UUID arrives before its parent read-back.
        f.target.refresh (next);
        assertFalse (f.target.snapshot ().hasDrums ());
        f.rootParentId = "new-group";
        f.target.refresh (next);
        assertTrue (f.target.snapshot ().hasDrums ());
        assertEquals ("new-group", f.selectedId);

        f.selectedId = "empty-group";
        f.childId = "";
        assertTrue (f.target.snapshot ().children ().isEmpty ());
        assertFalse (f.target.valid ());
    }

    private static final class Fixture
    {
        String selectedId = "group", rootId = "group", childId = "drums", childParentId = "group", rootParentId = "", secondChildId = "", secondParentId = "";
        boolean pinned, hasDrums;
        final List<Runnable> submitted = new ArrayList<> ();
        final List<String> routes = new ArrayList<> ();
        final GroupDrumTarget target;

        Fixture ()
        {
            final CursorTrack child = track (() -> this.childId, false);
            final CursorTrack empty = track (() -> "", false);
            final CursorTrack second = track (() -> this.secondChildId, false, null, () -> this.secondParentId);
            final TrackBank bank = proxy (TrackBank.class, (p, m, a) -> switch (m.getName ()) {
                case "itemCount" -> value (IntegerValue.class, () -> this.secondChildId.isEmpty () ? 1 : 2);
                case "getItemAt" -> ((Integer) a[0]) == 0 ? child : ((Integer) a[0]) == 1 ? second : empty;
                default -> relaxedValue (m.getReturnType ());
            });
            final CursorTrack selection = track (() -> this.selectedId, true, bank);
            final CursorTrack root = track (() -> this.rootId, false, null, () -> this.rootParentId);
            final var host = new HostImpl (relaxedProxy (ControllerHost.class));
            final var input = new NoteInputImpl (relaxedProxy (NoteInput.class));
            final var state = new SelectedTrackTargetState (host, selection, input, false);
            final ICursorTrack modelTrack = proxy (ICursorTrack.class, (p, m, a) -> switch (m.getName ()) {
                case "doesExist" -> true;
                case "getChannelID" -> this.selectedId;
                default -> relaxedValue (m.getReturnType ());
            });
            final IDrumDevice drum = proxy (IDrumDevice.class, (p, m, a) -> switch (m.getName ()) {
                case "doesExist", "hasDrumPads" -> this.hasDrums;
                default -> relaxedValue (m.getReturnType ());
            });
            final IModel model = proxy (IModel.class, (p, m, a) -> switch (m.getName ()) {
                case "getCursorTrack" -> modelTrack;
                case "getDrumDevice" -> drum;
                default -> relaxedValue (m.getReturnType ());
            });
            this.target = new GroupDrumTarget (host, state, selection, model, root, input);
        }
        void advance () { final var work = List.copyOf (this.submitted); this.submitted.clear (); work.forEach (Runnable::run); }
        CursorTrack track (Supplier<String> id, boolean group) { return this.track (id, group, null); }
        CursorTrack track (Supplier<String> id, boolean group, TrackBank children)
        {
            return this.track (id, group, children, () -> this.childParentId);
        }
        CursorTrack track (Supplier<String> id, boolean group, TrackBank children, Supplier<String> parentId)
        {
            return proxy (CursorTrack.class, (p, m, a) -> switch (m.getName ()) {
                case "channelId" -> value (StringValue.class, id);
                case "name" -> value (SettableStringValue.class, id);
                case "exists" -> value (BooleanValue.class, () -> !id.get ().isEmpty ());
                case "isGroup" -> value (BooleanValue.class, () -> group);
                case "canHoldNoteData" -> value (SettableBooleanValue.class, () -> !group);
                case "createTrackBank" -> children;
                case "createParentTrack" -> track (parentId, true);
                case "isPinned" -> proxy (SettableBooleanValue.class, (x, method, args) -> {
                    if (method.getName ().equals ("get")) return this.pinned;
                    if (method.getName ().equals ("set")) { final boolean next = (Boolean) args[0]; this.submitted.add (() -> { this.pinned = next; if (!next) this.rootId = this.selectedId; }); }
                    return relaxedValue (method.getReturnType ());
                });
                case "selectChannel" -> { final String next = ((Track) a[0]).channelId ().get (); this.submitted.add (() -> { this.rootId = next; this.rootParentId = next.equals ("drums") ? "group" : this.childParentId; if (!this.pinned) this.selectedId = next; }); yield null; }
                case "addNoteSource", "removeNoteSource" -> { this.routes.add (id.get () + (m.getName ().equals ("addNoteSource") ? ":on" : ":off")); yield null; }
                default -> relaxedValue (m.getReturnType ());
            });
        }
        static <T> T value (Class<T> type, Supplier<?> supplier)
        {
            return proxy (type, (p, m, a) -> m.getName ().equals ("get") ? supplier.get () : relaxedValue (m.getReturnType ()));
        }
    }
}
