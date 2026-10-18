// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.CursorDeviceFollowMode;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.DeviceChain;
import com.bitwig.extension.controller.api.DeviceLayerBank;
import com.bitwig.extension.controller.api.PinnableCursorDevice;
import de.mossgrabers.pull.shell.SelectionDebug;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * Initialization-owned access to a track's layer tree. Each chain is visited through its parent,
 * in device/layer order. Drum devices are leaves: their internal pad instruments are not another kit.
 * Eight retained drum cursors and eight container cursors bound resource use independently of the
 * project. Each chain exposes sixteen devices; each container exposes eight layers. Each container also exposes its selected FX slot, preserving the existing slot access. API 25
 * does not provide a bank of all FX slots; unselected slots are outside this bounded tree.
 * No editor selection is changed. Native equality acknowledges the retained device assignment;
 * it is not an atomic acknowledgement of that device's child windows. Source changes revoke it.
 */
public final class DrumDeviceTree
{
    public static final int MAX_MACHINES = 8;
    public static final int MAX_CONTAINERS = 8;
    public static final int DEVICES_PER_CHAIN = 16;
    public static final int LAYERS_PER_CONTAINER = 8;

    private static final long ACQUISITION_TIMEOUT_NANOS = 2_000_000_000L;

    private enum Acquisition { UNPINNING, PINNING }

    private final CursorTrack track;
    private final LongSupplier nanoTime;
    private final Chain root;
    private final List<Slot> containers = new ArrayList<> ();
    private final List<Slot> machines = new ArrayList<> ();
    private final List<Node> nodes = new ArrayList<> ();
    private List<Slot> result = List.of ();
    private String trackId = "";
    private long generation;
    private boolean dirty = true;
    private boolean refreshing;
    private boolean limited;
    private long lastDiagnostic;
    private final String diagnosticId;

    public DrumDeviceTree (final CursorTrack track, final String id)
    {
        this (track, id, System::nanoTime);
    }

    DrumDeviceTree (final CursorTrack track, final String id, final LongSupplier nanoTime)
    {
        this.track = track;
        this.nanoTime = nanoTime;
        this.diagnosticId = id;
        this.root = new Chain (track);
        for (int i = 0; i < MAX_CONTAINERS; i++)
            this.containers.add (new Slot (track, id + "_CONTAINER_" + i, true));
        for (int i = 0; i < MAX_MACHINES; i++)
            this.machines.add (new Slot (track, id + "_DRUM_" + i, false));
        // Equality objects, like all Bitwig proxies, must exist before initialization ends.
        for (final Slot slot : this.slots ())
            for (final Node node : this.nodes)
            {
                final BooleanValue equal = slot.device.createEqualsValue (node.device);
                equal.markInterested ();
                equal.addValueObserver (ignored -> this.dirty = true);
                slot.equal.add (equal);
            }
        track.channelId ().markInterested ();
        track.exists ().markInterested ();
        track.channelId ().addValueObserver (ignored -> this.dirty = true);
        track.exists ().addValueObserver (ignored -> this.dirty = true);
    }

    /** The eight permanent device proxies; availability is separately fenced by each slot. */
    public List<Slot> slotsForDrums () { return List.copyOf (this.machines); }

    /** Current distinct machines in tree order. Unacknowledged bindings are never exposed. */
    public List<Slot> machines () { this.refresh (); this.recordDiagnostics (); return this.result; }

    /** A structural window or the eight-machine capacity was exceeded. */
    public boolean limited () { this.refresh (); return this.limited; }

    private List<Slot> slots ()
    {
        final List<Slot> slots = new ArrayList<> (this.containers);
        slots.addAll (this.machines);
        return slots;
    }

    private void refresh ()
    {
        if (this.refreshing || !this.dirty) return;
        this.refreshing = true;
        try
        {
            this.dirty = false;
            final String current = this.track.exists ().get () ? this.track.channelId ().get () : "";
            if (!this.trackId.equals (current == null ? "" : current))
            {
                this.trackId = current == null ? "" : current;
                for (final Slot slot : this.slots ()) slot.release ();
            }
            final List<Slot> found = new ArrayList<> ();
            final List<Slot> used = new ArrayList<> ();
            this.limited = false;
            if (!this.trackId.isBlank ()) this.visit (this.root, found, used);
            for (final Slot slot : this.slots ())
                if (!used.contains (slot)) slot.release ();
            this.result = List.copyOf (found);
        }
        finally { this.refreshing = false; }
    }

