// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.shell.input.InputKind;
import de.mossgrabers.pull.shell.input.InputPhase;

import org.junit.jupiter.api.BeforeEach;
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
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Browser input requests must retain the permanent routed lifecycle and bounded cleanup. */
class PushDebugInputHostTest
{
    private static final ControlId PLAY = PushControlIds.button ("PLAY");
    private static final ControlId SHIFT = PushControlIds.button ("SHIFT");
    private static final ControlId MASTER = PushControlIds.button ("MASTERTRACK");
    private static final List<ControlId> ROW_BUTTONS = IntStream.rangeClosed (1, 8).mapToObj (index -> PushControlIds.button ("ROW2_" + index)).toList ();
    private static final ControlId ROW = ROW_BUTTONS.get (1);
    private static final ControlId KNOB = PushControlIds.continuous ("KNOB1");
    private static final ControlId STRIP = PushControlIds.continuous ("TOUCHSTRIP");
    private static final ControlId PAD = PushControlIds.pad (5);

    @TempDir
    Path debugDirectory;

    private AtomicLong time;
    private FakeSurface surface;
    private FakeAdmission admission;
    private PushDebugInputHost host;


    @BeforeEach
    void setUp ()
    {
        this.time = new AtomicLong ();
        this.surface = new FakeSurface ();
        this.admission = new FakeAdmission ();
        this.host = new PushDebugInputHost (this.debugDirectory, this.surface, this.admission, this.time::get);
        this.host.tick ();
    }


