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
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Native command submission, host application and observer delivery are independent steps. */
public final class DrumTreeHostFake
{
    public final List<Item> root = new ArrayList<> ();
    public String trackId = "track-a";
    public int selections;
    public int editorSelections;
    private final List<Runnable> commands = new ArrayList<> ();
    private final List<Runnable> observers = new ArrayList<> ();
    private final Map<Device, Supplier<Item>> targets = new IdentityHashMap<> ();
    private final Map<String, Object> trackProperties = new HashMap<> ();
    public final CursorTrack track = TestProxies.nativeProxy (CursorTrack.class, (method, args) -> switch (method)
    {
        case "channelId" -> this.trackProperties.computeIfAbsent (method, key -> value (StringValue.class, () -> this.trackId, null));
        case "exists" -> this.trackProperties.computeIfAbsent (method, key -> value (BooleanValue.class, () -> !this.trackId.isBlank (), null));
        case "canHoldNoteData" -> this.trackProperties.computeIfAbsent (method, key -> value (SettableBooleanValue.class, () -> true, null));
        case "createDeviceBank" -> bank (() -> this.root);
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
    }

    public void publish () { this.observers.forEach (Runnable::run); }
    public void advance () { this.applyCommands (); this.publish (); }
    public void settle (final DrumDeviceTree tree)
    {
        for (int i = 0; i < 50; i++) { tree.machines (); this.advance (); }
    }

    public static final class Item
    {
        public final String name;
        public final boolean drum;
        public final List<List<Item>> layers = new ArrayList<> ();
        public final List<Item> fx = new ArrayList<> ();
        private Item (final String name, final boolean drum) { this.name = name; this.drum = drum; }
    }

    private DeviceBank bank (final Supplier<List<Item>> items)
    {
        final IntegerValue count = value (IntegerValue.class, () -> items.get ().size (), null);
        return TestProxies.nativeProxy (DeviceBank.class, (method, args) -> switch (method)
        {
            case "itemCount" -> count;
            case "getItemAt" -> device (() -> at (items.get (), (Integer) args[0]), () -> (Integer) args[0]);
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
        final boolean [] pinned = {false};
        final Map<String, Object> properties = new HashMap<> ();
        final PinnableCursorDevice cursor = TestProxies.nativeProxy (PinnableCursorDevice.class, (method, args) -> switch (method)
        {
            case "isPinned" -> properties.computeIfAbsent (method, key -> value (SettableBooleanValue.class, () -> pinned[0], value -> this.commands.add (() -> pinned[0] = (Boolean) value)));
            case "selectDevice" -> {
                final Item requested = this.targets.get ((Device) args[0]).get ();
                this.selections++;
                this.commands.add (() -> selected[0] = requested);
                yield null;
            }
            case "createEqualsValue" -> value (BooleanValue.class, () -> selected[0] != null && selected[0] == this.targets.get ((Device) args[0]).get (), null);
            default -> properties.computeIfAbsent (method, key -> property (() -> selected[0], () -> positionOf (selected[0], this.root), method, args));
        });
        this.targets.put (cursor, () -> selected[0]);
        return cursor;
    }

    private Object property (final Supplier<Item> target, final Supplier<Integer> position, final String method, final Object [] args)
    {
        return switch (method)
        {
            case "exists" -> value (BooleanValue.class, () -> target.get () != null && position.get () >= 0, null);
            case "hasDrumPads" -> value (BooleanValue.class, () -> target.get () != null && target.get ().drum, null);
            case "hasLayers" -> value (BooleanValue.class, () -> target.get () != null && !target.get ().layers.isEmpty (), null);
            case "hasSlots" -> value (BooleanValue.class, () -> target.get () != null && !target.get ().fx.isEmpty (), null);
            case "position" -> value (IntegerValue.class, position::get, null);
            case "name" -> value (StringValue.class, () -> target.get () == null ? "" : target.get ().name, null);
            case "createLayerBank" -> layers (target);
            case "getCursorSlot" -> TestProxies.nativeProxy (DeviceSlot.class, (m, a) -> "createDeviceBank".equals (m) ? bank (() -> target.get () == null ? List.of () : target.get ().fx) : nativeDefault (DeviceSlot.class, m));
            case "selectInEditor" -> { this.editorSelections++; yield null; }
            default -> nativeDefault (Device.class, method);
        };
    }

    private DeviceLayerBank layers (final Supplier<Item> target)
    {
        final IntegerValue count = value (IntegerValue.class, () -> target.get () == null ? 0 : target.get ().layers.size (), null);
        final Map<Integer, DeviceLayer> layers = new HashMap<> ();
        return TestProxies.nativeProxy (DeviceLayerBank.class, (method, args) -> switch (method)
        {
            case "itemCount" -> count;
            case "getItemAt" -> layers.computeIfAbsent ((Integer) args[0], index -> {
                final BooleanValue exists = value (BooleanValue.class, () -> target.get () != null && index < target.get ().layers.size (), null);
                return TestProxies.nativeProxy (DeviceLayer.class, (m, a) -> switch (m)
                {
                    case "exists" -> exists;
                    case "createDeviceBank" -> bank (() -> target.get () == null || index >= target.get ().layers.size () ? List.of () : target.get ().layers.get (index));
                    default -> nativeDefault (DeviceLayer.class, m);
                });
            });
            default -> nativeDefault (DeviceLayerBank.class, method);
        });
    }

    private <T> T value (final Class<T> type, final Supplier<Object> read, final Consumer<Object> write)
    {
        final Object [] observed = {read.get ()};
        final List<Object> callbacks = new ArrayList<> ();
        this.observers.add (() -> {
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
        if (target == null) return -1;
        for (int i = 0; i < items.size (); i++)
        {
            final Item item = items.get (i);
            if (target == item) return i;
            for (final List<Item> layer : item.layers)
            {
                final int found = positionOf (target, layer);
                if (found >= 0) return found;
            }
            final int found = positionOf (target, item.fx);
            if (found >= 0) return found;
        }
        return -1;
    }

    private static Object nativeDefault (final Class<?> type, final String name)
    {
        for (final var method : type.getMethods ())
            if (method.getName ().equals (name)) return TestProxies.relaxedValue (method.getReturnType ());
        throw new AssertionError (type + "." + name);
    }
}
