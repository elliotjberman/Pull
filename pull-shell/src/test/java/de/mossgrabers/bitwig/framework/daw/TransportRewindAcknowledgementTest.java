// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.bitwig.framework.daw;

import com.bitwig.extension.controller.api.*;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IApplication;
import org.junit.jupiter.api.Test;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import static de.mossgrabers.pull.shell.testing.TestProxies.*;
import static org.junit.jupiter.api.Assertions.*;

class TransportRewindAcknowledgementTest
{
    @Test
    void rewindWaitsForStoppedReadbackAndSubmissionDoesNotAcknowledgePosition ()
    {
        final Fixture fixture = new Fixture ();
        fixture.adapter.stopAndRewind ();
        for (int i = 0; i < 12; i++) fixture.poll ();
        assertEquals (List.of ("stop"), fixture.commands, "even a slow stop must precede rewind");
        fixture.playing = false;
        fixture.poll ();
        assertEquals (List.of ("stop", "position:0.0"), fixture.commands);
        assertEquals (8, fixture.position, "the fake must not make submission into readback");
        fixture.poll ();
        assertFalse (fixture.scheduled.isEmpty (), "position remains unacknowledged");
        fixture.position = 0;
        fixture.poll ();
        assertTrue (fixture.scheduled.isEmpty ());
        assertTrue (fixture.errors.isEmpty ());
    }

    @Test
    void newPlayProjectChangeAndCloseInvalidatePendingRewind ()
    {
        final Fixture fixture = new Fixture ();
        fixture.adapter.stopAndRewind ();
        fixture.adapter.play ();
        fixture.playing = false;
        fixture.poll ();
        assertEquals (List.of ("stop", "play"), fixture.commands);
        fixture.adapter.stopAndRewind ();
        fixture.project = "other";
        fixture.poll ();
        assertEquals (List.of ("stop", "play", "stop"), fixture.commands);
        fixture.adapter.stopAndRewind ();
        fixture.adapter.close ();
        fixture.poll ();
        assertFalse (fixture.commands.stream ().anyMatch (command -> command.startsWith ("position")));
        assertTrue (fixture.scheduled.isEmpty ());
        assertTrue (fixture.errors.isEmpty (), "ordinary cancellation must not report a timeout");
    }

    @Test
    void replacementWaitsForItsOwnStoppedReadbackAfterAnEarlierPositionSubmission ()
    {
        final Fixture fixture = new Fixture ();
        fixture.playing = false;
        fixture.adapter.stopAndRewind ();
        fixture.poll ();
        fixture.playing = true;
        fixture.adapter.stopAndRewind ();
        for (int i = 0; i < 12; i++) fixture.poll ();
        assertEquals (List.of ("stop", "position:0.0", "stop"), fixture.commands);
        fixture.playing = false;
        fixture.poll ();
        assertEquals (List.of ("stop", "position:0.0", "stop", "position:0.0"), fixture.commands);
        fixture.position = 0;
        fixture.poll ();
        assertTrue (fixture.scheduled.isEmpty ());
        assertTrue (fixture.errors.isEmpty ());
    }

    @Test
    void timeoutAbandonsWithoutRewindAndAReplacementGetsItsOwnDeadline ()
    {
        final Fixture fixture = new Fixture ();
        fixture.adapter.stopAndRewind ();
        for (int i = 0; i < 149; i++) fixture.poll ();
        fixture.adapter.stopAndRewind ();
        for (int i = 0; i < 149; i++) fixture.poll ();
        assertTrue (fixture.errors.isEmpty (), "the replacement must not inherit the retired request's deadline");
        fixture.poll ();
        assertTrue (fixture.scheduled.isEmpty ());
        assertEquals (List.of ("stop", "stop"), fixture.commands);
        assertEquals (1, fixture.errors.size ());
    }

    @Test
    void unacknowledgedPositionUsesTheSameDeadlineAsTheStop ()
    {
        final Fixture fixture = new Fixture ();
        fixture.adapter.stopAndRewind ();
        for (int i = 0; i < 100; i++) fixture.poll ();
        fixture.playing = false;
        fixture.poll ();
        for (int i = 0; i < 48; i++) fixture.poll ();
        assertTrue (fixture.errors.isEmpty ());
        fixture.poll ();
        assertEquals (List.of ("stop", "position:0.0"), fixture.commands);
        assertEquals (8, fixture.position, "an unacknowledged write cannot become host state at the deadline");
        assertTrue (fixture.scheduled.isEmpty ());
        assertEquals (List.of ("Stop/rewind was not acknowledged before its deadline; abandoning the pending operation"), fixture.errors);
    }

    private static final class Fixture
    {
        private boolean playing = true;
        private double position = 8;
        private String project = "project";
        private final ArrayDeque<Runnable> scheduled = new ArrayDeque<> ();
        private final List<String> commands = new ArrayList<> ();
        private final List<String> errors = new ArrayList<> ();
        private final TransportImpl adapter;

        private Fixture ()
        {
            final var playingValue = proxy (SettableBooleanValue.class, (p, method, args) -> "get".equals (method.getName ()) ? this.playing : empty (method.getReturnType ()));
            final var positionValue = proxy (SettableBeatTimeValue.class, (p, method, args) -> "get".equals (method.getName ()) ? this.position : empty (method.getReturnType ()));
            final var transport = proxy (Transport.class, (p, method, args) -> switch (method.getName ()) {
                case "isPlaying" -> playingValue;
                case "getPosition" -> positionValue;
                case "stop", "play", "restart" -> { this.commands.add (method.getName ()); yield null; }
                case "setPosition" -> { this.commands.add ("position:" + args[0]); yield null; }
                default -> empty (method.getReturnType ());
            });
            final var host = proxy (ControllerHost.class, (p, method, args) -> switch (method.getName ()) {
                case "createTransport" -> transport;
                case "scheduleTask" -> { this.scheduled.add ((Runnable) args[0]); yield null; }
                case "errorln" -> { this.errors.add ((String) args[0]); yield null; }
                default -> empty (method.getReturnType ());
            });
            this.adapter = new TransportImpl (host, (IApplication) empty (IApplication.class), (Arranger) empty (Arranger.class), (IValueChanger) empty (IValueChanger.class), () -> this.project);
        }

        private void poll () { this.scheduled.remove ().run (); }
    }
}
