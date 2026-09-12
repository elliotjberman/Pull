// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.callback.BooleanValueChangedCallback;
import com.bitwig.extension.callback.IntegerValueChangedCallback;
import com.bitwig.extension.callback.ObjectValueChangedCallback;
import com.bitwig.extension.controller.api.*;
import de.mossgrabers.bitwig.framework.daw.data.bank.ParameterBankImpl;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.pull.shell.runtime.RetainedCursorPool.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static de.mossgrabers.pull.shell.testing.TestProxies.relaxedProxy;

/**
 * Native proxy observations, submitted effects, applied state and read-back advance independently.
 * Cross-track pinning and edits invisible to every installed observer still need real-host validation.
 */
class BitwigRetainedDevicePagesTest
{
    @Test
    void creationTrackAndSubmittedSelectionCannotProveDeviceOrPageReadiness ()
    {
        final Fixture fixture = new Fixture (3);
        fixture.request ();
        fixture.tick ();
        assertNull (fixture.host.devicePage ());
        assertTrue (fixture.commands.isEmpty ());
        fixture.deliverTracks ();
        fixture.tick ();
        fixture.tick ();
        assertEquals (List.of ("device:0:device-a"), fixture.commands);
        assertEquals ("creation-track", fixture.assignment.trackId (), "channel() supplies creation context, not the pinned device owner");
        assertEquals ("other-track", fixture.source.observed.trackId);
        fixture.tick ();
        assertNull (fixture.host.devicePage (), "selectDevice and pin requests have no synchronous read-back");
        fixture.children[0].deliverDevice (new DeviceState ("creation-track", "wrong-device", 3));
        fixture.tick ();
        assertEquals (1, fixture.commands.size (), "matching creation track cannot authorize page assignment");
        fixture.children[0].deliverDevice (fixture.source.observed);
        fixture.tick ();
        assertEquals (List.of ("device:0:device-a", "page:0:1"), fixture.commands);
        fixture.tick ();
        assertNull (fixture.host.devicePage (), "device equality cannot acknowledge page or parameter delivery");
        fixture.children[0].page.deliverIdentity (fixture.source.observed, 1);
        fixture.tick ();
        fixture.tick ();
        assertNull (fixture.host.devicePage (), "matching device and page with stale parameter properties remains pending");
        fixture.children[0].page.deliverProperties ();
        fixture.tick ();
        assertNull (fixture.host.devicePage ());
        fixture.tick ();
        assertNotNull (fixture.host.devicePage ());
        fixture.request ();
        fixture.tick ();
        assertEquals (2, fixture.commands.size (), "complete replay cannot repin the existing device");
    }

    @Test
    void partialAndEmptyPagesIgnoreStalePropertiesOfNonexistentRemotes ()
    {
        for (final int present: List.of (0, 3))
        {
            final Fixture fixture = new Fixture (present);
            final var page = fixture.ready ();
            assertNotNull (page, "unmapped remote slots must not block a coherent page");
            assertEquals (present, page.parameters ().stream ().filter (parameter -> parameter.doesExist ()).count ());
        }
    }

