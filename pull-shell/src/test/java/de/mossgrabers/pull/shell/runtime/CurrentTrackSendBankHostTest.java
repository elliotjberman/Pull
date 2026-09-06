// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.IProject;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.data.ISend;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ISendBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.pull.core.api.*;
import de.mossgrabers.pull.core.api.effect.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static de.mossgrabers.pull.shell.runtime.CurrentTrackParameterBankHostTest.proxy;
import static org.junit.jupiter.api.Assertions.*;

class CurrentTrackSendBankHostTest
{
    private static final ControlId OWNER = PushControlIds.continuous ("KNOB1");

    @Test
    void samplesOnlyRequestedColumnAndPublishesBoundedOwnerAndRoleMetadata ()
    {
        final Fixture fixture = new Fixture ();
        fixture.tracks[0].sendOffset = 4;
        fixture.host.refresh (banks (2));
        assertEquals (8, fixture.host.snapshot ().slots ().size ());
        final var target = fixture.snapshot (2, 0);
        assertEquals (new ParameterTargetIdentitySnapshot ("channel-send", "track-0", 0, 6), target.identity ());
        assertEquals ("Send 2", target.name ());
        assertEquals (64, target.value ());
        assertEquals (70, target.modulatedValue ());
        assertEquals (true, target.enabled ().orElseThrow ());
        for (final Track track: fixture.tracks)
            for (int send = 0; send < 8; send++)
                assertEquals (send == 2 ? 1 : 0, track.sends[send].valueReads);
        fixture.host.refresh (DesiredParameterBanks.empty ());
        assertEquals (ParameterBridgeSnapshot.empty (), fixture.host.snapshot ());
        assertEquals (1, fixture.tracks[0].sends[2].valueReads);
    }

    @Test
    void allSixtyFourSendTargetsAreIndependentlyAddressable ()
    {
        final Fixture fixture = new Fixture ();
        final Set<ParameterBankId> all = new HashSet<> ();
        for (int index = 0; index < 8; index++) all.add (ParameterBankId.trackSend (index));
        fixture.host.refresh (new DesiredParameterBanks (all));
        assertEquals (64, fixture.host.snapshot ().slots ().size ());
        assertEquals (64, fixture.host.snapshot ().slots ().values ().stream ().map (ParameterTargetSnapshot::target).distinct ().count ());
    }

