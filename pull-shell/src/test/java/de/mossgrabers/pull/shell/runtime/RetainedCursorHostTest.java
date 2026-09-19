// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.*;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.pull.shell.runtime.RetainedCursorPool.Coverage;
import org.junit.jupiter.api.Test;

import static de.mossgrabers.pull.shell.testing.TestProxies.nativeProxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real initialization adapter; proxy assignment and value delivery advance separately. */
class RetainedCursorHostTest
{
    @Test
    void uuidAloneCannotPublishPreviousTargetsMixAndSubmissionCannotAcknowledgeAWrite ()
    {
        final FakeHost host = new FakeHost (2);
        final RetainedCursorHost retained = host.service ();
        retained.tick ();
        retained.requestTracks (Set.of ("track-0"));
        assertNull (retained.lookup ("track-0"));
        host.applyIdentities ();
        retained.tick ();
        retained.tick ();
        assertNull (retained.lookup ("track-0"), "new UUID with stale mix values is not ready");
        host.applyParameters ();
        retained.tick ();
        assertNull (retained.lookup ("track-0"));
        retained.tick ();
        final var target = retained.lookup ("track-0");
        assertNotNull (target);
        final int before = target.volume ().getValue ();
        target.volume ().setNormalizedValue (0.7);
        assertEquals (before, target.volume ().getValue (), "submission must not change host read-back");
        assertEquals (List.of ("track-0:" + (89.0 / 127)), host.writes);
        host.applyWrites ();
        retained.tick ();
        assertEquals (89, target.volume ().getValue ());
        final int assignments = host.assignments;
        retained.requestTracks (Set.of ("track-0"));
        retained.tick ();
        assertEquals (assignments, host.assignments, "replay must not reselect a ready cursor");
    }

    @Test
    void retainedMixSurvivesBankReorderButOldHandleCannotWriteReplacement ()
    {
        final FakeHost host = new FakeHost (2);
        final RetainedCursorHost retained = host.service ();
        retained.tick ();
        retained.requestTracks (Set.of ("track-0"));
        host.applyIdentities ();
        host.applyParameters ();
        retained.tick ();
        retained.tick ();
        final var original = retained.lookup ("track-0");
        assertNotNull (original);
        java.util.Collections.swap (host.tracks, 0, 1);
        retained.tick ();
        assertTrue (original.addressable ().getAsBoolean ());
        original.pan ().touchValue (false);
        assertEquals (List.of ("track-0:touch:false"), host.writes);
        final CursorNode cursor = host.cursors.stream ().filter (item -> item.target != null).findFirst ().orElseThrow ();
        cursor.observed = host.tracks.get (0);
        assertFalse (original.addressable ().getAsBoolean (), "retarget must fail at actual effect boundary");
        retained.requestTracks (Set.of ());
        retained.requestTracks (Set.of ("track-1"));
        host.applyIdentities ();
        host.applyParameters ();
        retained.tick ();
        retained.tick ();
        assertNotNull (retained.lookup ("track-1"));
        assertFalse (original.addressable ().getAsBoolean ());
        final var second = retained.lookup ("track-1");
        host.project = "second-project";
        assertFalse (second.addressable ().getAsBoolean (), "project changes fence without waiting for a tick");
    }

    @Test
    void sendCountChangesRetireOldSendGenerationAndReuseTheFixedWindow ()
    {
        final FakeHost host = new FakeHost (1);
        final RetainedCursorHost retained = host.service ();
        retained.tick ();
        retained.requestTracks (Set.of ("track-0"));
        host.applyIdentities ();
        host.applyParameters ();
        retained.tick ();
        retained.tick ();
        final var mix = retained.lookup ("track-0");
        final long generation = mix.generation (2);
        assertTrue (generation > 0);
        mix.parameter (9).touchValue (true);
        assertEquals (List.of ("track-0:touch:true"), host.writes);
        host.tracks.get (0).sendCount = 3;
        host.sendCountObservers.forEach (observer -> observer.valueChanged (3));
        assertEquals (-1, mix.generation (2), "old send generation is invalid before the next tick");
        assertTrue (mix.addressable ().getAsBoolean (), "volume and pan remain addressable");
        retained.tick ();
        assertEquals (-1, mix.generation (2));
        retained.tick ();
        assertTrue (mix.generation (2) > generation);
        assertFalse (mix.parameter (9).doesExist ());
        host.tracks.get (0).sendCount = 9;
        host.sendCountObservers.forEach (observer -> observer.valueChanged (9));
        retained.tick ();
        retained.tick ();
        assertEquals (8, mix.sends ().size ());
        assertTrue (mix.parameter (9).doesExist ());
        assertEquals (1, host.assignments, "send changes do not rebuild or reassign the track pool");
    }