    @Test
    void sourcePageAndDeviceNavigationLeaveOldCleanupOnItsRetainedParameter ()
    {
        final Fixture fixture = new Fixture (3);
        final var original = fixture.ready ();
        final var parameter = original.parameters ().getFirst ();
        final int before = parameter.getValue ();
        parameter.setNormalizedValue (0.7);
        assertEquals (before, parameter.getValue (), "write submission cannot supply read-back");
        assertEquals (List.of ("device-a/page-1/0:set:0.7007874015748031"), fixture.effects);
        fixture.applyEffects ();
        assertEquals (before, parameter.getValue (), "applied host values still require subscribed delivery");
        fixture.source.page.deliverProperties ();
        assertTrue (original.current ().getAsBoolean (), "temporary property propagation lag cannot revoke a ready target");
        assertSame (original, fixture.host.devicePage ());
        fixture.deliverProperties ();
        assertEquals (89, parameter.getValue ());
        assertTrue (original.current ().getAsBoolean (), "ordinary value delivery cannot change parameter identity");
        assertSame (original, fixture.host.devicePage ());

        fixture.navigate (fixture.source.observed, 0);
        assertFalse (original.current ().getAsBoolean ());
        assertTrue (original.addressable ().getAsBoolean ());
        parameter.touchValue (false);
        assertEquals ("device-a/page-1/0:touch:false", fixture.effects.getLast ());
        fixture.request (original.owner ());
        fixture.deliverTracks ();
        fixture.tick ();
        fixture.tick ();
        fixture.deliverChild (1);
        final var next = fixture.host.devicePage ();
        assertNotNull (next);
        assertEquals (0, next.page ());
        assertNotEquals (original.owner (), next.owner ());

        fixture.navigate (new DeviceState ("third-track", "device-b", 3), 0);
        assertFalse (next.current ().getAsBoolean ());
        assertTrue (next.addressable ().getAsBoolean ());
        assertTrue (original.addressable ().getAsBoolean ());
        next.parameters ().getFirst ().touchValue (false);
        assertEquals ("device-a/page-0/0:touch:false", fixture.effects.getLast ());
        fixture.request (next.owner ());
        assertFalse (original.addressable ().getAsBoolean (), "retirement, rather than source navigation, frees the old page");
        fixture.deliverTracks ();
        fixture.tick ();
        fixture.tick ();
        fixture.deliverChild (0);
        assertNotNull (fixture.host.devicePage ());
        assertTrue (next.addressable ().getAsBoolean ());
        fixture.host.devicePage ().parameters ().getFirst ().touchValue (true);
        assertEquals ("device-b/page-0/0:touch:true", fixture.effects.getLast ());
    }

    @Test
    void observedTargetInvalidationsCannotBeUndoneToReviveCleanup ()
    {
        final List<Consumer<Child>> invalidations = List.of (
            child -> { child.exists.deliver (false); child.exists.deliver (true); },
            child -> { child.pinned.deliver (false); child.pinned.deliver (true); },
            child -> { child.page.index.deliver (0); child.page.index.deliver (1); },
            child -> child.page.names.deliver (new String[] {"Reordered", "Pages"}),
            child -> { child.page.remotes[0].mapped.deliver (true); child.page.remotes[0].mapped.deliver (false); },
            child -> { child.page.remotes[0].exists.deliver (false); child.page.remotes[0].exists.deliver (true); },
            child -> { child.page.remotes[0].name.deliver ("Replacement"); child.page.remotes[0].name.deliver ("Remote 0"); });
        for (final Consumer<Child> invalidate: invalidations)
        {
            final Fixture fixture = new Fixture (3);
            final var original = fixture.ready ();
            invalidate.accept (fixture.children[0]);
            assertFalse (original.current ().getAsBoolean ());
            assertFalse (original.addressable ().getAsBoolean (), "later identical-looking metadata cannot erase an observed invalidation");
            fixture.request (original.owner ());
            assertNull (fixture.host.devicePage ());
        }
    }

    @Test
    void sourceRoundTripRevokesEditingButOrdinaryValueDeliveryKeepsCleanup ()
    {
        for (final String change: List.of ("device", "device-exists", "page", "remote-exists", "name", "mapping", "topology"))
        {
            final Fixture fixture = new Fixture (3);
            final var original = fixture.ready ();
            final DeviceState source = fixture.source.observed;
            final RemoteNode remote = fixture.source.page.remotes[0];
            switch (change)
            {
                case "device" -> { fixture.navigate (new DeviceState ("third-track", "device-b", 3), 1); fixture.navigate (source, 1); }
                case "device-exists" -> { fixture.source.exists.deliver (false); fixture.source.exists.deliver (true); }
                case "page" -> { fixture.navigate (source, 0); fixture.navigate (source, 1); }
                case "remote-exists" -> { remote.exists.deliver (false); remote.exists.deliver (true); }
                case "name" -> { remote.name.deliver ("Replacement"); remote.name.deliver ("Remote 0"); }
                case "mapping" -> { remote.mapped.deliver (true); remote.mapped.deliver (false); }
                case "topology" -> fixture.source.page.names.deliver (new String[] {"Reordered", "Pages"});
                default -> throw new AssertionError (change);
            }
            assertFalse (original.current ().getAsBoolean (), change + " cannot revive the old editing generation");
            assertTrue (original.addressable ().getAsBoolean (), "source changes do not revoke exact retained cleanup");
            source.pages[1][0].value = 0.8;
            fixture.deliverProperties ();
            assertTrue (original.addressable ().getAsBoolean (), "parameter values are observed content, not target identity");
            assertEquals (102, original.parameters ().getFirst ().getValue ());
        }
    }