    private void visit (final Chain chain, final List<Slot> found, final List<Slot> used)
    {
        if (chain.bank.itemCount ().get () > DEVICES_PER_CHAIN) this.limited = true;
        for (final Node node : chain.children)
        {
            if (!node.device.exists ().get ()) continue;
            final boolean drum = node.device.hasDrumPads ().get ();
            if (!drum && !node.device.hasLayers ().get () && !node.device.hasSlots ().get ()) continue;
            // Bitwig's selected slot can alias a layer already visited through its layer bank.
            // Compare acknowledged native targets, never names or Java proxy identity.
            if (used.stream ().anyMatch (slot -> slot.ready && slot.equal.get (slot.source.index).get () && slot.equal.get (node.index).get ())) continue;
            final List<Slot> pool = drum ? this.machines : this.containers;
            // Bank positions can shift while a retained cursor still addresses the same device.
            Slot selected = pool.stream ().filter (slot -> !used.contains (slot) && slot.pointsTo (node)).findFirst ().orElse (null);
            if (selected == null)
                selected = pool.stream ().filter (slot -> slot.source == node && !used.contains (slot)).findFirst ().orElse (null);
            if (selected == null)
                selected = pool.stream ().filter (slot -> !used.contains (slot)).findFirst ().orElse (null);
            if (selected == null)
            {
                // Pending targets cannot yet tell us whether this is another alias or overflow.
                if (pool.stream ().allMatch (slot -> slot.ready)) this.limited = true;
                continue;
            }
            used.add (selected);
            if (!selected.bind (node)) continue;
            if (drum) found.add (selected);
            else
            {
                if (node.device.hasLayers ().get () && selected.layers.itemCount ().get () > LAYERS_PER_CONTAINER) this.limited = true;
                for (int i = 0; i < selected.children.size (); i++)
                    if (node.device.hasLayers ().get () && selected.layers.getItemAt (i).exists ().get ())
                        this.visit (selected.children.get (i), found, used);
                if (node.device.hasSlots ().get ()) this.visit (selected.fx, found, used);
            }
        }
    }

    /** Existing subscribed values only; bounded selection diagnostics are off by default. */
    private void recordDiagnostics ()
    {
        if (!SelectionDebug.recording ()) return;
        final long now = System.nanoTime ();
        if (now - this.lastDiagnostic < 250_000_000L) return;
        this.lastDiagnostic = now;
        SelectionDebug.record ("DRUM_TREE", this.diagnosticId + " track=" + this.trackId + " root=" + this.root.bank.itemCount ().get () +
            " found=" + this.result.size () + " limited=" + this.limited);
        for (final Slot slot : this.slots ())
        {
            final Node node = slot.source;
            if (node == null) continue;
            SelectionDebug.record ("DRUM_TARGET", this.diagnosticId + " assignment=" + slot.assignment + " node=" + node.index +
                " revision=" + slot.sourceRevision + "/" + node.revision + " acquisition=" + slot.acquisition + " ready=" + slot.ready +
                " exists=" + slot.device.exists ().get () + " pinned=" + slot.device.isPinned ().get () +
                " equal=" + slot.equal.get (node.index).get () + " position=" + slot.device.position ().get () + "/" + node.device.position ().get () +
                " drum=" + slot.device.hasDrumPads ().get () + "/" + node.device.hasDrumPads ().get () +
                " layers=" + (slot.layers == null ? 0 : slot.layers.itemCount ().get ()) +
                " fx=" + (slot.fx == null ? 0 : slot.fx.bank.itemCount ().get ()));
        }
    }

    private final class Chain
    {
        private final DeviceBank bank;
        private final List<Node> children = new ArrayList<> ();

