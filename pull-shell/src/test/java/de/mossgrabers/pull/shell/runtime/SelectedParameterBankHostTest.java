// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.IProject;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.data.ISend;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ISendBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class SelectedParameterBankHostTest
{
    private static final DesiredParameterBanks BANKS = new DesiredParameterBanks (Set.of (ParameterBankId.SELECTED_TRACK, ParameterBankId.SELECTED_TRACK_SENDS));
    private static final ControlId OWNER = PushControlIds.continuous ("KNOB3");

    @Test
    void namedBanksDoNotDependOnHardwareBindingsAndEnabledReadbackWaitsForHost ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.refresh (BANKS);
        assertEquals (10, fixture.host.snapshot ().slots ().size ());
        assertTrue (fixture.host.snapshot ().slots ().get (ParameterSlot.SELECTED_TRACK_VOLUME).enabled ().isEmpty ());
        final ParameterTargetRef target = fixture.sendTarget ();
        final var prepared = fixture.host.prepare (new SetParameterEnabledEffect (target, false));
        assertTrue (fixture.track.sends[0].events.isEmpty ());
        fixture.host.apply (prepared);
        fixture.host.refresh (BANKS);
        assertEquals (List.of ("enabled:false"), fixture.track.sends[0].events);
        assertEquals (Optional.of (true), fixture.host.snapshot ().slots ().get (ParameterSlot.selectedTrackSend (0)).enabled ());
        fixture.track.sends[0].enabled = false;
        fixture.host.refresh (BANKS);
        assertEquals (Optional.of (false), fixture.host.snapshot ().slots ().get (ParameterSlot.selectedTrackSend (0)).enabled ());
        assertThrows (IllegalArgumentException.class, () -> fixture.host.prepare (new SetParameterEnabledEffect (fixture.host.snapshot ().slots ().get (ParameterSlot.SELECTED_TRACK_VOLUME).target (), false)));
    }

    @Test
    void resetTouchAndEnabledHaveAnExplicitOrderAndCompleteReplayDoesNotRetriggerTouch ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.refresh (BANKS);
        final ParameterTargetRef target = fixture.sendTarget ();
        final var touches = fixture.host.prepareTouches (new DesiredParameterTouches (Map.of (OWNER, target)), BANKS);
        final var reset = fixture.host.prepare (new ResetParameterEffect (target));
        final var touch = fixture.host.prepare (new AcquireParameterTouchEffect (OWNER, target));
        final var enabled = fixture.host.prepare (new SetParameterEnabledEffect (target, false));
        assertTrue (fixture.track.sends[0].events.isEmpty ());
        fixture.host.releaseTouchesExcept (touches);
        fixture.host.apply (reset);
        fixture.host.apply (touch);
        fixture.host.apply (enabled);
        fixture.host.acquireTouches (touches);
        fixture.host.acquireTouches (touches);
        fixture.host.releaseTouches ();
        assertEquals (List.of ("reset", "touch:true", "enabled:false", "touch:false"), fixture.track.sends[0].events);
    }

    @Test
    void selectedTargetChangesRejectNewWritesButCanReleaseAnOldStillAddressableActuator ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.refresh (BANKS);
        final ParameterTargetRef target = fixture.sendTarget ();
        final var write = fixture.host.prepare (new SetParameterEnabledEffect (target, false));
        fixture.host.acquireTouches (fixture.host.prepareTouches (new DesiredParameterTouches (Map.of (OWNER, target)), BANKS));
        fixture.privateChannel.set ("track-b");
        fixture.generation.incrementAndGet ();
        fixture.selected.set (new TrackFixture ("track-b").track);
        assertThrows (IllegalStateException.class, () -> fixture.host.apply (write));
        // Production refreshes before the core can reconcile and release its old desired touch.
        fixture.host.refresh (BANKS);
        assertEquals (List.of ("touch:true", "touch:false"), fixture.track.sends[0].events);
        fixture.host.releaseTouches ();
        assertEquals (List.of ("touch:true", "touch:false"), fixture.track.sends[0].events);
        assertNotEquals (target, fixture.sendTarget ());
    }

    @Test
    void privateSelectionMismatchAndBankProxyRebindAreFailClosed ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.refresh (BANKS);
        final ParameterTargetRef target = fixture.sendTarget ();
        fixture.host.acquireTouches (fixture.host.prepareTouches (new DesiredParameterTouches (Map.of (OWNER, target)), BANKS));
        fixture.track.channel.set ("rebound-track");
        fixture.host.refresh (BANKS);
        fixture.host.releaseTouches ();
        assertEquals (List.of ("touch:true"), fixture.track.sends[0].events);
        assertTrue (fixture.host.snapshot ().slots ().isEmpty ());
    }

    private static final class Fixture
    {
        private final TrackFixture track = new TrackFixture ("track-a");
        private final AtomicReference<ITrack> selected = new AtomicReference<> (this.track.track);
        private final AtomicReference<String> privateChannel = new AtomicReference<> ("track-a");
        private final AtomicLong generation = new AtomicLong (1);
        private final ParameterTargetHost host;

        private Fixture ()
        {
            final IValueChanger changer = new TwosComplementValueChanger (1024, 10);
            final ITrackBank bank = proxy (ITrackBank.class, (method, args) -> switch (method) { case "getSelectedItem" -> Optional.ofNullable (this.selected.get ()); case "getPageSize" -> 8; default -> null; });
            final IProject project = proxy (IProject.class, (method, args) -> "getIdentity".equals (method) ? "project-a" : null);
            final ITransport transport = proxy (ITransport.class, (method, args) -> null);
            final IModel model = proxy (IModel.class, (method, args) -> switch (method) { case "getTransport" -> transport; case "getProject" -> project; case "getCurrentTrackBank" -> bank; case "getValueChanger" -> changer; default -> null; });
            final ISelectedTrackNoteTarget privateTarget = proxy (ISelectedTrackNoteTarget.class, (method, args) -> switch (method) { case "doesExist" -> true; case "getChannelID" -> this.privateChannel.get (); case "getGeneration" -> this.generation.get (); default -> null; });
            this.host = new ParameterTargetHost (ParameterTargetHostTest.emptySurface (changer), model, privateTarget, new RuntimeLog () { public void info (final String message) {} public void warn (final String message) {} });
        }
        private ParameterTargetRef sendTarget () { return this.host.snapshot ().slots ().get (ParameterSlot.selectedTrackSend (0)).target (); }
    }

    private static final class TrackFixture
    {
        private final AtomicReference<String> channel;
        private final Parameter volume = new Parameter (false);
        private final Parameter pan = new Parameter (false);
        private final Parameter[] sends = new Parameter[8];
        private final ITrack track;
        private TrackFixture (final String channel)
        {
            this.channel = new AtomicReference<> (channel);
            for (int index = 0; index < 8; index++) this.sends[index] = new Parameter (true);
            final ISendBank bank = proxy (ISendBank.class, (method, args) -> switch (method) { case "getPageSize" -> 8; case "getScrollPosition" -> 0; case "getItem" -> this.sends[(Integer) args[0]].parameter; default -> null; });
            this.track = proxy (ITrack.class, (method, args) -> switch (method) { case "doesExist", "isSelected" -> true; case "getChannelID" -> this.channel.get (); case "getVolumeParameter" -> this.volume.parameter; case "getPanParameter" -> this.pan.parameter; case "getSendBank" -> bank; default -> null; });
        }
    }

    private static final class Parameter
    {
        private boolean enabled = true;
        private final List<String> events = new ArrayList<> ();
        private final IParameter parameter;
        private Parameter (final boolean send)
        {
            final Invocation invocation = (method, args) -> switch (method) {
                case "doesExist" -> true;
                case "getName" -> "Parameter";
                case "getValue", "getModulatedValue" -> 64;
                case "getNumberOfSteps" -> 128;
                case "getDisplayedValue" -> "64";
                case "isEnabled" -> this.enabled;
                case "setEnabled" -> { this.events.add ("enabled:" + args[0]); yield null; }
                case "touchValue" -> { this.events.add ("touch:" + args[0]); yield null; }
                case "resetValue" -> { this.events.add ("reset"); yield null; }
                default -> null;
            };
            this.parameter = send ? proxy (ISend.class, invocation) : proxy (IParameter.class, invocation);
        }
    }

    private interface Invocation { Object call (String method, Object[] args); }
    private static <T> T proxy (final Class<T> type, final Invocation invocation)
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