    @Test
    void changedCreationContextPreservesPinnedDeviceCleanupUntilItsResourceRetires ()
    {
        final Fixture fixture = new Fixture (3);
        final var original = fixture.ready ();
        fixture.source.channel.deliver ("next-creation-track");
        assertFalse (original.current ().getAsBoolean ());
        assertTrue (original.addressable ().getAsBoolean ());
        fixture.request (original.owner ());
        fixture.deliverTracks ();
        fixture.tick ();
        fixture.tick ();
        assertEquals ("next-creation-track", fixture.assignment.trackId ());
        fixture.deliverChild (1);
        assertNotNull (fixture.host.devicePage ());
        assertTrue (original.addressable ().getAsBoolean ());
        original.parameters ().getFirst ().touchValue (false);
        fixture.host.devicePage ().parameters ().getFirst ().touchValue (true);
        assertEquals (List.of ("device-a/page-1/0:touch:false", "device-a/page-1/0:touch:true"), fixture.effects);
    }

    @Test
    void fullChildPoolKeepsBothCleanupOwnersUntilOneRetires ()
    {
        final Fixture fixture = new Fixture (3);
        final var first = fixture.ready ();
        fixture.navigate (fixture.source.observed, 0);
        fixture.request (first.owner ());
        fixture.deliverTracks ();
        fixture.tick ();
        fixture.tick ();
        fixture.deliverChild (1);
        final var second = fixture.host.devicePage ();
        assertNotNull (second);
        fixture.navigate (new DeviceState ("third-track", "device-b", 3), 0);
        fixture.request (first.owner (), second.owner ());
        fixture.tick ();
        assertNull (fixture.host.devicePage ());
        assertTrue (first.addressable ().getAsBoolean ());
        assertTrue (second.addressable ().getAsBoolean ());
        assertEquals (4, fixture.commands.size ());
        fixture.request (second.owner ());
        fixture.deliverTracks ();
        fixture.tick ();
        fixture.tick ();
        fixture.deliverChild (0);
        assertNotNull (fixture.host.devicePage ());
        assertFalse (first.addressable ().getAsBoolean ());
        assertTrue (second.addressable ().getAsBoolean ());
        second.parameters ().getFirst ().touchValue (false);
        assertEquals ("device-a/page-0/0:touch:false", fixture.effects.getLast ());
    }

    private static final class Fixture implements RetainedCursorPool.Host
    {
        private final List<String> commands = new ArrayList<> ();
        private final List<String> effects = new ArrayList<> ();
        private final List<Runnable> pendingEffects = new ArrayList<> ();
        private final SourceDevice source = new SourceDevice (this);
        private final Child[] children = {new Child (this, 0), new Child (this, 1)};
        private final BitwigRetainedDevicePages access;
        private final RetainedCursorPool pool;
        private final RetainedDevicePageHost host;
        private long sample;
        private Handle assignment;

        private Fixture (final int present)
        {
            this.navigate (new DeviceState ("other-track", "device-a", present), 1);
            final var changer = new TwosComplementValueChanger (128, 1);
            final var parameters = new ParameterBankImpl (relaxedProxy (IHost.class), changer, this.source.proxy.createCursorRemoteControlsPage (8), 8, 8);
            this.access = new BitwigRetainedDevicePages (this.source.proxy, parameters.getRemoteControlsPage (), Map.of (0, this.children[0].track, 1, this.children[1].track), changer);
            this.pool = new RetainedCursorPool (List.of (Profile.DEVICE_PAGE, Profile.DEVICE_PAGE), this);
            this.host = new RetainedDevicePageHost (this.pool, this.access);
        }

