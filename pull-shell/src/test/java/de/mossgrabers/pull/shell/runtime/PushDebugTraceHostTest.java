// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ClipCatalogSnapshot;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.ShellCapabilities;
import de.mossgrabers.pull.core.api.event.ButtonInputEvent;
import de.mossgrabers.pull.core.api.event.ControllerTickEvent;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.core.api.output.DesiredHardwareOutput;
import de.mossgrabers.pull.core.api.output.RgbColor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Bounded transport and retention contracts for request-scoped Push traces. */
class PushDebugTraceHostTest
{
    @TempDir
    Path debugDirectory;


    @Test
    void retainsSemanticTransactionsAndCoalescesEmptyTelemetry () throws IOException
    {
        final AtomicLong clock = new AtomicLong (1);
        final PushDebugTraceHost host = new PushDebugTraceHost (this.debugDirectory, clock::get);
        this.arm ("trace-one", "button-test", 15_000);

        host.tick (7, snapshot (4));
        assertEquals ("ARMED", this.status ()[1]);
        host.startup (7, "build-7", snapshot (4), CoreResult.empty ());
        host.applied (7);

        host.transaction (7, new ControllerTickEvent (1, 1), snapshot (5), CoreResult.empty ());
        host.transaction (7, new SnapshotChangedEvent (2, 2), snapshot (6), CoreResult.empty ());
        host.transaction (7, new ButtonInputEvent (3, 3, new ControlId ("play"), true), snapshot (7), CoreResult.empty ());
        host.applied (7);
        this.stop ("stop-one", "trace-one");
        host.tick (7, snapshot (8));

        final String trace = Files.readString (this.artifact ());
        assertTrue (trace.contains ("trace-one\tbutton-test\tSTOPPED\t1"));
        assertTrue (trace.contains ("SnapshotChangedEvent[sequence=2"), "the newest empty telemetry sample is retained");
        assertFalse (trace.contains ("ControllerTickEvent[sequence=1"), "superseded empty telemetry is not retained");
        assertTrue (trace.contains ("ButtonInputEvent[sequence=3"), "semantic input is retained exactly");
        assertTrue (trace.contains ("\tAPPLIED\t"));
        assertTrue (trace.contains ("\tSTOPPED\t"));
    }


    @Test
    void noEffectSnapshotResultsRemainExactWhenReplayableOutputChanges () throws IOException
    {
        final PushDebugTraceHost host = new PushDebugTraceHost (this.debugDirectory, () -> 1);
        this.arm ("output-change", "output-test", 15_000);
        host.tick (3, snapshot (1));
        host.startup (3, "build-3", snapshot (1), CoreResult.empty ());

        host.transaction (3, new SnapshotChangedEvent (1, 1), snapshot (2), withLight (new RgbColor (255, 0, 0)));
        host.applied (3);
        host.transaction (3, new SnapshotChangedEvent (2, 2), snapshot (3), withLight (new RgbColor (0, 0, 255)));
        host.applied (3);
        this.stop ("output-stop", "output-change");
        host.tick (3, snapshot (4));

        final String trace = Files.readString (this.artifact ());
        assertEquals (2, trace.lines ().filter (line -> line.contains ("\tTRANSACTION\t")).count ());
        assertTrue (trace.contains ("RgbColor[red=255, green=0, blue=0]"));
        assertTrue (trace.contains ("RgbColor[red=0, green=0, blue=255]"));
        assertEquals (2, trace.lines ().filter (line -> line.contains ("\tAPPLIED\t")).count ());
    }


    @Test
    void stopsAtTheBoundWithoutSamplingSemanticEvents () throws IOException
    {
        final AtomicLong clock = new AtomicLong (1);
        final PushDebugTraceHost host = new PushDebugTraceHost (this.debugDirectory, clock::get);
        this.arm ("bounded", "overflow-test", 60_000);
        host.tick (1, snapshot (1));

        for (int index = 0; index < 300; index++)
            host.transaction (1, new ButtonInputEvent (index, index, new ControlId ("button-" + index), true), snapshot (index + 2), CoreResult.empty ());
        host.tick (1, snapshot (400));

        final String trace = Files.readString (this.artifact ());
        assertTrue (trace.contains ("\toverflow-test\tOVERFLOW\t"));
        assertTrue (trace.contains ("button-254"), "all semantic events fit until the hard entry bound");
        assertFalse (trace.contains ("button-299"), "capture stops instead of sampling beyond the bound");
        assertTrue (trace.contains ("\tOVERFLOW\t"));
    }


