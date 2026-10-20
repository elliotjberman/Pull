// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

import com.bitwig.extension.controller.api.*;
import de.mossgrabers.pull.shell.testing.TestProxies;
import com.bitwig.extension.callback.BooleanValueChangedCallback;
import com.bitwig.extension.callback.StringValueChangedCallback;
import java.util.Objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Track assignment, device assignment and publication are separate host steps. */
public final class DrumCacheHostFake
{
    public String project = "project-a";
    public final List<Resource> resources = new ArrayList<> ();
    private final Map<String, List<DrumTreeHostFake.Item>> tracks = new HashMap<> ();
    private final Map<CursorTrack, String []> sources = new IdentityHashMap<> ();
    private final List<Runnable> commands = new ArrayList<> ();
    private final List<Runnable> observers = new ArrayList<> ();
    private final Map<Resource, Boolean> pinWrites = new IdentityHashMap<> ();
    public final CursorTrack model = this.source ();
    public final CursorTrack selected = this.source ();
    public boolean rejectUnpin;
    public int trackPinRequests;
    public int trackSelections;
    public int trackTargetChanges;

    public final ControllerHost controller = TestProxies.nativeProxy (ControllerHost.class, (method, args) -> switch (method)
    {
        case "createCursorTrack" -> this.createRetainedTrack ();
        case "getProject" -> TestProxies.nativeProxy (Project.class, (m, a) -> "getRootTrackGroup".equals (m) ?
            TestProxies.nativeProxy (Track.class, (n, b) -> "channelId".equals (n) ? observedValue (StringValue.class, () -> this.project) : empty (Track.class, n)) : empty (Project.class, m));
        case "createApplication" -> TestProxies.nativeProxy (Application.class, (m, a) -> "projectName".equals (m) ? observedValue (StringValue.class, () -> this.project) : empty (Application.class, m));
        default -> empty (ControllerHost.class, method);
    });

    public List<DrumTreeHostFake.Item> addTrack (final String id, final DrumTreeHostFake.Item... devices)
    {
        final List<DrumTreeHostFake.Item> root = new ArrayList<> (List.of (devices));
        this.tracks.put (id, root);
        return root;
    }

    public void select (final CursorTrack source, final String id) { this.sources.get (source)[0] = id; }
    public int deviceSelections () { return this.resources.stream ().mapToInt (r -> r.devices.selections).sum (); }
    public int deviceTargetChanges () { return this.resources.stream ().mapToInt (r -> r.devices.cursorTargetChanges).sum (); }
    public int equalityValues () { return this.resources.stream ().mapToInt (r -> r.devices.equalityValues).sum (); }

    public void applyCommands ()
    {
        final var pending = List.copyOf (this.commands);
        this.commands.clear ();
        pending.forEach (Runnable::run);
        this.pinWrites.forEach ((resource, value) -> { if (value || !this.rejectUnpin) resource.pinned = value; });
        this.pinWrites.clear ();
        this.resources.forEach (r -> r.devices.applyCommands ());
    }

    public void publish ()
    {
        this.sources.values ().forEach (id -> id[1] = id[0]);
        this.observers.forEach (Runnable::run);
        for (final Resource resource : this.resources)
        {
            if (!resource.observedTrack.equals (resource.devices.trackId)) this.trackTargetChanges++;
            resource.observedTrack = resource.devices.trackId;
            resource.observedPinned = resource.pinned;
            resource.devices.publish ();
        }
    }

    public void advance () { this.applyCommands (); this.publish (); }
    public void settle (final DrumDeviceCache.View... views)
    {
        for (int i = 0; i < 100; i++)
        {
            for (final var view : views) view.machines ();
            this.advance ();
        }
    }

    private CursorTrack source ()
    {
        final String [] id = {"", ""};
        final var channelId = observedValue (StringValue.class, () -> id[1]);
        final var exists = observedValue (BooleanValue.class, () -> !id[1].isBlank () && this.tracks.containsKey (id[1]));
        final CursorTrack source = TestProxies.nativeProxy (CursorTrack.class, (method, args) -> switch (method)
        {
            case "channelId" -> channelId;
            case "exists" -> exists;
            default -> empty (CursorTrack.class, method);
        });
        this.sources.put (source, id);
        return source;
    }

    private CursorTrack createRetainedTrack ()
    {
        final Resource resource = new Resource ();
        this.resources.add (resource);
        final var pinned = TestProxies.nativeProxy (SettableBooleanValue.class, (method, args) -> switch (method)
        {
            case "get" -> resource.observedPinned;
            case "set" -> { this.trackPinRequests++; this.pinWrites.put (resource, (Boolean) args[0]); yield null; }
            case "addValueObserver" -> null;
            default -> empty (SettableBooleanValue.class, method);
        });
        return TestProxies.proxy (CursorTrack.class, (proxy, method, args) -> {
            if ("isPinned".equals (method.getName ())) return pinned;
            if ("selectChannel".equals (method.getName ()))
            {
                final String id = this.sources.get ((CursorTrack) args[0])[0];
                final List<DrumTreeHostFake.Item> root = this.tracks.get (id);
                this.trackSelections++;
                this.commands.add (() -> {
                    if (resource.pinned) return;
                    resource.devices.trackId = id;
                    resource.devices.root = root;
                });
                return null;
            }
            return method.invoke (resource.devices.track, args);
        });
    }

    public static final class Resource
    {
        public final DrumTreeHostFake devices = new DrumTreeHostFake ();
        public boolean pinned;
        public boolean observedPinned;
        private String observedTrack = "";
        private Resource () { this.devices.trackId = ""; }
    }

    private <T> T observedValue (final Class<T> type, final Supplier<Object> read)
    {
        final Object [] observed = {read.get ()};
        final List<Object> callbacks = new ArrayList<> ();
        this.observers.add (() -> {
            final Object current = read.get ();
            if (Objects.equals (observed[0], current)) return;
            observed[0] = current;
            callbacks.forEach (callback -> publish (callback, current));
        });
        return TestProxies.nativeProxy (type, (method, args) -> switch (method)
        {
            case "get" -> observed[0];
            case "addValueObserver" -> { callbacks.add (args[0]); publish (args[0], observed[0]); yield null; }
            default -> empty (type, method);
        });
    }

    private static void publish (final Object callback, final Object value)
    {
        if (callback instanceof StringValueChangedCallback changed) changed.valueChanged ((String) value);
        else if (callback instanceof BooleanValueChangedCallback changed) changed.valueChanged ((Boolean) value);
        else throw new AssertionError (callback);
    }

    private static Object empty (final Class<?> type, final String method)
    {
        for (final var candidate : type.getMethods ())
            if (candidate.getName ().equals (method)) return TestProxies.empty (candidate.getReturnType ());
        throw new AssertionError (method);
    }
}