        @Override public Catalog catalog () { return new Catalog (1, Coverage.FULL, 2, 0, 64, List.of ("creation-track", "next-creation-track")); }
        @Override public boolean assign (final Handle handle) { this.assignment = handle; this.children[handle.slot ()].requestedTrack = handle; return true; }
        @Override public Observation observe (final Handle handle)
        {
            final Handle delivered = this.children[handle.slot ()].deliveredTrack;
            return new Observation (this.sample, delivered != null, delivered == null ? "" : delivered.trackId (), delivered != null, delivered == null ? 0 : delivered.assignmentGeneration ());
        }
        @Override public void release (final Handle handle) { this.children[handle.slot ()].deliveredTrack = null; }
        private void request (final String... cleanup) { this.host.requestDevicePage (true, Set.of (cleanup)); }
        private void tick () { this.sample++; this.pool.refresh (); this.host.tick (); }
        private void deliverTracks () { for (final Child child: this.children) child.deliveredTrack = child.requestedTrack; }
        private void navigate (final DeviceState device, final int page)
        {
            this.source.observed = device;
            this.source.page.deliverIdentity (device, page);
            this.source.page.deliverProperties ();
            this.updateEquality ();
        }
        private void updateEquality ()
        {
            for (final Child child: this.children)
                child.equal.deliver (child.observed != null && child.observed == this.source.observed);
        }
        private void deliverProperties () { this.source.page.deliverProperties (); for (final Child child: this.children) child.page.deliverProperties (); }
        private void applyEffects () { this.pendingEffects.forEach (Runnable::run); this.pendingEffects.clear (); }
        private void deliverChild (final int index)
        {
            final Child child = this.children[index];
            child.deliverDevice (child.requestedDevice);
            this.tick ();
            child.page.deliverIdentity (child.observed, child.requestedPage);
            child.page.deliverProperties ();
            this.tick ();
            this.tick ();
        }
        private RetainedDeviceParameters.DevicePage ready ()
        {
            this.request ();
            this.deliverTracks ();
            this.tick ();
            this.tick ();
            this.deliverChild (0);
            assertNotNull (this.host.devicePage ());
            return this.host.devicePage ();
        }
    }

    private static final class DeviceState
    {
        private final String trackId;
        private final String id;
        private final ParameterState[][] pages = new ParameterState[2][8];
        private DeviceState (final String trackId, final String id, final int present)
        {
            this.trackId = trackId;
            this.id = id;
            for (int page = 0; page < 2; page++)
                for (int slot = 0; slot < present; slot++)
                    this.pages[page][slot] = new ParameterState (id + "/page-" + page + "/" + slot, "Remote " + slot, 0.2 + slot * 0.01);
        }
    }

    private static final class ParameterState
    {
        private final String id;
        private final String name;
        private double value;
        private ParameterState (final String id, final String name, final double value) { this.id = id; this.name = name; this.value = value; }
    }

    private static final class SourceDevice
    {
        private final PageNode page;
        private final PinnableCursorDevice proxy;
        private final Value<String> channel = new Value<> ("creation-track");
        private final Value<Boolean> exists = new Value<> (true);
        private DeviceState observed;
        private boolean mainPageCreated;
        private SourceDevice (final Fixture fixture)
        {
            this.page = new PageNode (fixture, false);
            this.proxy = proxy (PinnableCursorDevice.class, (method, args) -> switch (method)
            {
                case "exists" -> this.exists.proxy (BooleanValue.class);
                case "channel" -> proxy (Channel.class, (name, arguments) -> {
                    assertEquals ("channelId", name);
                    return this.channel.proxy (StringValue.class);
                });
                case "createCursorRemoteControlsPage" -> {
                    assertEquals (1, args.length);
                    assertEquals (8, args[0]);
                    if (this.mainPageCreated) throw new IllegalStateException ("Only one main remote page may follow selection");
                    this.mainPageCreated = true;
                    yield this.page.proxy;
                }
                default -> throw new AssertionError (method);
            });
        }
    }

