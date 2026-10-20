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
import com.bitwig.extension.controller.api.ObjectProxy;
import com.bitwig.extension.controller.api.StringValue;
import com.bitwig.extension.controller.api.DeviceLayer;
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

    private enum Acquisition { REFERENCING, UNPINNING, PINNING }

    private final CursorTrack track;
    private final LongSupplier nanoTime;
    private final Chain root;
    private final List<Slot> containers = new ArrayList<> ();
    private final List<Slot> machines = new ArrayList<> ();
    private int nextChainIndex;
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
        this.root = new Chain (track, null, -1, null);
        for (int i = 0; i < MAX_CONTAINERS; i++)
            this.containers.add (new Slot (track, id + "_CONTAINER_" + i, true));
        for (int i = 0; i < MAX_MACHINES; i++)
            this.machines.add (new Slot (track, id + "_DRUM_" + i, false));
        for (final Slot slot : this.slots ())
        {
            slot.equalRoot = this.observeEquality (slot.chain, this.root.identity);
            for (final Slot parent : this.containers)
            {
                slot.equalParents.add (this.observeEquality (slot.referenceParent, parent.device));
                slot.equalFxChains.add (this.observeEquality (slot.chain, parent.fx.identity));
            }
        }
        track.channelId ().markInterested ();
        track.exists ().markInterested ();
        track.channelId ().addValueObserver (ignored -> this.dirty = true);
        track.exists ().addValueObserver (ignored -> this.dirty = true);
    }

    private BooleanValue observeEquality (final ObjectProxy left, final ObjectProxy right)
    {
        final BooleanValue equal = left.createEqualsValue (right);
        equal.markInterested ();
        equal.addValueObserver (ignored -> this.dirty = true);
        return equal;
    }

    /** The eight permanent device proxies; availability is separately fenced by each slot. */
    public List<Slot> slotsForDrums () { return List.copyOf (this.machines); }

    /** Current distinct machines in tree order. Unacknowledged bindings are never exposed. */
    public List<Slot> machines () { this.refresh (); this.recordDiagnostics (); return this.result; }

    /** A structural window or the eight-machine capacity was exceeded. */
    public boolean limited () { this.refresh (); return this.limited; }

    /** No native acquisition or source revision remains pending in the current bounded search. */
    public boolean settled () { this.refresh (); return !this.dirty; }

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
            if (used.stream ().anyMatch (slot -> slot.ready && slot.sameTarget (slot.source) && slot.sameTarget (node))) continue;
            final List<Slot> pool = drum ? this.machines : this.containers;
            // Bank positions can shift while a retained cursor still addresses the same device.
            Slot selected = pool.stream ().filter (slot -> !used.contains (slot) && slot.pointsTo (node)).findFirst ().orElse (null);
            if (selected == null)
                selected = pool.stream ().filter (slot -> !used.contains (slot) && slot.matchesLocationHint (node)).findFirst ().orElse (null);
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
            if (!selected.bind (node))
            {
                // Moving a witness window hides the subtree, but does not retire surviving targets.
                if (!drum) this.retainPendingDescendants (selected, used);
                continue;
            }
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

    private void retainPendingDescendants (final Slot parent, final List<Slot> used)
    {
        for (final Slot slot : this.slots ())
        {
            if (used.contains (slot) || slot.source == null || slot.source.chain.parent != parent ||
                slot.sourceParentAssignment != parent.assignment) continue;
            used.add (slot);
            slot.ready = false;
            this.retainPendingDescendants (slot, used);
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
            SelectionDebug.record ("DRUM_TARGET", this.diagnosticId + " assignment=" + slot.assignment + " chain=" + node.chain.index + " node=" + node.index +
                " revision=" + slot.sourceRevision + "/" + node.revision + " acquisition=" + slot.acquisition + " ready=" + slot.ready +
                " exists=" + slot.device.exists ().get () + " pinned=" + slot.device.isPinned ().get () +
                " equal=" + slot.sameTarget (node) + " position=" + slot.device.position ().get () + "/" + node.device.position ().get () +
                " drum=" + slot.device.hasDrumPads ().get () + "/" + node.device.hasDrumPads ().get () +
                " layers=" + (slot.layers == null ? 0 : slot.layers.itemCount ().get ()) +
                " fx=" + (slot.fx == null ? 0 : slot.fx.bank.itemCount ().get ()));
        }
    }

    private final class Chain
    {
        private final DeviceBank bank;
        private final DeviceChain identity;
        private final int index;
        private final List<Node> children = new ArrayList<> ();

        private final Slot parent;
        private final int layerIndex;
        private final StringValue layerId;

        private Chain (final DeviceChain chain, final Slot parent, final int layerIndex, final StringValue layerId)
        {
            this.parent = parent;
            this.layerIndex = layerIndex;
            this.layerId = layerId;
            this.index = DrumDeviceTree.this.nextChainIndex++;
            this.bank = chain.createDeviceBank (DEVICES_PER_CHAIN);
            this.identity = this.bank.getItemAt (0).deviceChain ();
            this.bank.itemCount ().markInterested ();
            this.bank.itemCount ().addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);
            for (int i = 0; i < DEVICES_PER_CHAIN; i++)
                this.children.add (new Node (this, i, this.bank.getItemAt (i)));
        }
    }

    private final class Node
    {
        private final Device device;
        private final Chain chain;
        private final int index;
        private long revision;

        private Node (final Chain chain, final int index, final Device device)
        {
            this.device = device;
            this.chain = chain;
            this.index = index;
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
        private final DeviceChain chain;
        private final DeviceBank sibling;
        private final BooleanValue equalSibling;
        private final PinnableCursorDevice referenceParent;
        private final DeviceLayerBank referenceLayers;
        private final DeviceLayer referenceLayer;
        private final BooleanValue equalLayerChain;
        private final List<BooleanValue> equalParents = new ArrayList<> ();
        private final List<BooleanValue> equalFxChains = new ArrayList<> ();
        private BooleanValue equalRoot;
        private Slot requestedParent;
        private long requestedParentAssignment;
        private Acquisition parentAcquisition;
        private long parentDeadline;
        private String layerHint = "";
        private Slot layerParentHint;
        private Node source;
        private long sourceRevision;
        private long sourceParentAssignment;
        private long assignment;
        private long acquisitionDeadline;
        private boolean ready;
        private Acquisition acquisition;

        private Slot (final CursorTrack track, final String id, final boolean container)
        {
            this.device = this.createParkedCursor (track, id);
            this.chain = this.device.deviceChain ();
            // One movable sibling proves the local device, rather than sixteen permanent pairs.
            this.sibling = this.chain.createDeviceBank (1);
            this.observeScroll (this.sibling.scrollPosition ());
            this.equalSibling = DrumDeviceTree.this.observeEquality (this.device, this.sibling.getItemAt (0));
            this.device.hasDrumPads ().markInterested ();
            this.device.position ().markInterested ();
            this.device.hasDrumPads ().addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);
            this.device.position ().addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);

            // A layer UUID locates the source chain beneath an acknowledged parent cursor.
            // Root/FX chains keep direct comparisons; no handle-by-all-73-chains matrix remains.
            this.referenceParent = this.createParkedCursor (track, id + "_SOURCE_PARENT");
            this.referenceLayers = this.referenceParent.createLayerBank (1);
            this.observeScroll (this.referenceLayers.scrollPosition ());
            this.referenceLayer = this.referenceLayers.getItemAt (0);
            this.referenceLayer.channelId ().markInterested ();
            this.referenceLayer.channelId ().addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);
            final DeviceChain referenceChain = this.referenceLayer.createDeviceBank (1).getItemAt (0).deviceChain ();
            this.equalLayerChain = DrumDeviceTree.this.observeEquality (this.chain, referenceChain);

            this.layers = container ? this.device.createLayerBank (LAYERS_PER_CONTAINER) : null;
            this.fx = container ? new Chain (this.device.getCursorSlot (), this, -1, null) : null;
            if (this.layers != null)
            {
                this.layers.itemCount ().markInterested ();
                this.layers.itemCount ().addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);
                for (int i = 0; i < LAYERS_PER_CONTAINER; i++)
                {
                    final var layer = this.layers.getItemAt (i);
                    layer.exists ().markInterested ();
                    layer.exists ().addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);
                    final Chain child = new Chain (layer, this, i, layer.channelId ());
                    this.children.add (child);
                    layer.channelId ().markInterested ();
                    layer.channelId ().addValueObserver (ignored -> child.children.forEach (Node::changed));
                }
            }
        }

        private PinnableCursorDevice createParkedCursor (final CursorTrack track, final String id)
        {
            final PinnableCursorDevice cursor = track.createCursorDevice (id, id, 0, CursorDeviceFollowMode.FOLLOW_SELECTION);
            cursor.isPinned ().set (true);
            cursor.exists ().markInterested ();
            cursor.isPinned ().markInterested ();
            cursor.exists ().addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);
            cursor.isPinned ().addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);
            return cursor;
        }

        private void observeScroll (final com.bitwig.extension.controller.api.SettableIntegerValue position)
        {
            position.markInterested ();
            position.addValueObserver (ignored -> DrumDeviceTree.this.dirty = true);
        }

        public Device device () { return this.device; }
        public boolean available ()
        {
            DrumDeviceTree.this.refresh ();
            return this.ready && this.source != null && this.sourceRevision == this.source.revision &&
                this.device.exists ().get () && this.device.isPinned ().get () && this.sameTarget (this.source);
        }
        public String identity () { return this.available () ? DrumDeviceTree.this.trackId + "/drum/" + this.assignment : ""; }

        private boolean bind (final Node node)
        {
            if (this.source != node || this.sourceRevision != node.revision)
            {
                if (this.source == null) this.assignment = 0;
                this.source = node;
                this.sourceRevision = node.revision;
                this.sourceParentAssignment = node.chain.parent == null ? 0 : node.chain.parent.assignment;
                this.acquisition = Acquisition.REFERENCING;
                this.acquisitionDeadline = DrumDeviceTree.this.nanoTime.getAsLong () + ACQUISITION_TIMEOUT_NANOS;
                this.ready = false;
            }
            if (!this.prepareReference (node))
            {
                this.ready = false;
                DrumDeviceTree.this.dirty = true;
                return false;
            }
            if (this.pointsTo (node))
            {
                if (this.assignment == 0) this.assignment = ++DrumDeviceTree.this.generation;
                this.acquisition = Acquisition.PINNING;
                this.ready = true;
                this.layerHint = node.chain.layerId == null ? "" : node.chain.layerId.get ();
                this.layerParentHint = node.chain.parent;
                return true;
            }
            if (this.acquisition == Acquisition.REFERENCING)
            {
                this.acquisition = Acquisition.UNPINNING;
                this.device.isPinned ().set (false);
            }
            // Unpin and pin writes can coalesce. Observe unpinning before select-and-pin.
            else if (this.acquisition == Acquisition.UNPINNING && !this.device.isPinned ().get ())
            {
                this.device.selectDevice (node.device);
                this.layerHint = "";
                this.device.isPinned ().set (true);
                this.assignment = ++DrumDeviceTree.this.generation;
                this.acquisition = Acquisition.PINNING;
            }
            else if (this.ready || DrumDeviceTree.this.nanoTime.getAsLong () - this.acquisitionDeadline >= 0)
                this.sourceRevision = -1;
            this.ready = false;
            DrumDeviceTree.this.dirty = true;
            return false;
        }

        private boolean prepareReference (final Node node)
        {
            if (this.sibling.scrollPosition ().get () != node.index)
            {
                this.sibling.scrollPosition ().set (node.index);
                // An empty or unrelated cursor chain cannot reach the source index until selected.
                if (this.matchesLocationHint (node)) return false;
            }
            if (node.chain.layerId == null) return true;
            final Slot parent = node.chain.parent;
            final long now = DrumDeviceTree.this.nanoTime.getAsLong ();
            if (this.requestedParent != parent || this.requestedParentAssignment != parent.assignment)
            {
                this.requestedParent = parent;
                this.requestedParentAssignment = parent.assignment;
                this.parentAcquisition = Acquisition.UNPINNING;
                this.parentDeadline = now + ACQUISITION_TIMEOUT_NANOS;
                this.referenceParent.isPinned ().set (false);
                return false;
            }
            if (this.parentAcquisition == Acquisition.UNPINNING && !this.referenceParent.isPinned ().get ())
            {
                this.referenceParent.selectDevice (parent.device);
                this.referenceParent.isPinned ().set (true);
                this.parentAcquisition = Acquisition.PINNING;
                return false;
            }
            if (!this.referenceParent.isPinned ().get () || !this.equalParents.get (DrumDeviceTree.this.containers.indexOf (parent)).get ())
            {
                if (now - this.parentDeadline >= 0) this.requestedParent = null;
                return false;
            }
            if (this.referenceLayers.scrollPosition ().get () != node.chain.layerIndex)
            {
                this.referenceLayers.scrollPosition ().set (node.chain.layerIndex);
                return false;
            }
            final boolean aligned = sameLayerId (node.chain.layerId.get (), this.referenceLayer.channelId ().get ());
            if (!aligned && now - this.parentDeadline >= 0) this.requestedParent = null;
            return aligned;
        }

        private boolean pointsTo (final Node node)
        {
            return this.device.exists ().get () && this.device.isPinned ().get () && this.sameTarget (node) &&
                this.device.position ().get () == node.device.position ().get () &&
                this.device.hasDrumPads ().get () == node.device.hasDrumPads ().get ();
        }

        private boolean matchesLocationHint (final Node node)
        {
            // A location only chooses a candidate after bank shifts; native equality still gates it.
            return this.device.exists ().get () && this.device.isPinned ().get () && this.device.position ().get () == node.device.position ().get () &&
                (this.sameChain (node) || node.chain.layerId != null && this.layerParentHint == node.chain.parent && sameLayerId (this.layerHint, node.chain.layerId.get ()));
        }

        private boolean sameTarget (final Node node)
        {
            return this.sibling.scrollPosition ().get () == node.index && this.equalSibling.get () && this.sameChain (node);
        }

        private boolean sameChain (final Node node)
        {
            if (node.chain.parent == null) return this.equalRoot.get ();
            final int parent = DrumDeviceTree.this.containers.indexOf (node.chain.parent);
            if (node.chain.layerId == null) return this.equalFxChains.get (parent).get ();
            // A changed window UUID cannot re-label the retained device using an older equality.
            if (!this.layerHint.isBlank () && !sameLayerId (this.layerHint, node.chain.layerId.get ())) return false;
            return this.referenceParent.isPinned ().get () && this.equalParents.get (parent).get () &&
                sameLayerId (node.chain.layerId.get (), this.referenceLayer.channelId ().get ()) && this.equalLayerChain.get ();
        }

        private static boolean sameLayerId (final String left, final String right)
        {
            return left != null && !left.isBlank () && left.equals (right);
        }

        private void release ()
        {
            if (!this.device.isPinned ().get ()) this.device.isPinned ().set (true);
            if (!this.referenceParent.isPinned ().get ()) this.referenceParent.isPinned ().set (true);
            if (this.source != null) DrumDeviceTree.this.dirty = true;
            this.source = null;
            this.ready = false;
            this.requestedParent = null;
            this.layerHint = "";
        }
    }
}
