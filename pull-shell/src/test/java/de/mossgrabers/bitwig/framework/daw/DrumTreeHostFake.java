// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

import com.bitwig.extension.controller.api.*;
import com.bitwig.extension.callback.BooleanValueChangedCallback;
import com.bitwig.extension.callback.IntegerValueChangedCallback;
import com.bitwig.extension.callback.StringValueChangedCallback;
import de.mossgrabers.pull.shell.testing.TestProxies;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Host updates coalesce pin writes; unpinned cursors share selection. Observer delivery is separate. */
public final class DrumTreeHostFake
{
    public List<Item> root = new ArrayList<> ();
    public String trackId = "track-a";
    public boolean initiallyPinned;
    public boolean holdEqualityObservations;
    public boolean holdScrollObservations;
    public boolean holdReferenceLayerObservations;
    public int unpinRequests;
    public int selections;
    public int editorSelections;
    public int equalityValues;
    private final List<Runnable> commands = new ArrayList<> ();
    private final Map<boolean [], Object> pinWrites = new IdentityHashMap<> ();
    public Item cursorSelection;
    public int cursorTargetChanges;
    private final Map<List<Item>, String> layerIds = new IdentityHashMap<> ();
    private final List<Runnable> observers = new ArrayList<> ();
    private final Map<ObjectProxy, Supplier<?>> targets = new IdentityHashMap<> ();
    private final Map<String, Object> trackProperties = new HashMap<> ();
    public final CursorTrack track = TestProxies.nativeProxy (CursorTrack.class, (method, args) -> switch (method)
    {
        case "channelId" -> this.trackProperties.computeIfAbsent (method, key -> value (StringValue.class, () -> this.trackId, null));
        case "exists" -> this.trackProperties.computeIfAbsent (method, key -> value (BooleanValue.class, () -> !this.trackId.isBlank (), null));
        case "canHoldNoteData" -> this.trackProperties.computeIfAbsent (method, key -> value (SettableBooleanValue.class, () -> true, null));
        case "createDeviceBank" -> bank (() -> this.root, (Integer) args[0]);
        case "createCursorDevice" -> cursor ();
        default -> nativeDefault (CursorTrack.class, method);
    });

    public static Item drum (final String name) { return new Item (name, true); }
    public static Item container (final String name, final Item... children)
    {
        final Item item = new Item (name, false);
        for (final Item child : children) item.layers.add (new ArrayList<> (List.of (child)));
        return item;
    }
    public static Item effect (final String name) { return new Item (name, false); }

    public void applyCommands ()
    {
        final List<Runnable> pending = List.copyOf (this.commands);
        this.commands.clear ();
        pending.forEach (Runnable::run);
        this.pinWrites.clear ();
    }

    public void publish () { this.observers.forEach (Runnable::run); }
    public void advance () { this.applyCommands (); this.publish (); }
    public void settle (final DrumDeviceTree tree)
    {
        for (int i = 0; i < 100; i++) { tree.machines (); this.advance (); }
    }

    public static final class Item
    {
        public final String name;
        public final boolean drum;
        public final List<List<Item>> layers = new ArrayList<> ();
        public final List<Item> fx = new ArrayList<> ();
        private Item (final String name, final boolean drum) { this.name = name; this.drum = drum; }
    }

    private DeviceBank bank (final Supplier<List<Item>> items, final int size)
    {
        final IntegerValue count = value (IntegerValue.class, () -> items.get ().size (), null);
        final int [] requested = {0};
        final Supplier<Integer> offset = () -> Math.min (requested[0], Math.max (0, items.get ().size () - size));
        final SettableIntegerValue scroll = value (SettableIntegerValue.class, offset::get, n -> this.commands.add (() -> requested[0] = (Integer) n), () -> !this.holdScrollObservations);
        return TestProxies.nativeProxy (DeviceBank.class, (method, args) -> switch (method)
        {
            case "itemCount" -> count;
            case "getItemAt" -> device (() -> at (items.get (), offset.get () + (Integer) args[0]), () -> offset.get () + (Integer) args[0]);
            case "scrollPosition" -> scroll;
            default -> nativeDefault (DeviceBank.class, method);
        });
    }

