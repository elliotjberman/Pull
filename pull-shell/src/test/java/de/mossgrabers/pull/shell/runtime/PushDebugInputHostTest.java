// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.shell.input.InputKind;
import de.mossgrabers.pull.shell.input.InputPhase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Browser input requests must retain the permanent routed lifecycle and bounded cleanup. */
class PushDebugInputHostTest
{
    private static final ControlId PLAY = PushControlIds.button ("PLAY");
    private static final ControlId KNOB = PushControlIds.continuous ("KNOB1");
    private static final ControlId PAD = PushControlIds.pad (5);

    @TempDir
    Path debugDirectory;


    @Test
    void buttonBeginAndEndShareOneAdmissionLease () throws IOException
    {
        final AtomicLong time = new AtomicLong ();
        final FakeSurface surface = new FakeSurface ();
        final FakeAdmission admission = new FakeAdmission ();
        final PushDebugInputHost host = this.host (surface, admission, time);

        this.request (host, "begin", PLAY, InputKind.BUTTON, "BEGIN", 127);
        host.tick ();

        assertEquals (List.of ("push.button.play:BUTTON:BEGIN:127"), surface.events);
        assertTrue (admission.debugActive);
        assertTrue (this.status ().contains ("\"state\":\"APPLIED\""));

        admission.routeIdle = false;
        this.request (host, "end", PLAY, InputKind.BUTTON, "END", 0);
        host.tick ();

        assertEquals (List.of (
            "push.button.play:BUTTON:BEGIN:127",
            "push.button.play:BUTTON:END:0"), surface.events);
        assertTrue (admission.debugActive, "release waits for the routed lifecycle to become idle");

        admission.routeIdle = true;
        host.tick ();
        assertFalse (admission.debugActive);
    }


    @Test
    void touchAndPressureUseTheirInstalledInputKinds () throws IOException
    {
        final AtomicLong time = new AtomicLong ();
        final FakeSurface surface = new FakeSurface ();
        final FakeAdmission admission = new FakeAdmission ();
        final PushDebugInputHost host = this.host (surface, admission, time);

        this.request (host, "touch-down", KNOB, InputKind.TOUCH, "BEGIN", 127);
        host.tick ();
        this.request (host, "touch-up", KNOB, InputKind.TOUCH, "END", 0);
        host.tick ();

        this.request (host, "pad-down", PAD, InputKind.PAD, "BEGIN", 100);
        host.tick ();
        this.request (host, "pressure", PAD, InputKind.POLY_PRESSURE, "CHANGE", 91);
        host.tick ();
        this.request (host, "pad-up", PAD, InputKind.PAD, "END", 0);
        host.tick ();

        assertEquals (List.of (
            "push.continuous.knob1:TOUCH:BEGIN:127",
            "push.continuous.knob1:TOUCH:END:0",
            "push.pad.5:PAD:BEGIN:100",
            "push.pad.5:POLY_PRESSURE:CHANGE:91",
            "push.pad.5:PAD:END:0"), surface.events);
        assertFalse (admission.debugActive);
    }


    @Test
    void pressureUpdatesAreCoalescedToTheLatestValuePerTick () throws IOException
    {
        final AtomicLong time = new AtomicLong ();
        final FakeSurface surface = new FakeSurface ();
        final FakeAdmission admission = new FakeAdmission ();
        final PushDebugInputHost host = this.host (surface, admission, time);

        this.request (host, "pressure-1", PAD, InputKind.POLY_PRESSURE, "CHANGE", 12);
        this.request (host, "pressure-2", PAD, InputKind.POLY_PRESSURE, "CHANGE", 48);
        this.request (host, "pressure-3", PAD, InputKind.POLY_PRESSURE, "CHANGE", 91);
        host.tick ();

        assertEquals (List.of ("push.pad.5:POLY_PRESSURE:CHANGE:91"), surface.events);
    }


