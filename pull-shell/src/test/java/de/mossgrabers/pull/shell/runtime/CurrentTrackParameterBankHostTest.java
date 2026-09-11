// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.IProject;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class CurrentTrackParameterBankHostTest
{
    private static final DesiredParameterBanks BANKS = new DesiredParameterBanks (Set.of (ParameterBankId.TRACK_VOLUME, ParameterBankId.TRACK_PAN));
    private static final ControlId OWNER = PushControlIds.continuous ("KNOB1");

    @Test
    void allSixteenNamedTargetsReadCurrentBankWithoutHardwareBindingsAndWaitForHostAdvancement ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.refresh (BANKS);
        assertEquals (16, fixture.host.snapshot ().slots ().size ());
        final ParameterTargetRef volume = fixture.target (ParameterSlot.trackVolume (0));
        final ParameterTargetRef pan = fixture.target (ParameterSlot.trackPan (0));
        assertNotEquals (volume, pan);
        final var write = fixture.host.prepare (new AdjustParameterValueEffect (volume, 12));
        fixture.host.apply (write);
        assertEquals (List.of ("adjust:12.0"), fixture.tracks[0].volume.events);
        fixture.host.refresh (BANKS);
        assertEquals (64, fixture.host.snapshot ().slots ().get (ParameterSlot.trackVolume (0)).value ());
        fixture.tracks[0].volume.value = 76;
        fixture.host.refresh (BANKS);
        assertEquals (76, fixture.host.snapshot ().slots ().get (ParameterSlot.trackVolume (0)).value ());
    }

    @Test
    void currentBankChangeRejectsPreparedWriteButCanReleaseOriginalExactTouch ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.refresh (BANKS);
        final ParameterTargetRef target = fixture.target (ParameterSlot.trackPan (0));
        final var prepared = fixture.host.prepare (new SetParameterNormalizedValueEffect (target, 0.5));
        fixture.host.acquireTouches (fixture.host.prepareTouches (new DesiredParameterTouches (Map.of (OWNER, target)), BANKS));
        // Both windows expose the same exact track, but ordinary writes still require the current window.
        fixture.current.set (fixture.otherBank);
        assertThrows (IllegalStateException.class, () -> fixture.host.apply (prepared));
        // Production refreshes before the core can reconcile and release its old desired touch.
        fixture.host.refresh (BANKS);
        assertEquals (List.of ("touch:true", "touch:false"), fixture.tracks[0].pan.events);
        fixture.host.releaseTouches ();
        assertEquals (List.of ("touch:true", "touch:false"), fixture.tracks[0].pan.events);
        assertNotEquals (target, fixture.target (ParameterSlot.trackPan (0)));
    }

    @Test
    void mutableBankSlotRebindRejectsNewWritesAndReleasesOriginalRetainedTouch ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.refresh (BANKS);
        final ParameterTargetRef target = fixture.target (ParameterSlot.trackVolume (0));
        final var prepared = fixture.host.prepare (new ResetParameterEffect (target));
        fixture.host.acquireTouches (fixture.host.prepareTouches (new DesiredParameterTouches (Map.of (OWNER, target)), BANKS));
        fixture.tracks[0].channel = "replacement";
        assertThrows (IllegalStateException.class, () -> fixture.host.apply (prepared));
        fixture.host.refresh (BANKS);
        fixture.host.releaseTouches ();
        assertEquals (List.of ("touch:true", "touch:false"), fixture.tracks[0].volume.events);
        assertFalse (fixture.host.snapshot ().slots ().containsKey (ParameterSlot.trackVolume (0)), "the replacement track is not yet acquired");
    }

    @Test
    void projectChangeAndMissingTrackCannotReuseAParameterWrapper ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.refresh (BANKS);
        final ParameterTargetRef target = fixture.target (ParameterSlot.trackVolume (0));
        final var prepared = fixture.host.prepare (new ResetParameterEffect (target));
        fixture.project.set ("new-project");
        assertThrows (IllegalStateException.class, () -> fixture.host.apply (prepared));
        fixture.tracks[0].exists = false;
        fixture.host.refresh (BANKS);
        assertFalse (fixture.host.snapshot ().slots ().containsKey (ParameterSlot.trackVolume (0)));
        assertFalse (fixture.host.snapshot ().slots ().containsKey (ParameterSlot.trackPan (0)));
    }

    private static final class Fixture
    {
        private final Track[] tracks = new Track[8];
        private final AtomicReference<String> project = new AtomicReference<> ("project");
        private final AtomicReference<ITrackBank> current;
        private final ITrackBank otherBank;
        private final ParameterTargetHost host;
        private Fixture ()
        {
            for (int index = 0; index < 8; index++) this.tracks[index] = new Track ("track-" + index);
            final ITrackBank bank = this.bank ();
            this.otherBank = this.bank ();
            this.current = new AtomicReference<> (bank);
            final var changer = new TwosComplementValueChanger (1024, 10);
            final IProject projectObject = proxy (IProject.class, (method, args) -> "getIdentity".equals (method) ? this.project.get () : null);
            final ITransport transport = proxy (ITransport.class, (method, args) -> null);
            final IModel model = proxy (IModel.class, (method, args) -> switch (method) { case "getCurrentTrackBank" -> this.current.get (); case "getProject" -> projectObject; case "getValueChanger" -> changer; case "getTransport" -> transport; default -> null; });
            // Mix cursors are independent of the mutable visible-bank slots and already acquired.
            final Map<String, RetainedTrackParameters.TrackMix> acquired = new java.util.HashMap<> ();
            for (int index = 0; index < this.tracks.length; index++)
            {
                final Track track = this.tracks[index];
                acquired.put (track.channel, new RetainedTrackParameters.TrackMix (track.channel, index + 1, track.volume.parameter, track.pan.parameter,
                    () -> track.exists && "project".equals (this.project.get ())));
            }
            final RetainedTrackParameters retained = new RetainedTrackParameters ()
            {
                @Override public void requestTracks (final Set<String> trackIds) { acquired.keySet ().retainAll (trackIds); }
                @Override public TrackMix lookup (final String trackId) { return acquired.get (trackId); }
            };
            this.host = new ParameterTargetHost (ParameterTargetHostTest.emptySurface (changer), model, null, new RuntimeLog () { public void info (final String message) { } public void warn (final String message) { } }, retained);
        }
        private ITrackBank bank () { return proxy (ITrackBank.class, (method, args) -> switch (method) { case "getPageSize" -> 8; case "getItem" -> this.tracks[(Integer) args[0]].track; default -> null; }); }
        private ParameterTargetRef target (final ParameterSlot slot) { return this.host.snapshot ().slots ().get (slot).target (); }
    }

    private static final class Track
    {
        private String channel;
        private boolean exists = true;
        private final Parameter volume = new Parameter ();
        private final Parameter pan = new Parameter ();
        private final ITrack track;
        private Track (final String channel)
        {
            this.channel = channel;
            this.track = proxy (ITrack.class, (method, args) -> switch (method) { case "getChannelID" -> this.channel; case "doesExist" -> this.exists; case "getVolumeParameter" -> this.volume.parameter; case "getPanParameter" -> this.pan.parameter; default -> null; });
        }
    }

    private static final class Parameter
    {
        private int value = 64;
        private final List<String> events = new ArrayList<> ();
        private final IParameter parameter = proxy (IParameter.class, (method, args) -> switch (method) {
            case "doesExist" -> true; case "getName" -> "Parameter"; case "getValue", "getModulatedValue" -> this.value; case "getDisplayedValue" -> "Value"; case "getNumberOfSteps" -> 128;
            case "inc" -> { this.events.add ("adjust:" + args[0]); yield null; }
            case "touchValue" -> { this.events.add ("touch:" + args[0]); yield null; }
            case "resetValue" -> { this.events.add ("reset"); yield null; }
            default -> null;
        });
    }

    interface Invocation { Object call (String method, Object[] args); }
    static <T> T proxy (final Class<T> type, final Invocation invocation)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?>[] { type }, (object, method, args) -> {
            final Object value = invocation.call (method.getName (), args);
            if (value != null || !method.getReturnType ().isPrimitive () || method.getReturnType () == void.class) return value;
            if (method.getReturnType () == boolean.class) return false;
            if (method.getReturnType () == long.class) return 0L;
            if (method.getReturnType () == double.class) return 0.0;
            return 0;
        }));
    }
}
