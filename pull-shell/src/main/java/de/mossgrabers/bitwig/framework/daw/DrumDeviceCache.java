// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Shared, initialization-owned drum trees. Selection chooses a retained track entry; it does
 * not move that entry's native windows. Structural observers remain attached while offscreen.
 * Four entries permit the two independent consumers plus two idle tracks. Only idle entries
 * may be evicted, in least-recently-used order. First visits and eviction still require host ACK.
 */
public final class DrumDeviceCache
{
    public static final int TRACK_CAPACITY = 4;
    private static final long ACQUISITION_TIMEOUT_NANOS = 2_000_000_000L;
    private enum State { EMPTY, UNPINNING, PINNING, READY }

    private final Supplier<String> projectIdentity;
    private final LongSupplier nanoTime;
    private final List<Entry> entries = new ArrayList<> ();
    private final List<View> views = new ArrayList<> (2);
    private final List<DrumDeviceTree.Slot> slots;
    private String project = "";
    private long access;

    public DrumDeviceCache (final ControllerHost host, final Supplier<String> projectIdentity)
    {
        this (host, projectIdentity, System::nanoTime);
    }

    DrumDeviceCache (final ControllerHost host, final Supplier<String> projectIdentity, final LongSupplier nanoTime)
    {
        this.projectIdentity = Objects.requireNonNull (projectIdentity);
        this.nanoTime = Objects.requireNonNull (nanoTime);
        for (int i = 0; i < TRACK_CAPACITY; i++)
            this.entries.add (new Entry (host.createCursorTrack ("PULL_DRUM_CACHE_" + i, "Pull drum cache " + (i + 1), 0, 0, false), i));
        this.slots = this.entries.stream ().flatMap (entry -> entry.tree.slotsForDrums ().stream ()).toList ();
    }

    /** Called during initialization for the model and the private actual-selection cursor. */
    public View follow (final CursorTrack target)
    {
        if (this.views.size () == 2) throw new IllegalStateException ("Both drum cache consumers are registered");
        target.channelId ().markInterested ();
        target.exists ().markInterested ();
        final View view = new View (target);
        this.views.add (view);
        target.channelId ().addValueObserver (ignored -> this.selectionChanged ());
        target.exists ().addValueObserver (ignored -> this.selectionChanged ());
        return view;
    }

    /** Native pad/parameter wrappers must also be installed eagerly for these permanent slots. */
    public List<DrumDeviceTree.Slot> slotsForDrums () { return this.slots; }

    /** Project observers also retire pending work when no drum snapshots are being requested. */
    public void refreshProject ()
    {
        final String current = Objects.requireNonNullElse (this.projectIdentity.get (), "");
        if (this.project.equals (current)) return;
        this.project = current;
        for (final Entry entry : this.entries) entry.clear ();
    }

    private void selectionChanged ()
    {
        for (final Entry entry : this.entries)
        {
            if (entry.state == State.EMPTY || this.inUse (entry.trackId)) continue;
            if (entry.state == State.READY) entry.tree.suspendPendingAcquisitions ();
            else entry.clear ();
        }
    }

    private Entry resolve (final View view)
    {
        this.refreshProject ();
        final String trackId = view.trackId ();
        if (this.project.isBlank () || trackId.isBlank ()) return null;
        Entry entry = this.entries.stream ().filter (candidate -> candidate.trackId.equals (trackId)).findFirst ().orElse (null);
        if (entry == null)
        {
            entry = this.entries.stream ().filter (candidate -> !this.inUse (candidate.trackId))
                .min (Comparator.comparingLong (candidate -> candidate.lastAccess)).orElse (null);
            if (entry == null) return null;
            entry.begin (trackId);
        }
        entry.lastAccess = ++this.access;
        entry.advance (view.target);
        return entry.ready () ? entry : null;
    }

    private boolean inUse (final String id)
    {
        return !id.isBlank () && this.views.stream ().anyMatch (view -> view.trackId ().equals (id));
    }

    public final class View
    {
        private final CursorTrack target;
        private View (final CursorTrack target) { this.target = target; }
        private String trackId () { return this.target.exists ().get () ? Objects.requireNonNullElse (this.target.channelId ().get (), "") : ""; }

        public List<DrumDeviceTree.Slot> machines ()
        {
            final Entry entry = DrumDeviceCache.this.resolve (this);
            return entry == null ? List.of () : entry.tree.machines ();
        }

        public boolean limited ()
        {
            final Entry entry = DrumDeviceCache.this.resolve (this);
            return entry != null && entry.tree.limited ();
        }

        /** Fences even the model's empty-result fallback wrapper against another cached track. */
        public boolean contains (final DrumDeviceTree.Slot slot) { return this.machines ().contains (slot); }
    }

    private final class Entry
    {
        private final CursorTrack track;
        private final DrumDeviceTree tree;
        private String trackId = "";
        private State state = State.EMPTY;
        private long deadline;
        private long lastAccess;

        private Entry (final CursorTrack track, final int index)
        {
            this.track = track;
            track.channelId ().markInterested ();
            track.exists ().markInterested ();
            track.isPinned ().markInterested ();
            track.isPinned ().set (true);
            this.tree = new DrumDeviceTree (track, "DRUM_CACHE_" + index, DrumDeviceCache.this.nanoTime, this::ready);
        }

        private boolean ready ()
        {
            return this.state == State.READY && this.aligned () &&
                DrumDeviceCache.this.project.equals (DrumDeviceCache.this.projectIdentity.get ());
        }

        private boolean aligned ()
        {
            return this.track.exists ().get () && this.track.isPinned ().get () && this.trackId.equals (this.track.channelId ().get ());
        }

        private void begin (final String id)
        {
            this.tree.invalidate ();
            this.trackId = id;
            this.state = State.UNPINNING;
            this.deadline = DrumDeviceCache.this.nanoTime.getAsLong () + ACQUISITION_TIMEOUT_NANOS;
            this.track.isPinned ().set (false);
        }

        private void advance (final CursorTrack source)
        {
            if (this.state == State.READY)
            {
                if (!this.aligned ()) this.begin (this.trackId);
                return;
            }
            if (this.state == State.UNPINNING && !this.track.isPinned ().get ())
            {
                if (!source.exists ().get () || !this.trackId.equals (source.channelId ().get ())) return;
                this.track.selectChannel (source);
                this.track.isPinned ().set (true);
                this.state = State.PINNING;
            }
            else if (this.state == State.PINNING && this.aligned ()) this.state = State.READY;
            else if (DrumDeviceCache.this.nanoTime.getAsLong () - this.deadline >= 0) this.begin (this.trackId);
        }

        private void clear ()
        {
            this.state = State.EMPTY;
            this.trackId = "";
            this.lastAccess = 0;
            this.tree.invalidate ();
            // Also supersede a pending unpin command whose observation has not arrived yet.
            this.track.isPinned ().set (true);
        }
    }
}
