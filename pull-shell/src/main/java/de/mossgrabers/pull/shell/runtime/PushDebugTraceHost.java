// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.event.ControllerTickEvent;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.event.SnapshotChangedEvent;
import de.mossgrabers.pull.shell.PushDebugging;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongSupplier;
import java.util.stream.Stream;


/** Bounded, request-scoped reload transaction traces for the opt-in Push debugger. */
final class PushDebugTraceHost implements RuntimeTraceSink, AutoCloseable
{
    static final String REQUEST_FILE = "trace-request.txt";
    static final String STATUS_FILE  = "trace-status.txt";

    private static final int  MAX_REQUEST_BYTES       = 256;
    private static final int  MAX_CRITICAL_ENTRIES    = 256;
    private static final int  MAX_TRACE_CHARACTERS    = 2_000_000;
    private static final int  TRUNCATION_RESERVE      = 160;
    private static final int  MAX_TRACE_FILES         = 16;
    private static final long MIN_DURATION_MILLIS     = 1_000;
    private static final long MAX_DURATION_MILLIS     = 60_000;
    private static final long POLL_INTERVAL_MILLIS    = 100;

    private final Path                         directory;
    private final Path                         requestPath;
    private final Path                         statusPath;
    private final ScheduledExecutorService     worker;
    private final LongSupplier                 clock;
    private final AtomicReference<Incoming>    incoming = new AtomicReference<> ();
    private final AtomicReference<Status>      outgoingStatus = new AtomicReference<> ();
    private final AtomicReference<Artifact>    outgoingArtifact = new AtomicReference<> ();
    private final AtomicReference<CompletedTrace> completed = new AtomicReference<> ();
    private final AtomicBoolean                artifactPending = new AtomicBoolean ();
    private final AtomicBoolean                closed = new AtomicBoolean ();

    private ActiveTrace active;
    private boolean retainNextApply;
    private long lastGeneration;
    private ControllerSnapshot lastSnapshot;


