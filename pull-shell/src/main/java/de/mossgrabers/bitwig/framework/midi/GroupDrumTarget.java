// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.midi;

import com.bitwig.extension.controller.api.*;
import de.mossgrabers.bitwig.framework.daw.*;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.pull.core.api.*;
import java.util.*;

/** Observes a finite child window and executes the core's exact descendant request. No target policy. */
final class GroupDrumTarget
{
    private final SelectedTrackTargetState selection;
    private final CursorTrack selected;
    private final de.mossgrabers.framework.daw.IModel model;
    private final CursorTrack root;
    private final TrackBank children;
    private final SelectedTrackTargetState childTarget;
    private DesiredDrumTarget request = DesiredDrumTarget.inactive ();
    private List<GroupDrumScanSnapshot.Child> catalog = List.of ();
    private String scope = "";
    private long revision;
    private int phase;
    private long deadline;

    GroupDrumTarget (HostImpl host, SelectedTrackTargetState selection, CursorTrack selected, de.mossgrabers.framework.daw.IModel model, CursorTrack root, NoteInputImpl input)
    {
        this.selection = selection;
        this.selected = selected;
        this.model = model;
        this.root = root;
        this.children = selected.createTrackBank (GroupDrumScanSnapshot.CAPACITY, 0, 0, false);
        this.children.itemCount ().markInterested ();
        this.children.itemCount ().addValueObserver (ignored -> this.revision++);
        for (int i = 0; i < GroupDrumScanSnapshot.CAPACITY; i++)
        {
            final Track child = this.children.getItemAt (i);
            child.exists ().markInterested ();
            child.channelId ().markInterested ();
            child.canHoldNoteData ().markInterested ();
            child.isGroup ().markInterested ();
        }
        this.childTarget = new SelectedTrackTargetState (host, this.root, input, false);
        this.childTarget.fence (this::valid, () -> this.selection.getGeneration () + this.revision,
            () -> this.model.getDrumDevice ().doesExist () && this.model.getDrumDevice ().hasDrumPads ());
    }

    boolean active () { return this.request.active (); }
    ISelectedTrackNoteTarget target () { return this.active () ? this.childTarget : this.selection; }

    private void observeCatalog ()
    {
        final String currentScope = this.selection.getGeneration () + ":" + this.selection.getChannelID ();
        final List<GroupDrumScanSnapshot.Child> observed = new ArrayList<> ();
        if (this.selected.isGroup ().get ())
            for (int i = 0; i < GroupDrumScanSnapshot.CAPACITY; i++)
            {
                final Track child = this.children.getItemAt (i);
                final String id = child.channelId ().get ();
                if (child.exists ().get () && id != null && !id.isBlank ())
                    observed.add (new GroupDrumScanSnapshot.Child (id, child.canHoldNoteData ().get () && !child.isGroup ().get ()));
            }
        if (!this.scope.equals (currentScope) || !this.catalog.equals (observed))
        {
            this.scope = currentScope;
            this.catalog = List.copyOf (observed);
            this.revision++;
        }
    }

    private Track source (DesiredDrumTarget desired)
    {
        this.observeCatalog ();
        if (!desired.active () || !this.selected.isGroup ().get () || !this.selection.doesExist () ||
            this.selection.getGeneration () != desired.selectionGeneration () || !this.selection.getChannelID ().equals (desired.selectionId ()) || this.revision != desired.childrenRevision () ||
            !this.model.getCursorTrack ().doesExist () || !desired.selectionId ().equals (this.model.getCursorTrack ().getChannelID ())) return null;
        for (int i = 0; i < GroupDrumScanSnapshot.CAPACITY; i++)
        {
            final Track track = this.children.getItemAt (i);
            if (track.exists ().get () && desired.trackId ().equals (track.channelId ().get ()) && track.canHoldNoteData ().get () && !track.isGroup ().get ()) return track;
        }
        return null;
    }

    boolean valid ()
    {
        return this.phase == 2 && this.source (this.request) != null && this.root.isPinned ().get () &&
            this.root.exists ().get () && this.request.trackId ().equals (this.root.channelId ().get ());
    }

    boolean changing (DesiredDrumTarget desired)
    {
        return !this.request.equals (this.source (desired) == null ? DesiredDrumTarget.inactive () : desired);
    }

    void refresh (DesiredDrumTarget desired)
    {
        final Track source = this.source (desired);
        final DesiredDrumTarget checked = source == null ? DesiredDrumTarget.inactive () : desired;
        if (!this.request.equals (checked))
        {
            this.request = checked;
            this.phase = 0;
            this.deadline = System.nanoTime () + 2_000_000_000L;
            this.root.isPinned ().set (checked.active ());
            return;
        }
        if (!checked.active ())
        {
            // A named cursor can arrive pinned from host state; idle means follow selection.
            if (this.root.isPinned ().get ()) this.root.isPinned ().set (false);
            return;
        }
        if (this.phase == 0 && this.root.isPinned ().get ())
        {
            // Detach from editor selection before moving the private cursor.
            this.root.selectChannel (source);
            this.phase = 1;
        }
        else if (this.phase == 1 && this.root.isPinned ().get () && this.root.exists ().get () && checked.trackId ().equals (this.root.channelId ().get ()))
            this.phase = 2;
        else if (!this.valid () && System.nanoTime () - this.deadline >= 0)
        {
            this.phase = 0;
            this.deadline = System.nanoTime () + 2_000_000_000L;
            this.root.isPinned ().set (true);
        }
    }

    GroupDrumScanSnapshot snapshot ()
    {
        this.observeCatalog ();
        if (!this.selected.isGroup ().get ()) return GroupDrumScanSnapshot.empty ();
        final boolean ready = this.valid ();
        final boolean found = ready && this.childTarget.hasDrumDevice ();
        return new GroupDrumScanSnapshot (this.selection.getGeneration (), this.selection.getChannelID (), this.revision, this.catalog,
            this.children.itemCount ().get () > GroupDrumScanSnapshot.CAPACITY, ready ? this.request.trackId () : "", found);
    }
}