    private Device device (final Supplier<Item> target, final Supplier<Integer> position)
    {
        final Map<String, Object> properties = new HashMap<> ();
        final Device device = TestProxies.nativeProxy (Device.class, (method, args) -> properties.computeIfAbsent (method, key -> property (target, position, method, args)));
        this.targets.put (device, target);
        return device;
    }

    private PinnableCursorDevice cursor ()
    {
        final Item [] selected = {null};
        final boolean [] pinned = {this.initiallyPinned};
        final Map<String, Object> properties = new HashMap<> ();
        final Supplier<Item> target = () -> pinned[0] ? selected[0] : this.cursorSelection;
        final Item [] observed = {target.get ()};
        this.observers.add (() -> {
            final Item current = target.get ();
            if (observed[0] != current) this.cursorTargetChanges++;
            observed[0] = current;
        });
        final PinnableCursorDevice cursor = TestProxies.nativeProxy (PinnableCursorDevice.class, (method, args) -> switch (method)
        {
            case "isPinned" -> properties.computeIfAbsent (method, key -> value (SettableBooleanValue.class, () -> pinned[0], value -> {
                if (!(Boolean) value) this.unpinRequests++;
                final Object request = new Object ();
                this.pinWrites.put (pinned, request);
                this.commands.add (() -> {
                    if (this.pinWrites.get (pinned) != request) return;
                    if ((Boolean) value && !pinned[0]) selected[0] = this.cursorSelection;
                    pinned[0] = (Boolean) value;
                });
            }));
            case "selectDevice" -> {
                final Item requested = (Item) this.targets.get ((Device) args[0]).get ();
                this.selections++;
                this.commands.add (() -> { if (!pinned[0]) this.cursorSelection = requested; });
                yield null;
            }
            case "createEqualsValue" -> equality (target, (ObjectProxy) args[0]);
            default -> properties.computeIfAbsent (method, key -> property (target, () -> positionOf (target.get (), this.root), method, args));
        });
        this.targets.put (cursor, target);
        return cursor;
    }

    private Object property (final Supplier<Item> target, final Supplier<Integer> position, final String method, final Object [] args)
    {
        return switch (method)
        {
            case "deviceChain" -> chain (() -> chainOf (target.get (), this.root));
            case "exists" -> value (BooleanValue.class, () -> target.get () != null && position.get () >= 0, null);
            case "hasDrumPads" -> value (BooleanValue.class, () -> target.get () != null && target.get ().drum, null);
            case "hasLayers" -> value (BooleanValue.class, () -> target.get () != null && !target.get ().layers.isEmpty (), null);
            case "hasSlots" -> value (BooleanValue.class, () -> target.get () != null && !target.get ().fx.isEmpty (), null);
            case "position" -> value (IntegerValue.class, position::get, null);
            case "name" -> value (StringValue.class, () -> target.get () == null ? "" : target.get ().name, null);
            case "createLayerBank" -> layers (target, (Integer) args[0]);
            case "getCursorSlot" -> TestProxies.nativeProxy (DeviceSlot.class, (m, a) -> "createDeviceBank".equals (m) ? bank (() -> target.get () == null ? List.of () : target.get ().fx, (Integer) a[0]) : nativeDefault (DeviceSlot.class, m));
            case "selectInEditor" -> { this.editorSelections++; yield null; }
            default -> nativeDefault (Device.class, method);
        };
    }

    private BooleanValue equality (final Supplier<?> target, final ObjectProxy other)
    {
        this.equalityValues++;
        return value (BooleanValue.class, () -> target.get () != null && target.get () == this.targets.get (other).get (),
            null, () -> !this.holdEqualityObservations);
    }

    private DeviceChain chain (final Supplier<List<Item>> items)
    {
        final DeviceChain chain = TestProxies.nativeProxy (DeviceChain.class, (method, args) -> switch (method)
        {
            case "createDeviceBank" -> bank (() -> items.get () == null ? List.of () : items.get (), (Integer) args[0]);
            case "createEqualsValue" -> equality (items, (ObjectProxy) args[0]);
            default -> nativeDefault (DeviceChain.class, method);
        });
        this.targets.put (chain, items);
        return chain;
    }