    private static final class Child
    {
        private final Fixture fixture;
        private final int slot;
        private final CursorTrack track;
        private final PinnableCursorDevice proxy;
        private final PageNode page;
        private final Value<Boolean> exists = new Value<> (false);
        private final Value<Boolean> pinned = new Value<> (false);
        private final Value<Boolean> equal = new Value<> (false);
        private DeviceState observed;
        private DeviceState requestedDevice;
        private boolean requestedPin;
        private int requestedPage = -1;
        private Handle requestedTrack;
        private Handle deliveredTrack;
        private Child (final Fixture fixture, final int slot)
        {
            this.fixture = fixture;
            this.slot = slot;
            this.page = new PageNode (fixture, true);
            this.pinned.setter = value -> this.requestedPin = value;
            this.page.index.setter = value -> { this.requestedPage = value; fixture.commands.add ("page:" + slot + ":" + value); };
            this.proxy = proxy (PinnableCursorDevice.class, (method, args) -> switch (method)
            {
                case "exists" -> this.exists.proxy (BooleanValue.class);
                case "isPinned" -> this.pinned.proxy (SettableBooleanValue.class);
                case "createEqualsValue" -> { assertSame (fixture.source.proxy, args[0]); yield this.equal.proxy (BooleanValue.class); }
                case "selectDevice" -> { assertSame (fixture.source.proxy, args[0]); this.requestedDevice = fixture.source.observed; fixture.commands.add ("device:" + slot + ":" + this.requestedDevice.id); yield null; }
                case "createCursorRemoteControlsPage" -> { assertEquals (3, args.length, "retained pages must be independent named cursors"); yield this.page.proxy; }
                default -> throw new AssertionError (method);
            });
            this.track = proxy (CursorTrack.class, (method, args) -> {
                assertEquals ("createCursorDevice", method);
                assertEquals (4, args.length);
                assertEquals (CursorDeviceFollowMode.FOLLOW_SELECTION, args[3]);
                return this.proxy;
            });
        }
        private void deliverDevice (final DeviceState device)
        {
            this.observed = device;
            this.exists.deliver (device != null);
            this.pinned.deliver (this.requestedPin);
            this.fixture.updateEquality ();
        }
    }

    private static final class PageNode
    {
        private final Fixture fixture;
        private final CursorRemoteControlsPage proxy;
        private final RemoteNode[] remotes = new RemoteNode[8];
        private final Value<Integer> index = new Value<> (-1);
        private final Value<String[]> names = new Value<> (new String[0]);
        private PageNode (final Fixture fixture, final boolean retained)
        {
            this.fixture = fixture;
            for (int slot = 0; slot < 8; slot++) this.remotes[slot] = new RemoteNode (fixture, retained);
            this.proxy = proxy (CursorRemoteControlsPage.class, (method, args) -> switch (method)
            {
                case "selectedPageIndex" -> this.index.proxy (SettableIntegerValue.class);
                case "pageNames" -> this.names.proxy (StringArrayValue.class);
                case "getParameter" -> this.remotes[(Integer) args[0]].proxy;
                case "hasPrevious", "hasNext" -> new Value<> (false).proxy (BooleanValue.class);
                case "pageCount" -> new Value<> (2).proxy (IntegerValue.class);
                default -> throw new AssertionError (method);
            });
        }
        private void deliverIdentity (final DeviceState device, final int page)
        {
            this.index.deliver (page);
            for (int slot = 0; slot < 8; slot++)
            {
                this.remotes[slot].target = device.pages[page][slot];
                this.remotes[slot].exists.deliver (this.remotes[slot].target != null);
            }
            if (this.fixture.children != null) this.fixture.updateEquality ();
        }
        private void deliverProperties ()
        {
            for (final RemoteNode remote: this.remotes)
                if (remote.target != null)
                {
                    remote.name.deliver (remote.target.name);
                    remote.value.deliver (remote.target.value);
                    remote.display.deliver (Double.toString (remote.target.value));
                }
        }
    }