    static PushDebugTraceHost createIfEnabled ()
    {
        if (!PushDebugging.isEnabled ())
            return null;

        final ScheduledExecutorService worker = PushDebugging.createWorker ("Pull debug trace transport");
        final PushDebugTraceHost host = new PushDebugTraceHost (PushDebugging.directory (), worker, System::nanoTime);
        worker.scheduleWithFixedDelay (host::pollFilesSafely, 0, POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        return host;
    }


    PushDebugTraceHost (final Path directory, final LongSupplier clock)
    {
        this (directory, null, clock);
    }


    /** Test whether a request or expired deadline needs one controller-thread snapshot. */
    boolean needsControllerTick ()
    {
        return this.incoming.get () != null || this.active != null && this.clock.getAsLong () >= this.active.deadlineNanos;
    }


    private PushDebugTraceHost (final Path directory, final ScheduledExecutorService worker, final LongSupplier clock)
    {
        this.directory = Objects.requireNonNull (directory, "directory");
        this.requestPath = directory.resolve (REQUEST_FILE);
        this.statusPath = directory.resolve (STATUS_FILE);
        this.worker = worker;
        this.clock = Objects.requireNonNull (clock, "clock");
    }


    /** Consume arm/stop requests and enforce the active trace's wall-clock bound. */
    void tick (final long generation, final ControllerSnapshot snapshot)
    {
        if (this.closed.get ())
            return;
        if (this.worker == null)
            this.pollFilesSafely ();

        this.lastGeneration = generation;
        this.lastSnapshot = Objects.requireNonNull (snapshot, "snapshot");
        final Incoming request = this.incoming.getAndSet (null);
        if (request != null)
            this.accept (request, generation, snapshot);

        final ActiveTrace trace = this.active;
        if (trace != null && this.clock.getAsLong () >= trace.deadlineNanos)
            this.finish (trace.traceID, trace.traceID, "TIMED_OUT", generation, snapshot);

        if (this.worker == null)
            this.pollFilesSafely ();
    }


    @Override
    public void transaction (final long generation, final CoreEvent event, final ControllerSnapshot snapshot, final CoreResult result)
    {
        final ActiveTrace trace = this.active;
        if (trace == null)
            return;
        final CoreEvent checkedEvent = Objects.requireNonNull (event, "event");
        final ControllerSnapshot checkedSnapshot = Objects.requireNonNull (snapshot, "snapshot");
        final CoreResult checkedResult = Objects.requireNonNull (result, "result");
        this.lastGeneration = generation;
        this.lastSnapshot = checkedSnapshot;

        final boolean telemetry = (checkedEvent instanceof ControllerTickEvent || checkedEvent instanceof SnapshotChangedEvent) && checkedResult.effects ().isEmpty () && trace.repeats (checkedResult);
        trace.remember (checkedResult);
        this.retainNextApply = !telemetry;
        final TraceEntry entry = new TraceEntry (this.clock.getAsLong (), generation, "TRANSACTION", checkedEvent, checkedSnapshot, checkedResult, "");
        if (telemetry)
            trace.coalesce (entry);
        else
            this.appendCritical (trace, entry, checkedSnapshot);
    }


    @Override
    public void startup (final long generation, final String buildID, final ControllerSnapshot snapshot, final CoreResult result)
    {
        final ActiveTrace trace = this.active;
        if (trace == null)
            return;
        trace.remember (result);
        this.retainNextApply = true;
        this.appendCritical (trace, new TraceEntry (this.clock.getAsLong (), generation, "STARTUP", buildID, snapshot, result, ""), snapshot);
    }


    @Override
    public void applied (final long generation)
    {
        final ActiveTrace trace = this.active;
        if (trace == null)
            return;
        if (!this.retainNextApply)
        {
            trace.completeTelemetry (this.clock.getAsLong (), generation);
            return;
        }
        this.retainNextApply = false;
        this.appendCritical (trace, new TraceEntry (this.clock.getAsLong (), generation, "APPLIED", null, null, null, "Complete stable result applied"), this.lastSnapshot);
    }


    @Override
    public void lifecycle (final long generation, final String state, final String detail)
    {
        final ActiveTrace trace = this.active;
        if (trace != null)
            this.appendCritical (trace, new TraceEntry (this.clock.getAsLong (), generation, Objects.requireNonNull (state, "state"), null, null, null, detail), this.lastSnapshot);
    }


    @Override
    public void failure (final long generation, final String stage, final String detail)
    {
        final ActiveTrace trace = this.active;
        if (trace != null)
            this.appendCritical (trace, new TraceEntry (this.clock.getAsLong (), generation, "FAILED_" + Objects.requireNonNull (stage, "stage"), null, null, null, Objects.requireNonNull (detail, "detail")), this.lastSnapshot);
    }


    private void appendCritical (final ActiveTrace trace, final TraceEntry entry, final ControllerSnapshot snapshot)
    {
        if (this.active != trace)
            return;
        if (trace.append (entry))
            return;
        this.finish (trace.traceID, trace.traceID, "OVERFLOW", entry.generation (), snapshot);
    }


    private void accept (final Incoming request, final long generation, final ControllerSnapshot snapshot)
    {
        if (!request.failure.isEmpty ())
        {
            this.publish (new Status (request.requestID, "FAILED", request.traceID, "", request.failure));
            return;
        }

        if (request.action == Action.ARM)
        {
            if (this.active != null)
            {
                this.publish (new Status (request.requestID, "FAILED", this.active.traceID, "", "another trace is already armed"));
                return;
            }
            if (this.artifactPending.get ())
            {
                this.publish (new Status (request.requestID, "FAILED", "", "", "the previous trace is still being serialized"));
                return;
            }
            final long now = this.clock.getAsLong ();
            this.active = new ActiveTrace (request.requestID, request.label, now, Math.addExact (now, TimeUnit.MILLISECONDS.toNanos (request.durationMillis)));
            this.completed.set (null);
            this.retainNextApply = false;
            this.active.append (new TraceEntry (now, generation, "ARMED", null, snapshot, null, "duration_ms=" + request.durationMillis));
            this.publish (new Status (request.requestID, "ARMED", request.requestID, "", ""));
            return;
        }

        if (this.active != null && this.active.traceID.equals (request.traceID))
        {
            this.finish (request.requestID, request.traceID, "STOPPED", generation, snapshot);
            return;
        }
        final CompletedTrace retained = this.completed.get ();
        if (retained != null && retained.traceID.equals (request.traceID))
        {
            this.publish (new Status (request.requestID, "READY", request.traceID, retained.filename, ""));
            return;
        }
        this.publish (new Status (request.requestID, "FAILED", request.traceID, "", "trace is not active or retained"));
    }


    private void finish (final String responseRequestID, final String traceID, final String outcome, final long generation, final ControllerSnapshot snapshot)
    {
        final ActiveTrace trace = this.active;
        if (trace == null || !trace.traceID.equals (traceID))
            return;
        trace.flushTelemetry ();
        trace.appendTerminal (new TraceEntry (this.clock.getAsLong (), generation, outcome, null, snapshot, null, ""));
        this.active = null;
        this.retainNextApply = false;
        this.artifactPending.set (true);
        this.outgoingArtifact.set (new Artifact (responseRequestID, trace.freeze (outcome)));
    }


    private void pollFilesSafely ()
    {
        if (this.closed.get () && this.outgoingArtifact.get () == null && this.outgoingStatus.get () == null)
            return;
        try
        {
            final Artifact artifact = this.outgoingArtifact.getAndSet (null);
            if (artifact != null)
            {
                try
                {
                    this.writeArtifact (artifact);
                }
                finally
                {
                    this.artifactPending.set (false);
                }
            }
            final Status status = this.outgoingStatus.getAndSet (null);
            if (status != null)
                this.writeStatus (status);
            if (!this.closed.get () && this.incoming.get () == null)
            {
                final Incoming request = this.readRequest ();
                if (request != null && !this.closed.get ())
                    this.incoming.compareAndSet (null, request);
            }
        }
        catch (final IOException | RuntimeException ignored)
        {
            // Trace transport failure cannot affect controller or core behavior.
        }
    }


    private Incoming readRequest () throws IOException
    {
        if (!Files.isRegularFile (this.requestPath, LinkOption.NOFOLLOW_LINKS))
            return null;
        final String text = Files.size (this.requestPath) <= MAX_REQUEST_BYTES ? Files.readString (this.requestPath).strip () : "";
        Files.deleteIfExists (this.requestPath);
        final String [] fields = text.split ("\\t", -1);
        final String requestID = fields.length == 0 || !PushDebugging.isIdentifier (fields[0]) ? "invalid" : fields[0];
        if (fields.length == 4 && PushDebugging.isIdentifier (fields[0]) && "ARM".equals (fields[1]) && PushDebugging.isIdentifier (fields[2]))
        {
            try
            {
                final long duration = Long.parseLong (fields[3]);
                if (duration >= MIN_DURATION_MILLIS && duration <= MAX_DURATION_MILLIS)
                    return Incoming.arm (fields[0], fields[2], duration);
            }
            catch (final NumberFormatException ignored)
            {
                // Report the common bounded validation failure below.
            }
        }
        else if (fields.length == 3 && PushDebugging.isIdentifier (fields[0]) && "STOP".equals (fields[1]) && PushDebugging.isIdentifier (fields[2]))
            return Incoming.stop (fields[0], fields[2]);
        return Incoming.failed (requestID, "invalid trace request");
    }


    private void writeArtifact (final Artifact artifact) throws IOException
    {
        Files.createDirectories (this.directory);
        final Trace trace = artifact.trace;
        final String filename = "trace-" + trace.traceID + ".tsv";
        final Path output = this.directory.resolve (filename);
        final Path temporary = this.directory.resolve (filename + ".tmp");
        Files.writeString (temporary, render (trace));
        PushDebugging.replaceAtomically (temporary, output);
        this.completed.set (new CompletedTrace (trace.traceID, filename));
        this.writeStatus (new Status (artifact.responseRequestID, "READY", trace.traceID, filename, ""));
        this.pruneTraces ();
    }


    private static String render (final Trace trace)
    {
        final BoundedTraceText output = new BoundedTraceText (MAX_TRACE_CHARACTERS - TRUNCATION_RESERVE);
        output.append ("trace_id\tlabel\toutcome\tcoalesced_telemetry\n")
            .append (trace.traceID).append ('\t').append (trace.label).append ('\t').append (trace.outcome).append ('\t').append (trace.coalescedTelemetry).append ('\n')
            .append ("index\telapsed_us\tgeneration\tstage\tevent\tsnapshot\tresult\tdetail\n");
        int index = 0;
        for (final TraceEntry entry: trace.entries)
        {
            output.append (index++).append ('\t')
                .append (TimeUnit.NANOSECONDS.toMicros (Math.max (0, entry.atNanos - trace.startedAtNanos))).append ('\t')
                .append (entry.generation).append ('\t').appendValue (entry.stage).append ('\t');
            appendNullable (output, entry.event);
            output.append ('\t');
            appendNullable (output, entry.snapshot);
            output.append ('\t');
            appendNullable (output, entry.result);
            output.append ('\t');
            appendNullable (output, entry.detail);
            output.append ('\n');
            if (output.truncated ())
                break;
        }
        if (!output.truncated ())
            return output.toString ();
        return output + "\n0\t0\t0\tSERIALIZATION_TRUNCATED\t\t\t\tTrace reached the bounded text limit\n";
    }


    private static void appendNullable (final BoundedTraceText output, final Object value)
    {
        if (value != null)
            output.appendValue (value);
    }


    private void pruneTraces () throws IOException
    {
        final List<Path> traces;
        try (Stream<Path> files = Files.list (this.directory))
        {
            traces = files.filter (path -> path.getFileName ().toString ().startsWith ("trace-") && path.getFileName ().toString ().endsWith (".tsv")).sorted ().toList ();
        }
        for (int index = 0; index < traces.size () - MAX_TRACE_FILES; index++)
            Files.deleteIfExists (traces.get (index));
    }


    private void publish (final Status status)
    {
        this.outgoingStatus.set (status);
    }


    private void writeStatus (final Status status) throws IOException
    {
        final String content = String.join ("\t", status.requestID, status.state, present (status.traceID), present (status.filename), present (status.message)) + "\n";
        final Path temporary = this.statusPath.resolveSibling (this.statusPath.getFileName () + ".tmp");
        Files.writeString (temporary, content);
        PushDebugging.replaceAtomically (temporary, this.statusPath);
    }


    @Override
    public void close ()
    {
        if (!this.closed.compareAndSet (false, true))
            return;
        if (this.active != null)
            this.finish (this.active.traceID, this.active.traceID, "EXTENSION_CLOSED", this.lastGeneration, this.lastSnapshot);
        final Incoming queued = this.incoming.getAndSet (null);
        if (queued != null)
            this.publish (new Status (queued.requestID, "FAILED", queued.traceID, "", "extension is closing"));
        if (this.worker == null)
        {
            this.pollFilesSafely ();
            return;
        }
        PushDebugging.shutdownWorker (this.worker, this::pollFilesSafely);
    }


    private static String present (final String value)
    {
        final String sanitized = PushDebugging.sanitize (value);
        return sanitized.isEmpty () ? "-" : sanitized;
    }


    private enum Action
    {
        ARM,
        STOP
    }


    private static final class ActiveTrace
    {
        private final String traceID;
        private final String label;
        private final long startedAtNanos;
        private final long deadlineNanos;
        private final List<TraceEntry> entries = new ArrayList<> (MAX_CRITICAL_ENTRIES + 2);
        private TraceEntry telemetry;
        private long coalescedTelemetry;
        private CoreResult lastResult;


        private ActiveTrace (final String traceID, final String label, final long startedAtNanos, final long deadlineNanos)
        {
            this.traceID = traceID;
            this.label = label;
            this.startedAtNanos = startedAtNanos;
            this.deadlineNanos = deadlineNanos;
        }


        private boolean append (final TraceEntry entry)
        {
            this.flushTelemetry ();
            if (this.entries.size () >= MAX_CRITICAL_ENTRIES)
                return false;
            this.entries.add (entry);
            return true;
        }


        private void appendTerminal (final TraceEntry entry)
        {
            this.entries.add (entry);
        }


        private void coalesce (final TraceEntry entry)
        {
            if (this.telemetry != null)
                this.coalescedTelemetry++;
            this.telemetry = entry;
        }


        private void completeTelemetry (final long atNanos, final long generation)
        {
            if (this.telemetry != null)
                this.telemetry = new TraceEntry (atNanos, generation, "TRANSACTION_APPLIED", this.telemetry.event, this.telemetry.snapshot, this.telemetry.result, "Complete stable result applied");
        }


        private boolean repeats (final CoreResult result)
        {
            return this.lastResult != null && this.lastResult.equals (result);
        }


        private void remember (final CoreResult result)
        {
            this.lastResult = Objects.requireNonNull (result, "result");
        }


        private void flushTelemetry ()
        {
            if (this.telemetry != null && this.entries.size () < MAX_CRITICAL_ENTRIES)
                this.entries.add (this.telemetry);
            this.telemetry = null;
        }


        private Trace freeze (final String outcome)
        {
            return new Trace (this.traceID, this.label, this.startedAtNanos, outcome, this.coalescedTelemetry, List.copyOf (this.entries));
        }
    }


    private record TraceEntry (long atNanos, long generation, String stage, Object event, ControllerSnapshot snapshot, CoreResult result, String detail)
    {
    }


    private record Trace (String traceID, String label, long startedAtNanos, String outcome, long coalescedTelemetry, List<TraceEntry> entries)
    {
    }


    private record Artifact (String responseRequestID, Trace trace)
    {
    }


    private record CompletedTrace (String traceID, String filename)
    {
    }


    private record Status (String requestID, String state, String traceID, String filename, String message)
    {
    }


    private record Incoming (String requestID, Action action, String traceID, String label, long durationMillis, String failure)
    {
        private static Incoming arm (final String requestID, final String label, final long durationMillis)
        {
            return new Incoming (requestID, Action.ARM, requestID, label, durationMillis, "");
        }


        private static Incoming stop (final String requestID, final String traceID)
        {
            return new Incoming (requestID, Action.STOP, traceID, "", 0, "");
        }


        private static Incoming failed (final String requestID, final String failure)
        {
            return new Incoming (requestID, Action.STOP, "", "", 0, failure);
        }
    }
}