    private static List<Item> chainOf (final Item target, final List<Item> items)
    {
        if (target == null) return null;
        for (final Item item : items)
        {
            if (item == target) return items;
            for (final List<Item> layer : item.layers)
            {
                final List<Item> found = chainOf (target, layer);
                if (found != null) return found;
            }
            final List<Item> found = chainOf (target, item.fx);
            if (found != null) return found;
        }
        return null;
    }

    private DeviceLayerBank layers (final Supplier<Item> target, final int size)
    {
        final IntegerValue count = value (IntegerValue.class, () -> target.get () == null ? 0 : target.get ().layers.size (), null);
        final Map<Integer, DeviceLayer> layers = new HashMap<> ();
        final int [] requested = {0};
        final Supplier<Integer> offset = () -> Math.min (requested[0], Math.max (0, (target.get () == null ? 0 : target.get ().layers.size ()) - size));
        final SettableIntegerValue scroll = value (SettableIntegerValue.class, offset::get, n -> this.commands.add (() -> requested[0] = (Integer) n), () -> !this.holdScrollObservations);
        return TestProxies.nativeProxy (DeviceLayerBank.class, (method, args) -> switch (method)
        {
            case "itemCount" -> count;
            case "scrollPosition" -> scroll;
            case "getItemAt" -> layers.computeIfAbsent ((Integer) args[0], index -> {
                final BooleanValue exists = value (BooleanValue.class, () -> target.get () != null && offset.get () + index < target.get ().layers.size (), null);
                final StringValue id = value (StringValue.class, () -> target.get () == null || offset.get () + index >= target.get ().layers.size () ? "" :
                    this.layerIds.computeIfAbsent (target.get ().layers.get (offset.get () + index), key -> "layer-" + this.layerIds.size ()), null, () -> size != 1 || !this.holdReferenceLayerObservations);
                return TestProxies.nativeProxy (DeviceLayer.class, (m, a) -> switch (m)
                {
                    case "exists" -> exists;
                    case "channelId" -> id;
                    case "createDeviceBank" -> bank (() -> target.get () == null || offset.get () + index >= target.get ().layers.size () ? List.of () : target.get ().layers.get (offset.get () + index), (Integer) a[0]);
                    default -> nativeDefault (DeviceLayer.class, m);
                });
            });
            default -> nativeDefault (DeviceLayerBank.class, method);
        });
    }

    private <T> T value (final Class<T> type, final Supplier<Object> read, final Consumer<Object> write)
    {
        return value (type, read, write, () -> true);
    }

    private <T> T value (final Class<T> type, final Supplier<Object> read, final Consumer<Object> write, final BooleanSupplier publish)
    {
        final Object [] observed = {read.get ()};
        final List<Object> callbacks = new ArrayList<> ();
        this.observers.add (() -> {
            if (!publish.getAsBoolean ()) return;
            final Object current = read.get ();
            if (Objects.equals (observed[0], current)) return;
            observed[0] = current;
            for (final Object callback : callbacks)
            {
                if (callback instanceof BooleanValueChangedCallback observer) observer.valueChanged ((Boolean) current);
                else if (callback instanceof IntegerValueChangedCallback observer) observer.valueChanged ((Integer) current);
                else if (callback instanceof StringValueChangedCallback observer) observer.valueChanged ((String) current);
            }
        });
        return TestProxies.nativeProxy (type, (method, args) -> switch (method)
        {
            case "get" -> observed[0];
            case "set" -> { if (write != null) write.accept (args[0]); yield null; }
            case "addValueObserver" -> {
                callbacks.add (args[args.length - 1]);
                yield null;
            }
            default -> nativeDefault (type, method);
        });
    }

    private static Item at (final List<Item> items, final int index) { return index < items.size () ? items.get (index) : null; }
    private static int positionOf (final Item target, final List<Item> items)
    {
        final List<Item> chain = chainOf (target, items);
        return chain == null ? -1 : chain.indexOf (target);
    }

    private static Object nativeDefault (final Class<?> type, final String name)
    {
        for (final var method : type.getMethods ())
            if (method.getName ().equals (name)) return TestProxies.relaxedValue (method.getReturnType ());
        throw new AssertionError (type + "." + name);
    }
}