    @Test
    void staleSessionIsRejectedAndExpiredHoldIsReleased () throws IOException
    {
        final AtomicLong time = new AtomicLong ();
        final FakeSurface surface = new FakeSurface ();
        final FakeAdmission admission = new FakeAdmission ();
        final PushDebugInputHost host = this.host (surface, admission, time);

        this.request ("stale", "stale", PLAY, InputKind.BUTTON, "BEGIN", 127);
        host.tick ();
        assertTrue (surface.events.isEmpty ());
        assertTrue (this.status ().contains ("session is stale"));

        this.request (host, "held", PAD, InputKind.PAD, "BEGIN", 127);
        host.tick ();
        time.set (TimeUnit.SECONDS.toNanos (6));
        host.tick ();

        assertEquals (List.of (
            "push.pad.5:PAD:BEGIN:127",
            "push.pad.5:PAD:END:0"), surface.events);
        assertFalse (admission.debugActive);
        assertTrue (this.status ().contains ("browser input lease expired"));
    }


    private PushDebugInputHost host (final FakeSurface surface, final FakeAdmission admission, final AtomicLong time)
    {
        final PushDebugInputHost host = new PushDebugInputHost (this.debugDirectory, surface, admission, time::get);
        host.tick ();
        return host;
    }


    private void request (final PushDebugInputHost host, final String requestID, final ControlId control, final InputKind kind, final String phase, final int value) throws IOException
    {
        this.request (host.sessionForTest (), requestID, control, kind, phase, value);
    }


    private void request (final String session, final String requestID, final ControlId control, final InputKind kind, final String phase, final int value) throws IOException
    {
        final Path directory = this.debugDirectory.resolve (PushDebugInputHost.REQUEST_DIRECTORY);
        Files.writeString (
            directory.resolve ("input-00000000000000000001-000001-" + requestID + ".txt"),
            String.join ("\t", session, requestID, control.value (), kind.name (), phase, Integer.toString (value)) + "\n");
    }


    private String status () throws IOException
    {
        return Files.readString (this.debugDirectory.resolve (PushDebugInputHost.STATUS_FILE));
    }


    private static final class FakeSurface implements PushDebugInputHost.InputSurface
    {
        private final List<String> events = new ArrayList<> ();
        private final Set<String> active = new HashSet<> ();


        @Override
        public boolean supports (final ControlId control, final InputKind kind)
        {
            return PLAY.equals (control) && kind == InputKind.BUTTON ||
                KNOB.equals (control) && kind == InputKind.TOUCH ||
                PAD.equals (control) && (kind == InputKind.PAD || kind == InputKind.POLY_PRESSURE);
        }


        @Override
        public boolean isActive (final ControlId control, final InputKind kind)
        {
            return this.active.contains (control.value () + ":" + kind.name ());
        }


        @Override
        public void trigger (final ControlId control, final InputKind kind, final InputPhase phase, final int value)
        {
            final String address = control.value () + ":" + kind.name ();
            if (phase == InputPhase.BEGIN)
                this.active.add (address);
            else if (phase == InputPhase.END)
                this.active.remove (address);
            this.events.add (address + ":" + phase.name () + ":" + value);
        }
    }


    private static final class FakeAdmission implements PushDebugNavigationHost.GestureAdmission
    {
        private boolean debugActive;
        private boolean routeIdle = true;


        @Override
        public boolean isIdle ()
        {
            return !this.debugActive;
        }


        @Override
        public boolean trySubmit (final Runnable gesture)
        {
            if (!this.isIdle ())
                return false;
            gesture.run ();
            return true;
        }


        @Override
        public boolean tryBeginDebugInput (final Runnable press)
        {
            if (!this.isIdle ())
                return false;
            this.debugActive = true;
            press.run ();
            return true;
        }


        @Override
        public void endDebugInput (final Runnable release)
        {
            assertTrue (this.debugActive);
            release.run ();
        }


        @Override
        public void completeDebugInput ()
        {
            this.debugActive = false;
        }


        @Override
        public boolean debugInputRouteIdle ()
        {
            return this.routeIdle;
        }
    }
}