    @Test
    void flatDiscoveryReportsOverflowAndNeverMistakesUnknownTracksForDeleted ()
    {
        final FakeHost host = new FakeHost (65);
        final RetainedCursorHost retained = host.service ();
        retained.tick ();
        assertTrue (host.flat);
        assertEquals (Coverage.PARTIAL, retained.pool ().catalog ().coverage ());
        assertEquals (65, retained.pool ().catalog ().totalCount ());
        assertEquals (64, retained.pool ().catalog ().trackIds ().size ());
        retained.requestTracks (Set.of ("track-64"));
        assertEquals (RetainedCursorPool.Status.UNAVAILABLE, retained.pool ().lookup ("parameters", "track-64").status ());
        assertEquals (0, host.assignments);
    }

    @Test
    void idlePoolStopsSamplingTheDiscoveryWindowUntilDemandReturns ()
    {
        final FakeHost host = new FakeHost (32);
        final RetainedCursorHost retained = host.service ();
        retained.tick ();
        final int initial = host.discoveryReads;
        assertTrue (initial > 0);
        for (int index = 0; index < 20; index++) retained.tick ();
        assertEquals (initial, host.discoveryReads);
        retained.requestTracks (Set.of ("track-0"));
        retained.tick ();
        assertTrue (host.discoveryReads > initial);
        retained.requestTracks (Set.of ());
        final int stopped = host.discoveryReads;
        retained.tick ();
        retained.tick ();
        assertEquals (stopped, host.discoveryReads);
    }

    private static final class FakeHost
    {
        private final List<TrackState> tracks = new ArrayList<> ();
        private final List<CursorNode> cursors = new ArrayList<> ();
        private final List<String> writes = new ArrayList<> ();
        private final List<com.bitwig.extension.callback.IntegerValueChangedCallback> sendCountObservers = new ArrayList<> ();
        private final List<Runnable> pendingWrites = new ArrayList<> ();
        private String project = "project";
        private boolean flat;
        private int assignments;
        private int discoveryReads;

        private FakeHost (final int count)
        {
            for (int index = 0; index < count; index++)
                this.tracks.add (new TrackState ("track-" + index, 0.2 + index * 0.01));
        }

        private RetainedCursorHost service ()
        {
            final ControllerHost host = nativeProxy (ControllerHost.class, (method, args) -> switch (method)
            {
                case "createTrackBank" -> {
                    this.flat = (Boolean) args[3];
                    yield this.bank ();
                }
                case "createCursorTrack" -> {
                    final CursorNode cursor = new CursorNode (this, (Integer) args[3]);
                    this.cursors.add (cursor);
                    yield cursor.proxy;
                }
                default -> throw new AssertionError (method);
            });
            return new RetainedCursorHost (host, new TwosComplementValueChanger (128, 1), () -> this.project, new RuntimeLog ()
            {
                @Override public void info (final String message) { }
                @Override public void warn (final String message) { }
            });
        }

        private TrackBank bank ()
        {
            return nativeProxy (TrackBank.class, (method, args) -> switch (method)
            {
                case "itemCount" -> value (IntegerValue.class, this.tracks::size);
                case "scrollPosition" -> value (SettableIntegerValue.class, () -> 0);
                case "getItemAt" -> {
                    final int index = (Integer) args[0];
                    yield this.track (() -> { this.discoveryReads++; return index < this.tracks.size () ? this.tracks.get (index) : TrackState.EMPTY; });
                }
                default -> throw new AssertionError (method);
            });
        }

        private Track track (final Supplier<TrackState> state)
        {
            return nativeProxy (Track.class, (method, args) -> trackProperty (method, state, state, this));
        }

        private void applyIdentities ()
        {
            for (final CursorNode cursor: this.cursors)
            {
                if (cursor.target != null)
                    cursor.observed = cursor.target;
                cursor.pinned = cursor.requestedPin;
            }
        }

        private void applyParameters ()
        {
            for (final CursorNode cursor: this.cursors)
                cursor.parameters = cursor.observed;
        }

        private void applyWrites ()
        {
            this.pendingWrites.forEach (Runnable::run);
            this.pendingWrites.clear ();
        }
    }

    private static final class TrackState
    {
        private static final TrackState EMPTY = new TrackState ("", 0);
        private final String id;
        private double volume;
        private int sendCount = 8;
        private TrackState (final String id, final double volume) { this.id = id; this.volume = volume; }
    }

    private static final class CursorNode
    {
        private final CursorTrack proxy;
        private TrackState target;
        private TrackState observed = TrackState.EMPTY;
        private TrackState parameters = TrackState.EMPTY;
        private boolean pinned;
        private boolean requestedPin;