        private Chain (final DeviceChain chain)
        {
            this.bank = chain.createDeviceBank (DEVICES_PER_CHAIN);
            this.bank.itemCount ().markInterested ();
            this.bank.itemCount ().addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);
            for (int i = 0; i < DEVICES_PER_CHAIN; i++)
                this.children.add (new Node (this.bank.getItemAt (i)));
        }
    }

    private final class Node
    {
        private final Device device;
        private final int index;
        private long revision;

        private Node (final Device device)
        {
            this.device = device;
            this.index = DrumDeviceTree.this.nodes.size ();
            DrumDeviceTree.this.nodes.add (this);
            device.exists ().markInterested ();
            device.hasDrumPads ().markInterested ();
            device.hasLayers ().markInterested ();
            device.hasSlots ().markInterested ();
            device.position ().markInterested ();
            device.exists ().addValueObserver (ignored -> this.changed ());
            device.hasDrumPads ().addValueObserver (ignored -> this.changed ());
            device.hasLayers ().addValueObserver (ignored -> this.changed ());
            device.hasSlots ().addValueObserver (ignored -> this.changed ());
            device.position ().addValueObserver (ignored -> this.changed ());
        }

        private void changed () { this.revision++; DrumDeviceTree.this.dirty = true; }
    }

    /** An opaque retained device assignment, never an identity inferred from its display name. */
    public final class Slot
    {
        private final PinnableCursorDevice device;
        private final DeviceLayerBank layers;
        private final Chain fx;
        private final List<Chain> children = new ArrayList<> ();
        private final List<BooleanValue> equal = new ArrayList<> ();
        private Node source;
        private long sourceRevision;
        private long assignment;
        private long acquisitionDeadline;
        private boolean ready;
        private Acquisition acquisition;

        private Slot (final CursorTrack track, final String id, final boolean container)
        {
            this.device = track.createCursorDevice (id, id, 0, CursorDeviceFollowMode.FOLLOW_SELECTION);
            this.device.exists ().markInterested ();
            this.device.isPinned ().markInterested ();
            this.device.hasDrumPads ().markInterested ();
            this.device.position ().markInterested ();
            this.device.exists ().addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);
            this.device.isPinned ().addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);
            this.device.hasDrumPads ().addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);
            this.device.position ().addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);
            this.layers = container ? this.device.createLayerBank (LAYERS_PER_CONTAINER) : null;
            this.fx = container ? new Chain (this.device.getCursorSlot ()) : null;
            if (this.layers != null)
            {
                this.layers.itemCount ().markInterested ();
                this.layers.itemCount ().addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);
                for (int i = 0; i < LAYERS_PER_CONTAINER; i++)
                {
                    final var layer = this.layers.getItemAt (i);
                    layer.exists ().markInterested ();
                    layer.exists ().addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);
                    final Chain child = new Chain (layer);
                    this.children.add (child);
                    layer.channelId ().markInterested ();
                    layer.channelId ().addValueObserver (ignored -> child.children.forEach (Node::changed));
                }
            }
        }

        public Device device () { return this.device; }
        public boolean available ()
        {
            DrumDeviceTree.this.refresh ();
            return this.ready && this.source != null && this.sourceRevision == this.source.revision &&
                this.device.exists ().get () && this.device.isPinned ().get () && this.equal.get (this.source.index).get ();
        }
        public String identity () { return this.available () ? DrumDeviceTree.this.trackId + "/drum/" + this.assignment : ""; }

        private boolean bind (final Node node)
        {
            if (this.source != node || this.sourceRevision != node.revision)
            {
                if (this.pointsTo (node))
                {
                    if (!this.ready) this.assignment = ++DrumDeviceTree.this.generation;
                    this.source = node;
                    this.sourceRevision = node.revision;
                    this.acquisition = Acquisition.PINNING;
                    this.ready = true;
                    return true;
                }
                this.release ();
                this.source = node;
                this.sourceRevision = node.revision;
                this.assignment = ++DrumDeviceTree.this.generation;
                this.acquisition = Acquisition.UNPINNING;
                this.acquisitionDeadline = DrumDeviceTree.this.nanoTime.getAsLong () + ACQUISITION_TIMEOUT_NANOS;
                this.device.isPinned ().set (false);
                DrumDeviceTree.this.dirty = true;
                return false;
            }
            // Unpin and pin writes may coalesce within one host update. Observe unpinning
            // first, then select-and-pin together: unpinned cursors share native selection.
            if (this.acquisition == Acquisition.UNPINNING && !this.device.isPinned ().get ())
            {
                this.device.selectDevice (node.device);
                this.device.isPinned ().set (true);
                this.acquisition = Acquisition.PINNING;
                DrumDeviceTree.this.dirty = true;
                return false;
            }
            final boolean coherent = this.acquisition == Acquisition.PINNING && this.pointsTo (node);
            if (!coherent && (this.ready || DrumDeviceTree.this.nanoTime.getAsLong () - this.acquisitionDeadline >= 0))
            {
                // Same-shaped replacement can strand even an assignment never acknowledged.
                // Expiry only retries acquisition; it never substitutes for native equality.
                this.sourceRevision = -1;
                this.assignment = ++DrumDeviceTree.this.generation;
            }
            this.ready = coherent;
            if (!this.ready) DrumDeviceTree.this.dirty = true;
            return this.ready;
        }

        private boolean pointsTo (final Node node)
        {
            return this.device.exists ().get () && this.device.isPinned ().get () && this.equal.get (node.index).get () &&
                this.device.position ().get () == node.device.position ().get () &&
                this.device.hasDrumPads ().get () == node.device.hasDrumPads ().get ();
        }

        private void release ()
        {
            if (this.source != null) DrumDeviceTree.this.dirty = true;
            this.source = null;
            this.ready = false;
        }
    }
}