    @Test
    void writesAndTouchAreSubmissionsUntilLaterAuthoritativeReadback ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.refresh (banks (7));
        final var target = fixture.snapshot (7, 0).target ();
        fixture.host.apply (fixture.host.prepare (new ResetParameterEffect (target)));
        fixture.host.apply (fixture.host.prepare (new AcquireParameterTouchEffect (OWNER, target)));
        fixture.host.apply (fixture.host.prepare (new SetParameterEnabledEffect (target, false)));
        fixture.host.apply (fixture.host.prepare (new AdjustParameterValueEffect (target, 12)));
        final Send send = fixture.tracks[0].sends[7];
        assertEquals (List.of ("reset", "touch:true", "enabled:false", "adjust:12.0"), send.events);
        fixture.host.refresh (banks (7));
        assertEquals (64, fixture.snapshot (7, 0).value ());
        assertTrue (fixture.snapshot (7, 0).enabled ().orElseThrow ());
        send.value = 76;
        send.enabled = false;
        fixture.host.refresh (banks (7));
        assertEquals (76, fixture.snapshot (7, 0).value ());
        assertFalse (fixture.snapshot (7, 0).enabled ().orElseThrow ());
        fixture.host.releaseTouches ();
        assertEquals ("touch:false", send.events.getLast ());
    }

    @Test
    void sendPageAndTrackRebindsRejectPreparedActionsAndDoNotReleaseReplacement ()
    {
        for (final boolean rebindTrack: List.of (false, true))
        {
            final Fixture fixture = new Fixture ();
            fixture.host.refresh (banks (0));
            final var target = fixture.snapshot (0, 0).target ();
            final var enabled = fixture.host.prepare (new SetParameterEnabledEffect (target, false));
            final var adjust = fixture.host.prepare (new AdjustParameterValueEffect (target, 3));
            fixture.host.apply (fixture.host.prepare (new AcquireParameterTouchEffect (OWNER, target)));
            if (rebindTrack) fixture.tracks[0].id = "other-track";
            else fixture.tracks[0].sendOffset = 8;
            assertThrows (IllegalStateException.class, () -> fixture.host.apply (enabled));
            assertThrows (IllegalStateException.class, () -> fixture.host.apply (adjust));
            fixture.host.releaseTouches ();
            assertEquals (List.of ("touch:true"), fixture.tracks[0].sends[0].events);
            fixture.host.refresh (banks (0));
            assertNotEquals (target, fixture.snapshot (0, 0).target ());
        }
    }

    @Test
    void oldExactTouchCanReleaseAfterCurrentBankChangesButNewWritesCannot ()
    {
        final Fixture fixture = new Fixture ();
        fixture.host.refresh (banks (3));
        final var target = fixture.snapshot (3, 0).target ();
        final var reset = fixture.host.prepare (new ResetParameterEffect (target));
        fixture.host.acquireTouches (fixture.host.prepareTouches (new DesiredParameterTouches (Map.of (OWNER, target)), banks (3)));
        fixture.current.set (fixture.bank ());
        assertThrows (IllegalStateException.class, () -> fixture.host.apply (reset));
        fixture.host.releaseTouches ();
        assertEquals (List.of ("touch:true", "touch:false"), fixture.tracks[0].sends[3].events);
        fixture.host.refresh (banks (3));
        final var next = fixture.host.prepare (new ResetParameterEffect (fixture.snapshot (3, 0).target ()));
        fixture.project.set ("project-two");
        assertThrows (IllegalStateException.class, () -> fixture.host.apply (next));
    }

    @Test
    void absentTracksAndSendsPublishNoTarget ()
    {
        final Fixture fixture = new Fixture ();
        fixture.tracks[0].exists = false;
        fixture.tracks[1].sends[5].exists = false;
        fixture.host.refresh (banks (5));
        assertEquals (6, fixture.host.snapshot ().slots ().size ());
        assertFalse (fixture.host.snapshot ().slots ().containsKey (ParameterSlot.trackSend (5, 0)));
        assertFalse (fixture.host.snapshot ().slots ().containsKey (ParameterSlot.trackSend (5, 1)));
    }

    private static DesiredParameterBanks banks (final int column) { return new DesiredParameterBanks (Set.of (ParameterBankId.trackSend (column))); }

    private static final class Fixture
    {
        private final Track[] tracks = new Track[8];
        private final AtomicReference<String> project = new AtomicReference<> ("project");
        private final AtomicReference<ITrackBank> current;
        private final ParameterTargetHost host;
        private Fixture ()
        {
            for (int index = 0; index < 8; index++) this.tracks[index] = new Track ("track-" + index);
            this.current = new AtomicReference<> (this.bank ());
            final var changer = new TwosComplementValueChanger (1024, 10);
            final IProject projectObject = proxy (IProject.class, (method, args) -> "getIdentity".equals (method) ? this.project.get () : null);
            final ITransport transport = proxy (ITransport.class, (method, args) -> null);
            final IModel model = proxy (IModel.class, (method, args) -> switch (method) { case "getCurrentTrackBank" -> this.current.get (); case "getProject" -> projectObject; case "getValueChanger" -> changer; case "getTransport" -> transport; default -> null; });
            this.host = new ParameterTargetHost (ParameterTargetHostTest.emptySurface (changer), model, new RuntimeLog () { public void info (final String message) { } public void warn (final String message) { } });
        }
        private ITrackBank bank () { return proxy (ITrackBank.class, (method, args) -> switch (method) { case "getPageSize" -> 8; case "getItem" -> this.tracks[(Integer) args[0]].track; default -> null; }); }
        private ParameterTargetSnapshot snapshot (final int send, final int track) { return this.host.snapshot ().slots ().get (ParameterSlot.trackSend (send, track)); }
    }

    private static final class Track
    {
        private String id;
        private boolean exists = true;
        private int sendOffset;
        private final Send[] sends = new Send[8];
        private final ITrack track;
        private Track (final String id)
        {
            this.id = id;
            for (int index = 0; index < 8; index++) this.sends[index] = new Send (index);
            final ISendBank sends = proxy (ISendBank.class, (method, args) -> switch (method) { case "getPageSize", "getItemCount" -> 8; case "getScrollPosition" -> this.sendOffset; case "getItem" -> this.sends[(Integer) args[0]].parameter; default -> null; });
            this.track = proxy (ITrack.class, (method, args) -> switch (method) { case "getChannelID" -> this.id; case "doesExist" -> this.exists; case "getSendBank" -> sends; default -> null; });
        }
    }

    private static final class Send
    {
        private boolean exists = true;
        private boolean enabled = true;
        private int value = 64;
        private int valueReads;
        private final List<String> events = new ArrayList<> ();
        private final ISend parameter;
        private Send (final int index)
        {
            this.parameter = proxy (ISend.class, (method, args) -> switch (method) {
                case "doesExist" -> this.exists; case "isEnabled" -> this.enabled; case "getName" -> "Send " + index;
                case "getValue" -> { this.valueReads++; yield this.value; } case "getModulatedValue" -> 70; case "getDisplayedValue" -> "-6 dB"; case "getNumberOfSteps" -> 1024;
                case "inc" -> { this.events.add ("adjust:" + args[0]); yield null; }
                case "touchValue" -> { this.events.add ("touch:" + args[0]); yield null; }
                case "setEnabled" -> { this.events.add ("enabled:" + args[0]); yield null; }
                case "resetValue" -> { this.events.add ("reset"); yield null; }
                default -> null;
            });
        }
    }
}