        private CursorNode (final FakeHost host, final int scenes)
        {
            this.proxy = nativeProxy (CursorTrack.class, (method, args) -> switch (method)
            {
                case "isPinned" -> nativeProxy (SettableBooleanValue.class, (name, arguments) -> {
                    if (name.equals ("set")) { this.requestedPin = (Boolean) arguments[0]; return null; }
                    if (name.equals ("get")) return this.pinned;
                    throw new AssertionError (name);
                });
                case "selectChannel" -> {
                    final String id = ((Track) args[0]).channelId ().get ();
                    this.target = host.tracks.stream ().filter (track -> track.id.equals (id)).findFirst ().orElseThrow ();
                    host.assignments++;
                    yield null;
                }
                case "clipLauncherSlotBank" -> nativeProxy (ClipLauncherSlotBank.class, (name, arguments) -> switch (name)
                {
                    case "scrollPosition", "itemCount" -> value (SettableIntegerValue.class, () -> 0);
                    case "getItemAt" -> nativeProxy (ClipLauncherSlot.class, (slotMethod, slotArgs) -> switch (slotMethod)
                    {
                        case "sceneIndex" -> value (IntegerValue.class, () -> (Integer) arguments[0]);
                        case "name" -> value (SettableStringValue.class, () -> "");
                        default -> value (BooleanValue.class, () -> false);
                    });
                    default -> throw new AssertionError (name);
                });
                default -> trackProperty (method, () -> this.observed, () -> this.parameters, host);
            });
        }
    }

    private static Object trackProperty (final String method, final Supplier<TrackState> identity, final Supplier<TrackState> parameters, final FakeHost host)
    {
        return switch (method)
        {
            case "exists" -> value (BooleanValue.class, () -> !identity.get ().id.isEmpty ());
            case "channelId", "name" -> value (SettableStringValue.class, () -> identity.get ().id);
            case "crossFadeMode" -> value (com.bitwig.extension.controller.api.SettableEnumValue.class, () -> "AB");
            case "trackType" -> value (StringValue.class, () -> "Instrument");
            case "position" -> value (IntegerValue.class, () -> 0);
            case "sendBank" -> nativeProxy (SendBank.class, (name, args) -> switch (name)
            {
                case "itemCount" -> nativeProxy (IntegerValue.class, (operation, arguments) -> switch (operation)
                {
                    case "get" -> parameters.get ().sendCount;
                    case "addValueObserver" -> { host.sendCountObservers.add ((com.bitwig.extension.callback.IntegerValueChangedCallback) arguments[0]); yield null; }
                    default -> throw new AssertionError (operation);
                });
                case "scrollPosition" -> value (SettableIntegerValue.class, () -> 0);
                case "getItemAt" -> send (parameters, (Integer) args[0], host);
                default -> throw new AssertionError (name);
            });
            case "volume", "pan" -> parameter (parameters, method.equals ("pan"), host);
            default -> throw new AssertionError (method);
        };
    }

    private static Send send (final Supplier<TrackState> target, final int index, final FakeHost host)
    {
        return nativeProxy (Send.class, (method, args) -> switch (method)
        {
            case "exists" -> value (BooleanValue.class, () -> !target.get ().id.isEmpty () && index < target.get ().sendCount);
            case "name" -> value (StringValue.class, () -> "Send " + index);
            case "isEnabled" -> value (SettableBooleanValue.class, () -> true);
            case "sendChannelColor" -> value (SettableColorValue.class, () -> 0);
            default -> parameterProperty (target, false, host, method, args);
        });
    }

    private static Parameter parameter (final Supplier<TrackState> target, final boolean pan, final FakeHost host)
    {
        return nativeProxy (Parameter.class, (method, args) -> parameterProperty (target, pan, host, method, args));
    }

    private static Object parameterProperty (final Supplier<TrackState> target, final boolean pan, final FakeHost host, final String method, final Object[] args)
    {
        return switch (method)
        {
            case "exists" -> value (BooleanValue.class, () -> !target.get ().id.isEmpty ());
            case "name" -> value (StringValue.class, () -> pan ? "Pan" : "Volume");
            case "value", "modulatedValue" -> value (SettableRangedValue.class, () -> pan ? 0.5 : target.get ().volume);
            case "get" -> pan ? 0.5 : target.get ().volume;
            case "displayedValue" -> value (StringValue.class, () -> Double.toString (pan ? 0.5 : target.get ().volume));
            case "discreteValueCount" -> value (IntegerValue.class, () -> -1);
            case "set", "setImmediately" -> {
                final TrackState state = target.get ();
                final double next = ((Number) args[0]).doubleValue () / (args.length == 2 ? ((Number) args[1]).doubleValue () - 1 : 1);
                host.writes.add (state.id + ":" + next);
                host.pendingWrites.add (() -> state.volume = next);
                yield null;
            }
            case "touch" -> { host.writes.add (target.get ().id + ":touch:" + args[0]); yield null; }
            default -> throw new AssertionError (method);
        };
    }

    private static <T> T value (final Class<T> type, final Supplier<Object> value)
    {
        return nativeProxy (type, (method, args) -> switch (method)
        {
            case "get", "getAsDouble", "getLimited" -> value.get ();
            case "set", "addValueObserver" -> null;
            default -> throw new AssertionError (method);
        });
    }

}
