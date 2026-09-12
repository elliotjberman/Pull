package de.mossgrabers.pull.shell;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Opt-in, bounded selection diagnostics. File I/O stays on the worker. */
public final class SelectionDebug implements AutoCloseable
{
    private static volatile SelectionDebug active;
    private final ScheduledExecutorService worker;
    private final Path directory;
    private final java.util.function.LongSupplier clock;
    private final ArrayBlockingQueue<String> entries = new ArrayBlockingQueue<> (4096);
    private volatile long deadline;
    private volatile boolean paused;
    private volatile boolean closed;
    private String requestId = "";
    private String mode = "OFF";
    private final java.util.concurrent.atomic.AtomicInteger dropped = new java.util.concurrent.atomic.AtomicInteger ();
    private boolean firstPoll = true;
    private final StringBuilder output = new StringBuilder ();

    private SelectionDebug ()
    {
        this (PushDebugging.directory (), System::nanoTime, PushDebugging.createWorker ("Pull selection diagnostics"));
        this.worker.scheduleWithFixedDelay (this::poll, 0, 100, TimeUnit.MILLISECONDS);
    }

    SelectionDebug (final Path directory, final java.util.function.LongSupplier clock, final ScheduledExecutorService worker)
    {
        this.directory = directory;
        this.clock = clock;
        this.worker = worker;
    }

    boolean isRecording ()
    {
        return !this.closed && this.clock.getAsLong () < this.deadline;
    }

    boolean isScannerPaused ()
    {
        return this.paused && this.isRecording ();
    }

    public static SelectionDebug createIfEnabled ()
    {
        if (!PushDebugging.isEnabled ()) return null;
        final SelectionDebug result = new SelectionDebug ();
        active = result;
        return result;
    }

    public static boolean recording ()
    {
        final SelectionDebug debug = active;
        return debug != null && debug.isRecording ();
    }

    public static boolean scannerPaused ()
    {
        final SelectionDebug debug = active;
        return debug != null && debug.isScannerPaused ();
    }

    public static void record (final String kind, final String detail)
    {
        final SelectionDebug debug = active;
        if (debug == null || !recording ()) return;
        if (!debug.entries.offer (System.currentTimeMillis () + "\t" + System.nanoTime () + "\t" + kind + "\t" + PushDebugging.sanitize (detail)))
            debug.dropped.incrementAndGet ();
    }

    void poll ()
    {
        try
        {
            final Path directory = this.directory;
            final Path request = directory.resolve ("selection-request.txt");
            if (this.firstPoll)
            {
                Files.deleteIfExists (request);
                this.firstPoll = false;
            }
            if (!this.closed && Files.isRegularFile (request) && Files.size (request) <= 256)
            {
                final String[] fields = Files.readString (request).strip ().split ("\t");
                if (fields.length == 3 && PushDebugging.isIdentifier (fields[0]) && !fields[0].equals (this.requestId)
                    && (fields[1].equals ("RUN") || fields[1].equals ("PAUSE")))
                {
                    final int seconds = Integer.parseInt (fields[2]);
                    if (seconds >= 1 && seconds <= 60)
                    {
                        this.requestId = fields[0];
                        this.mode = fields[1];
                        this.paused = this.mode.equals ("PAUSE");
                        this.deadline = this.clock.getAsLong () + TimeUnit.SECONDS.toNanos (seconds);
                        record ("REQUEST", this.requestId + " " + this.mode + " " + seconds);
                    }
                }
            }
            boolean changed = false;
            String entry;
            while ((entry = this.entries.poll ()) != null)
            {
                if (this.output.length () + entry.length () > 1_000_000)
                    this.output.delete (0, this.output.indexOf ("\n", 250_000) + 1);
                changed = true;
                this.output.append (entry).append ('\n');
            }
            final boolean running = this.isRecording ();
            if (changed)
            {
                final Path trace = directory.resolve ("selection-trace.tsv.tmp");
                Files.writeString (trace, this.output);
                PushDebugging.replaceAtomically (trace, directory.resolve ("selection-trace.tsv"));
            }
            final Path status = directory.resolve ("selection-status.txt.tmp");
            Files.writeString (status, this.requestId + "\t" + (running ? this.mode : "OFF") + "\tdropped=" + this.dropped.get () + "\t" + System.currentTimeMillis () + "\n");
            PushDebugging.replaceAtomically (status, directory.resolve ("selection-status.txt"));
        }
        catch (final java.io.IOException | IllegalArgumentException ignored)
        {
            // A malformed/unreadable request cannot extend the monotonic timeout.
        }
    }

    @Override
    public void close ()
    {
        this.closed = true;
        this.deadline = 0;
        if (active == this) active = null;
        if (this.worker != null) PushDebugging.shutdownWorker (this.worker, this::poll);
        else this.poll ();
    }
}