    @Test
    void timeoutCanBeRetrievedByALaterCorrelatedStop () throws IOException
    {
        final AtomicLong clock = new AtomicLong (1);
        final PushDebugTraceHost host = new PushDebugTraceHost (this.debugDirectory, clock::get);
        this.arm ("timed", "timeout-test", 1_000);
        host.tick (2, snapshot (1));
        assertFalse (host.needsControllerTick (), "an armed trace does not request unconditional snapshots");

        clock.addAndGet (1_000_000_000L);
        assertTrue (host.needsControllerTick (), "the deadline requests one terminal snapshot");
        host.tick (2, snapshot (2));
        assertEquals ("READY", this.status ()[1]);

        this.stop ("retrieve", "timed");
        host.tick (2, snapshot (3));

        final String [] status = this.status ();
        assertEquals ("retrieve", status[0]);
        assertEquals ("READY", status[1]);
        assertTrue (Files.readString (this.debugDirectory.resolve (status[3])).contains ("\ttimeout-test\tTIMED_OUT\t"));
    }


    @Test
    void invalidAndConcurrentRequestsFailClosed () throws IOException
    {
        final PushDebugTraceHost host = new PushDebugTraceHost (this.debugDirectory, () -> 1);
        Files.writeString (this.debugDirectory.resolve (PushDebugTraceHost.REQUEST_FILE), "bad\tARM\tlabel\t999999\n");
        host.tick (1, snapshot (1));
        assertEquals ("FAILED", this.status ()[1]);

        this.arm ("first", "first", 10_000);
        host.tick (1, snapshot (2));
        this.arm ("second", "second", 10_000);
        host.tick (1, snapshot (3));

        final String [] status = this.status ();
        assertEquals ("second", status[0]);
        assertEquals ("FAILED", status[1]);
        assertEquals ("first", status[2]);
    }


    @Test
    void structuralTextStopsBeforeCallingAnUnboundedToString ()
    {
        final BoundedTraceText text = new BoundedTraceText (1_024);
        text.appendValue (new CharSequence ()
        {
            @Override
            public int length ()
            {
                return 5_000_000;
            }


            @Override
            public char charAt (final int index)
            {
                return 'x';
            }


            @Override
            public CharSequence subSequence (final int start, final int end)
            {
                throw new UnsupportedOperationException ();
            }


            @Override
            public String toString ()
            {
                throw new AssertionError ("unbounded toString must not be called");
            }
        });

        assertTrue (text.truncated ());
        assertEquals (1_024, text.toString ().length ());
    }


    private void arm (final String requestID, final String label, final long durationMillis) throws IOException
    {
        Files.writeString (this.debugDirectory.resolve (PushDebugTraceHost.REQUEST_FILE), String.join ("\t", requestID, "ARM", label, Long.toString (durationMillis)) + "\n");
    }


    private void stop (final String requestID, final String traceID) throws IOException
    {
        Files.writeString (this.debugDirectory.resolve (PushDebugTraceHost.REQUEST_FILE), String.join ("\t", requestID, "STOP", traceID) + "\n");
    }


    private String [] status () throws IOException
    {
        return Files.readString (this.debugDirectory.resolve (PushDebugTraceHost.STATUS_FILE)).strip ().split ("\t", -1);
    }


    private Path artifact () throws IOException
    {
        final String [] status = this.status ();
        assertEquals ("READY", status[1]);
        return this.debugDirectory.resolve (status[3]);
    }


    private static ControllerSnapshot snapshot (final long revision)
    {
        return new ControllerSnapshot (revision, revision, ShellCapabilities.empty (), ClipCatalogSnapshot.empty (), Map.of (), Set.of (), Set.of ());
    }


    private static CoreResult withLight (final RgbColor color)
    {
        final CoreResult empty = CoreResult.empty ();
        return new CoreResult (
            new DesiredHardwareOutput (Map.of (new ControlId ("test-light"), color)),
            empty.desiredInputRoutes (),
            empty.desiredBridgeSubscriptions (),
            empty.desiredClipBindings (),
            empty.desiredControllerState (),
            empty.desiredNoteRepeat (),
            empty.desiredControllerActions (),
            empty.desiredParameterBanks (),
            empty.desiredParameterInteraction (),
            empty.executionRequirements (),
            empty.effects ());
    }
}