    private static final class RemoteNode
    {
        private final RemoteControl proxy;
        private final Value<Boolean> exists = new Value<> (false);
        private final Value<Boolean> mapped = new Value<> (false);
        private final Value<String> name;
        private final Value<Double> value;
        private final Value<String> display;
        private ParameterState target;
        private RemoteNode (final Fixture fixture, final boolean retained)
        {
            this.name = new Value<> (retained ? "Previous remote" : "");
            this.value = new Value<> (retained ? 0.9 : 0.0);
            this.display = new Value<> (retained ? "stale" : "");
            this.proxy = proxy (RemoteControl.class, (method, args) -> switch (method)
            {
                case "exists" -> this.exists.proxy (BooleanValue.class);
                case "isBeingMapped" -> this.mapped.proxy (SettableBooleanValue.class);
                case "name" -> this.name.proxy (SettableStringValue.class);
                case "value" -> this.value.proxy (SettableRangedValue.class);
                case "modulatedValue" -> this.value.proxy (RangedValue.class);
                case "displayedValue" -> this.display.proxy (StringValue.class);
                case "discreteValueCount" -> new Value<> (-1).proxy (IntegerValue.class);
                // Bitwig compares distinct internal ParameterTarget wrappers, even for the same
                // mapped remote. Readiness must use the exact device/page/slot and later properties.
                case "createEqualsValue" -> new Value<> (false).proxy (BooleanValue.class);
                case "get" -> this.value.observed;
                case "touch", "setIndication" -> { fixture.effects.add (this.target.id + ":" + method + ":" + args[0]); yield null; }
                case "set", "setImmediately" -> {
                    final ParameterState target = this.target;
                    final double value = ((Number) args[0]).doubleValue () / (args.length == 2 ? ((Number) args[1]).doubleValue () - 1 : 1);
                    fixture.effects.add (target.id + ":set:" + value);
                    fixture.pendingEffects.add (() -> target.value = value);
                    yield null;
                }
                default -> throw new AssertionError (method);
            });
        }
    }

    private static final class Value<T>
    {
        private final List<Object> observers = new ArrayList<> ();
        private T observed;
        private Consumer<T> setter = ignored -> { };
        private Value (final T observed) { this.observed = observed; }
        @SuppressWarnings ("unchecked")
        private <I> I proxy (final Class<I> type)
        {
            return BitwigRetainedDevicePagesTest.proxy (type, (method, args) -> switch (method)
            {
                case "get", "getAsDouble", "getLimited" -> this.observed;
                case "set" -> { this.setter.accept ((T) args[0]); yield null; }
                case "addValueObserver" -> { this.observers.add (args[args.length - 1]); yield null; }
                default -> throw new AssertionError (method);
            });
        }
        @SuppressWarnings ("unchecked")
        private void deliver (final T value)
        {
            if (java.util.Objects.deepEquals (this.observed, value)) return;
            this.observed = value;
            for (final Object observer: this.observers)
            {
                if (observer instanceof BooleanValueChangedCallback callback) callback.valueChanged ((Boolean) value);
                else if (observer instanceof IntegerValueChangedCallback callback) callback.valueChanged ((Integer) value);
                else ((ObjectValueChangedCallback<T>) observer).valueChanged (value);
            }
        }
    }

    private interface Invocation { Object invoke (String method, Object[] args); }
    private static <T> T proxy (final Class<T> type, final Invocation invocation)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?>[] {type}, (instance, method, args) -> {
            assertFalse (method.isAnnotationPresent (Deprecated.class), "deprecated API call: " + method);
            return switch (method.getName ())
            {
                case "markInterested", "subscribe", "unsubscribe" -> null;
                case "isSubscribed" -> true;
                case "toString" -> type.getSimpleName ();
                case "hashCode" -> System.identityHashCode (instance);
                case "equals" -> instance == args[0];
                default -> invocation.invoke (method.getName (), args);
            };
        }));
    }
}