    @Test
    void buttonBeginAndEndShareOneAdmissionLease () throws IOException
    {
        this.request (this.host, "begin", PLAY, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();

        assertEquals (List.of ("push.button.play:BUTTON:BEGIN:127"), this.surface.events);
        assertTrue (this.admission.debugActive);
        assertTrue (this.status ().contains ("\"state\":\"APPLIED\""));

        this.admission.routeIdle = false;
        this.request (this.host, "end", PLAY, InputKind.BUTTON, "END", 0);
        this.host.tick ();

        assertEquals (List.of (
            "push.button.play:BUTTON:BEGIN:127",
            "push.button.play:BUTTON:END:0"), this.surface.events);
        assertTrue (this.admission.debugActive, "release waits for the routed lifecycle to become idle");

        this.admission.routeIdle = true;
        this.host.tick ();
        assertFalse (this.admission.debugActive);
    }


    @Test
    void multipleButtonsShareOneBoundedAdmissionLease () throws IOException
    {
        this.request (this.host, "shift-down", SHIFT, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();
        this.request (this.host, "play-down", PLAY, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();

        assertEquals (List.of (
            "push.button.shift:BUTTON:BEGIN:127",
            "push.button.play:BUTTON:BEGIN:127"), this.surface.events);
        assertTrue (this.admission.debugActive);

        this.admission.routeIdle = false;
        this.request (this.host, "play-up", PLAY, InputKind.BUTTON, "END", 0);
        this.host.tick ();
        this.request (this.host, "shift-up", SHIFT, InputKind.BUTTON, "END", 0);
        this.host.tick ();

        assertEquals (List.of (
            "push.button.shift:BUTTON:BEGIN:127",
            "push.button.play:BUTTON:BEGIN:127",
            "push.button.play:BUTTON:END:0",
            "push.button.shift:BUTTON:END:0"), this.surface.events);
        assertTrue (this.admission.debugActive);

        this.admission.routeIdle = true;
        this.host.tick ();
        assertFalse (this.admission.debugActive);
    }


    @Test
    void releasedRowCanRepeatWhileMasterRemainsHeld () throws IOException
    {
        this.request (this.host, "master-down", MASTER, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();
        this.admission.routeIdle = false;
        for (int press = 0; press < 2; press++)
        {
            this.request (this.host, "row-down-" + press, ROW, InputKind.BUTTON, "BEGIN", 127);
            this.host.tick ();
            assertTrue (this.status ().contains ("\"state\":\"APPLIED\""));
            this.request (this.host, "row-up-" + press, ROW, InputKind.BUTTON, "END", 0);
            this.host.tick ();
            assertFalse (this.surface.isActive (ROW, InputKind.BUTTON));
            assertTrue (this.surface.isActive (MASTER, InputKind.BUTTON));
            assertTrue (this.admission.debugActive);
        }
        this.request (this.host, "duplicate-row-up", ROW, InputKind.BUTTON, "END", 0);
        this.host.tick ();
        assertTrue (this.status ().contains ("no matching browser input is held"));
        this.request (this.host, "released-row-keepalive", ROW, InputKind.BUTTON, "KEEPALIVE", 0);
        this.host.tick ();
        assertTrue (this.status ().contains ("no matching browser input is held"));

        this.request (this.host, "master-up", MASTER, InputKind.BUTTON, "END", 0);
        this.host.tick ();
        assertEquals (List.of (
            "push.button.mastertrack:BUTTON:BEGIN:127",
            "push.button.row2-2:BUTTON:BEGIN:127",
            "push.button.row2-2:BUTTON:END:0",
            "push.button.row2-2:BUTTON:BEGIN:127",
            "push.button.row2-2:BUTTON:END:0",
            "push.button.mastertrack:BUTTON:END:0"), this.surface.events);
        assertEquals (1, this.admission.beginCount);
        assertEquals (2, this.admission.extensionCount);
        assertEquals (0, this.admission.completionCount, "the final release still waits for routed idle");
        this.admission.routeIdle = true;
        this.host.tick ();
        assertEquals (1, this.admission.completionCount);
        assertFalse (this.admission.debugActive);
    }


    @Test
    void repeatedEdgeExtendsAdmissionWhileEarlierReleaseAwaitsRoutedIdle () throws IOException
    {
        this.request (this.host, "down", ROW, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();
        this.admission.routeIdle = false;
        this.request (this.host, "up", ROW, InputKind.BUTTON, "END", 0);
        this.host.tick ();
        this.request (this.host, "repeat-down", ROW, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();

        assertTrue (this.status ().contains ("\"state\":\"APPLIED\""));
        assertEquals (1, this.admission.beginCount);
        assertEquals (1, this.admission.extensionCount);
        assertEquals (0, this.admission.completionCount);
        this.request (this.host, "repeat-up", ROW, InputKind.BUTTON, "END", 0);
        this.host.tick ();
        this.admission.routeIdle = true;
        this.host.tick ();
        assertEquals (1, this.admission.completionCount);
    }


    @Test
    void expiredRowCanRepeatWhileMasterLeaseIsRenewed () throws IOException
    {
        this.request (this.host, "master-down", MASTER, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();
        this.request (this.host, "row-down", ROW, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();
        this.admission.routeIdle = false;
        this.time.set (TimeUnit.SECONDS.toNanos (4));
        this.request (this.host, "master-keepalive", MASTER, InputKind.BUTTON, "KEEPALIVE", 0);
        this.host.tick ();
        this.time.set (TimeUnit.SECONDS.toNanos (6));
        this.host.tick ();

        assertFalse (this.surface.isActive (ROW, InputKind.BUTTON));
        assertTrue (this.surface.isActive (MASTER, InputKind.BUTTON));
        this.request (this.host, "repeat-row-down", ROW, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();
        assertTrue (this.status ().contains ("\"state\":\"APPLIED\""));
        assertEquals (1, this.admission.beginCount);
        assertEquals (2, this.admission.extensionCount);
        assertEquals (0, this.admission.completionCount);
    }


    @Test
    void releasedEdgesDoNotConsumeTheConcurrentChordCapacity () throws IOException
    {
        this.request (this.host, "master-down", MASTER, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();
        for (int index = 0; index < ROW_BUTTONS.size (); index++)
        {
            this.request (this.host, "row-down-" + index, ROW_BUTTONS.get (index), InputKind.BUTTON, "BEGIN", 127);
            this.host.tick ();
            assertTrue (this.status ().contains ("\"state\":\"APPLIED\""));
            this.request (this.host, "row-up-" + index, ROW_BUTTONS.get (index), InputKind.BUTTON, "END", 0);
            this.host.tick ();
        }
        for (int index = 0; index < ROW_BUTTONS.size (); index++)
        {
            this.request (this.host, "held-row-" + index, ROW_BUTTONS.get (index), InputKind.BUTTON, "BEGIN", 127);
            this.host.tick ();
        }
        assertTrue (this.status ().contains ("too many browser inputs are held"));
        assertEquals (8, this.surface.active.size (), "the eight-edge limit still applies to concurrent holds");
    }


    @Test
    void failedReleaseCleansOnlyStillOwnedEdges () throws IOException
    {
        this.request (this.host, "master-down", MASTER, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();
        this.request (this.host, "row-down", ROW, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();
        this.request (this.host, "row-up", ROW, InputKind.BUTTON, "END", 0);
        this.host.tick ();
        this.request (this.host, "play-down", PLAY, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();
        this.surface.failNextEnd = PLAY;
        this.request (this.host, "play-up", PLAY, InputKind.BUTTON, "END", 0);
        this.host.tick ();

        assertTrue (this.status ().contains ("could not end input"));
        assertEquals (List.of (
            "push.button.mastertrack:BUTTON:BEGIN:127",
            "push.button.row2-2:BUTTON:BEGIN:127",
            "push.button.row2-2:BUTTON:END:0",
            "push.button.play:BUTTON:BEGIN:127",
            "push.button.play:BUTTON:END:0",
            "push.button.mastertrack:BUTTON:END:0"), this.surface.events);
        assertTrue (this.surface.active.isEmpty ());
        assertFalse (this.admission.debugActive);
        assertEquals (1, this.admission.completionCount);
    }


    @Test
    void cancelRetainsAnAdmissionUntilTheReleasedRouteIsIdle () throws IOException
    {
        this.request (this.host, "down", ROW, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();
        this.admission.routeIdle = false;
        this.request (this.host, "up", ROW, InputKind.BUTTON, "END", 0);
        this.host.tick ();
        this.host.cancelActive ("route invalidated");
        this.host.tick ();

        assertEquals (2, this.surface.events.size (), "the completed edge must not receive a duplicate release");
        assertTrue (this.admission.debugActive);
        assertEquals (0, this.admission.completionCount);
        this.admission.routeIdle = true;
        this.host.tick ();
        assertFalse (this.admission.debugActive);
        assertEquals (1, this.admission.completionCount);
    }


    @Test
    void pressureNeutralizationCanReenterCancellationWithoutDuplicatingRelease () throws IOException
    {
        this.request (this.host, "pad-down", PAD, InputKind.PAD, "BEGIN", 100);
        this.host.tick ();
        this.request (this.host, "pressure", PAD, InputKind.POLY_PRESSURE, "CHANGE", 91);
        this.host.tick ();
        this.surface.onNeutralPressure = () -> this.host.cancelActive ("route invalidated by pressure callback");
        this.request (this.host, "pad-up", PAD, InputKind.PAD, "END", 0);
        this.host.tick ();

        assertEquals (List.of (
            "push.pad.5:PAD:BEGIN:100",
            "push.pad.5:POLY_PRESSURE:CHANGE:91",
            "push.pad.5:POLY_PRESSURE:CHANGE:0",
            "push.pad.5:PAD:END:0"), this.surface.events);
        assertEquals (List.of (
            "push.pad.5:PAD:BEGIN:100", "push.pad.5:POLY_PRESSURE:CHANGE:91",
            "push.pad.5:PAD:END:0", "push.pad.5:POLY_PRESSURE:CHANGE:0"), this.surface.noteInputEvents);
        assertTrue (this.surface.active.isEmpty ());
        assertFalse (this.admission.debugActive);
        assertEquals (1, this.admission.completionCount);
    }


    @Test
    void midiNeutralizationRetiresRawResourcesWithoutReleasingBrowserGestures () throws IOException
    {
        this.request (this.host, "knob-down", KNOB, InputKind.TOUCH, "BEGIN", 127);
        this.host.tick ();
        this.request (this.host, "pad-down", PAD, InputKind.PAD, "BEGIN", 100);
        this.host.tick ();
        this.request (this.host, "pressure", PAD, InputKind.POLY_PRESSURE, "CHANGE", 91);
        this.host.tick ();
        final List<String> physicalBefore = List.copyOf (this.surface.events);

        this.host.neutralizeNoteInput ();
        this.host.neutralizeNoteInput ();

        assertEquals (physicalBefore, this.surface.events, "target loss does not manufacture physical input");
        final List<String> retiredMidi = List.of (
            "push.pad.5:PAD:BEGIN:100", "push.pad.5:POLY_PRESSURE:CHANGE:91",
            "push.pad.5:POLY_PRESSURE:CHANGE:0", "push.pad.5:PAD:END:0");
        assertEquals (retiredMidi, this.surface.noteInputEvents);
        for (final InputKind kind: List.of (InputKind.TOUCH, InputKind.PAD))
        {
            this.request (this.host, "keep-" + kind, kind == InputKind.TOUCH ? KNOB : PAD, kind, "KEEPALIVE", 0);
            this.host.tick ();
            assertTrue (this.status ().contains ("\"state\":\"APPLIED\""));
        }
        this.request (this.host, "tail-turn", KNOB, InputKind.RELATIVE, "CHANGE", 2);
        this.host.tick ();
        this.request (this.host, "tail-pressure", PAD, InputKind.POLY_PRESSURE, "CHANGE", 52);
        this.host.tick ();
        assertEquals (retiredMidi, this.surface.noteInputEvents, "held raw pressure cannot retarget after neutralization");
        assertTrue (this.surface.events.contains ("push.continuous.knob1:RELATIVE:CHANGE:2"));
        assertTrue (this.surface.events.contains ("push.pad.5:POLY_PRESSURE:CHANGE:52"), "core must receive and cancel the real physical tail");
        this.request (this.host, "pad-up", PAD, InputKind.PAD, "END", 0);
        this.host.tick ();
        this.request (this.host, "knob-up", KNOB, InputKind.TOUCH, "END", 0);
        this.host.tick ();
        assertEquals (retiredMidi, this.surface.noteInputEvents, "physical END cannot repeat the retired native note-off");
        assertTrue (this.surface.active.isEmpty ());
        assertFalse (this.admission.debugActive);

        this.request (this.host, "fresh-pad-down", PAD, InputKind.PAD, "BEGIN", 80);
        this.host.tick ();
        this.host.cancelActive ("core invalidated");
        assertEquals (List.of ("push.pad.5:PAD:BEGIN:80", "push.pad.5:PAD:END:0"), this.surface.noteInputEvents.subList (4, 6));
        assertTrue (this.surface.active.isEmpty (), "terminal invalidation still releases the physical edge");
    }


    @Test
    void targetLossDuringPadBeginCannotSubmitANoteAfterNeutralization () throws IOException
    {
        this.surface.onBegin = this.host::neutralizeNoteInput;
        this.request (this.host, "pad-down", PAD, InputKind.PAD, "BEGIN", 100);
        this.host.tick ();
        assertTrue (this.surface.isActive (PAD, InputKind.PAD));
        assertTrue (this.surface.noteInputEvents.isEmpty (), "synchronous target loss preceded the raw note-on");
        this.request (this.host, "pad-up", PAD, InputKind.PAD, "END", 0);
        this.host.tick ();
        assertTrue (this.surface.noteInputEvents.isEmpty (), "there was no raw note-on to clean up");
        assertFalse (this.admission.debugActive);
    }


    @Test
    void failedBrowserAdmissionDoesNotReleaseAPhysicallyHeldControl () throws IOException
    {
        this.surface.active.add (ROW.value () + ":BUTTON");
        this.request (this.host, "physical-row-down", ROW, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();
        assertTrue (this.status ().contains ("that physical or browser input is already held"));
        this.host.cancelActive ("route invalidated");

        assertTrue (this.surface.isActive (ROW, InputKind.BUTTON));
        assertTrue (this.surface.events.isEmpty ());
        assertEquals (0, this.admission.beginCount);
        assertEquals (0, this.admission.completionCount);
    }


    @Test
    void touchAndPressureUseTheirInstalledInputKinds () throws IOException
    {
        this.request (this.host, "touch-down", KNOB, InputKind.TOUCH, "BEGIN", 127);
        this.host.tick ();
        this.request (this.host, "touch-up", KNOB, InputKind.TOUCH, "END", 0);
        this.host.tick ();

        this.request (this.host, "pad-down", PAD, InputKind.PAD, "BEGIN", 100);
        this.host.tick ();
        this.request (this.host, "pressure", PAD, InputKind.POLY_PRESSURE, "CHANGE", 91);
        this.host.tick ();
        this.request (this.host, "pad-up", PAD, InputKind.PAD, "END", 0);
        this.host.tick ();

        assertEquals (List.of (
            "push.continuous.knob1:TOUCH:BEGIN:127",
            "push.continuous.knob1:TOUCH:END:0",
            "push.pad.5:PAD:BEGIN:100",
            "push.pad.5:POLY_PRESSURE:CHANGE:91",
            "push.pad.5:POLY_PRESSURE:CHANGE:0",
            "push.pad.5:PAD:END:0"), this.surface.events);
        assertEquals (List.of (
            "push.pad.5:PAD:BEGIN:100",
            "push.pad.5:POLY_PRESSURE:CHANGE:91",
            "push.pad.5:POLY_PRESSURE:CHANGE:0",
            "push.pad.5:PAD:END:0"), this.surface.noteInputEvents);
        assertFalse (this.admission.debugActive);
    }


    @Test
    void pressureUpdatesAreCoalescedToTheLatestValuePerTick () throws IOException
    {
        this.request (this.host, "pressure-1", PAD, InputKind.POLY_PRESSURE, "CHANGE", 12);
        this.request (this.host, "pressure-2", PAD, InputKind.POLY_PRESSURE, "CHANGE", 48);
        this.request (this.host, "pressure-3", PAD, InputKind.POLY_PRESSURE, "CHANGE", 91);
        this.host.tick ();

        assertEquals (List.of ("push.pad.5:POLY_PRESSURE:CHANGE:91"), this.surface.events);
        assertEquals (List.of ("push.pad.5:POLY_PRESSURE:CHANGE:91"), this.surface.noteInputEvents);
    }


    @Test
    void absoluteStripMotionRequiresItsTouchLeaseAndPreservesAllFourteenBits () throws IOException
    {
        this.request (this.host, "orphan", STRIP, InputKind.ABSOLUTE, "CHANGE", 9000);
        this.host.tick ();
        assertTrue (this.status ().contains ("\"state\":\"FAILED\""));
        assertTrue (this.surface.events.isEmpty ());

        this.request (this.host, "touch", STRIP, InputKind.TOUCH, "BEGIN", 127);
        this.host.tick ();
        this.request (this.host, "position", STRIP, InputKind.ABSOLUTE, "CHANGE", 12345);
        this.host.tick ();
        assertTrue (this.status ().contains ("\"state\":\"APPLIED\""));
        this.request (this.host, "release", STRIP, InputKind.TOUCH, "END", 0);
        this.host.tick ();

        assertEquals (List.of (
            "push.continuous.touchstrip:TOUCH:BEGIN:127",
            "push.continuous.touchstrip:ABSOLUTE:CHANGE:12345",
            "push.continuous.touchstrip:TOUCH:END:0"), this.surface.events);
        assertTrue (this.surface.noteInputEvents.isEmpty (), "core effects own musical pitch; the debugger must not add a parallel MIDI write");
    }


    @Test
    void absoluteStripRangeAndExpiredTouchFailClosed () throws IOException
    {
        this.request (this.host, "touch", STRIP, InputKind.TOUCH, "BEGIN", 127);
        this.host.tick ();
        this.request (this.host, "invalid", STRIP, InputKind.ABSOLUTE, "CHANGE", 16384);
        this.host.tick ();
        assertTrue (this.status ().contains ("\"state\":\"FAILED\""));
        this.time.set (TimeUnit.SECONDS.toNanos (6));
        this.host.tick ();
        assertEquals ("push.continuous.touchstrip:TOUCH:END:0", this.surface.events.getLast ());
        assertFalse (this.admission.debugActive);
    }


    @Test
    void relativeTurnsAreSummedInsideTheMatchingTouchLease () throws IOException
    {
        this.request (this.host, "touch-down", KNOB, InputKind.TOUCH, "BEGIN", 127);
        this.host.tick ();
        this.request (this.host, "relative-1", KNOB, InputKind.RELATIVE, "CHANGE", 4);
        this.request (this.host, "relative-2", KNOB, InputKind.RELATIVE, "CHANGE", -3);
        this.host.tick ();
        this.request (this.host, "touch-up", KNOB, InputKind.TOUCH, "END", 0);
        this.host.tick ();

        assertEquals (List.of (
            "push.continuous.knob1:TOUCH:BEGIN:127",
            "push.continuous.knob1:RELATIVE:CHANGE:1",
            "push.continuous.knob1:TOUCH:END:0"), this.surface.events);
        assertTrue (this.surface.noteInputEvents.isEmpty ());
        assertFalse (this.admission.debugActive);
    }


    @Test
    void detachedPressureExpiresToARealNoteInputNeutralization () throws IOException
    {
        this.request (this.host, "pressure", PAD, InputKind.POLY_PRESSURE, "CHANGE", 91);
        this.host.tick ();
        this.time.set (TimeUnit.SECONDS.toNanos (6));
        this.host.tick ();

        assertEquals (List.of (
            "push.pad.5:POLY_PRESSURE:CHANGE:91",
            "push.pad.5:POLY_PRESSURE:CHANGE:0"), this.surface.events);
        assertEquals (this.surface.events, this.surface.noteInputEvents);
    }


    @Test
    void heldPressureLivesForTheRenewedPadLease () throws IOException
    {
        this.request (this.host, "pad-down", PAD, InputKind.PAD, "BEGIN", 100);
        this.host.tick ();
        this.request (this.host, "pressure", PAD, InputKind.POLY_PRESSURE, "CHANGE", 91);
        this.host.tick ();

        this.time.set (TimeUnit.SECONDS.toNanos (4));
        this.request (this.host, "keepalive", PAD, InputKind.PAD, "KEEPALIVE", 0);
        this.host.tick ();
        this.time.set (TimeUnit.SECONDS.toNanos (8));
        this.host.tick ();

        assertEquals (List.of (
            "push.pad.5:PAD:BEGIN:100",
            "push.pad.5:POLY_PRESSURE:CHANGE:91"), this.surface.events);

        this.request (this.host, "pad-up", PAD, InputKind.PAD, "END", 0);
        this.host.tick ();
        assertEquals (List.of (
            "push.pad.5:PAD:BEGIN:100",
            "push.pad.5:POLY_PRESSURE:CHANGE:91",
            "push.pad.5:POLY_PRESSURE:CHANGE:0",
            "push.pad.5:PAD:END:0"), this.surface.events);
        assertEquals (this.surface.events, this.surface.noteInputEvents);
    }


    @Test
    void staleSessionIsRejectedAndExpiredHoldIsReleased () throws IOException
    {
        this.request ("stale", "stale", PLAY, InputKind.BUTTON, "BEGIN", 127);
        this.host.tick ();
        assertTrue (this.surface.events.isEmpty ());
        assertTrue (this.status ().contains ("session is stale"));

        this.request (this.host, "held", PAD, InputKind.PAD, "BEGIN", 127);
        this.host.tick ();
        this.time.set (TimeUnit.SECONDS.toNanos (6));
        this.host.tick ();

        assertEquals (List.of (
            "push.pad.5:PAD:BEGIN:127",
            "push.pad.5:PAD:END:0"), this.surface.events);
        assertEquals (this.surface.events, this.surface.noteInputEvents);
        assertFalse (this.admission.debugActive);
        assertTrue (this.status ().contains ("browser input lease expired"));
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
        private final List<String> noteInputEvents = new ArrayList<> ();
        private final Set<String> active = new HashSet<> ();
        private ControlId failNextEnd;
        private Runnable onNeutralPressure = () -> { };
        private Runnable onBegin = () -> { };


        @Override
        public boolean supports (final ControlId control, final InputKind kind)
        {
            return (PLAY.equals (control) || SHIFT.equals (control) || MASTER.equals (control) || ROW_BUTTONS.contains (control)) && kind == InputKind.BUTTON ||
                KNOB.equals (control) && (kind == InputKind.TOUCH || kind == InputKind.RELATIVE) ||
                STRIP.equals (control) && (kind == InputKind.TOUCH || kind == InputKind.ABSOLUTE) ||
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
            if (phase == InputPhase.BEGIN)
                this.onBegin.run ();
            if (kind == InputKind.POLY_PRESSURE && value == 0)
                this.onNeutralPressure.run ();
            // Hardware state and routed ownership are updated before a downstream callback can fail.
            if (phase == InputPhase.END && control.equals (this.failNextEnd))
            {
                this.failNextEnd = null;
                throw new IllegalStateException ("test release failed");
            }
        }


        @Override
        public void triggerNoteInput (final ControlId control, final InputKind kind, final InputPhase phase, final int value)
        {
            this.noteInputEvents.add (control.value () + ":" + kind.name () + ":" + phase.name () + ":" + value);
        }
    }


    private static final class FakeAdmission implements PushDebugNavigationHost.GestureAdmission
    {
        private boolean debugActive;
        private boolean routeIdle = true;
        private int beginCount;
        private int extensionCount;
        private int completionCount;


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
            this.beginCount++;
            press.run ();
            return true;
        }


        @Override
        public boolean tryExtendDebugInput (final Runnable press)
        {
            if (!this.debugActive)
                return false;
            this.extensionCount++;
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
            this.completionCount++;
        }


        @Override
        public boolean debugInputRouteIdle ()
        {
            return this.routeIdle;
        }
    }
}
