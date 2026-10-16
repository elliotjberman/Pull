package de.mossgrabers.pull.shell;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Event-driven debug ownership. No tick callback, idle executor or disabled-state poll. */
public final class PushDebugSession implements AutoCloseable
{
    private final Path directory;
    private final Consumer<Runnable> dispatch;
    private final Function<PushDebugging.Workers, Session> open;
    private final Consumer<String> status;
    private Session active;
    private PushDebugging.Workers workers;
    private boolean desired;
    private boolean transitioning;
    private boolean failed;
    private boolean closed;

    public PushDebugSession (final Path directory, final Consumer<Runnable> dispatch,
                            final Function<PushDebugging.Workers, Session> open, final Consumer<String> status)
    {
        this.directory = directory;
        this.dispatch = dispatch;
        this.open = open;
        this.status = status;
    }

    /** Called only by controller-thread startup, settings or a posted enabled-worker notification. */
    public void request (final boolean enabled)
    {
        if (this.closed || this.failed)
            return;
        this.desired = enabled;
        this.advance ();
    }

    /** One startup publication; no periodic status/heartbeat task is retained when off. */
    public void start (final boolean enabled)
    {
        this.desired = enabled;
        this.transitioning = true;
        this.status.accept (enabled ? "Starting" : "Off");
        this.background ( () -> this.writeStatus (enabled ? "STARTING" : "OFF"), this::finished);
    }

    /** Enabled-only filesystem notification, fenced to the session that observed it. */
    public void requestOffFrom (final PushDebugging.Workers source)
    {
        this.post ( () -> {
            if (this.workers == source) this.request (false);
        });
    }

    private void advance ()
    {
        if (this.transitioning || this.closed || this.desired == (this.active != null))
            return;
        this.transitioning = true;
        if (!this.desired)
        {
            this.stop ();
            return;
        }
        this.status.accept ("Starting");
        this.background ( () -> {
            Files.createDirectories (this.directory);
            this.writeStatus ("STARTING");
            // Never replay commands from a retired debug session.
            for (final String file: List.of ("capture-request.txt", "navigate-request.txt", "trace-request.txt", "selection-request.txt",
                    "capture-status.txt", "navigate-status.txt", "trace-status.txt", "selection-status.txt"))
                Files.deleteIfExists (this.directory.resolve (file));
            Files.writeString (this.directory.resolve ("enabled"), "");
        }, () -> {
            this.workers = new PushDebugging.Workers ();
            if (!this.desired)
            {
                this.stop ();
                return;
            }
            try
            {
                this.active = this.open.apply (this.workers);
                this.status.accept ("On");
                this.background ( () -> this.writeStatus ("ON"), this::finished);
            }
            catch (final RuntimeException ex)
            {
                this.desired = false;
                this.failed = true;
                this.stop ();
                this.status.accept ("Start failed: " + ex.getMessage ());
            }
        });
    }

    private void stop ()
    {
        this.status.accept ("Stopping");
        final PushDebugging.Workers retiring = this.workers;
        this.workers = null;
        final Session previous = this.active;
        this.active = null;
        try
        {
            if (previous != null) previous.close ();
        }
        catch (final Exception ex)
        {
            this.failed = true;
            this.status.accept ("Cleanup failed: " + ex.getMessage ());
        }
        this.background ( () -> {
            try { this.writeStatus ("STOPPING"); }
            finally { if (retiring != null) retiring.awaitStopped (); }
        }, () -> this.finishRetirement (previous, 100));
    }

    private void finishRetirement (final Session previous, final int remaining)
    {
        // Only during explicit shutdown: allow the normal router to drain released debug edges.
        // The dispatcher requests 20 ms between callbacks; no permanent tick hook is installed.
        if (previous != null && !previous.retired ())
        {
            if (remaining > 0)
            {
                this.post (() -> this.finishRetirement (previous, remaining - 1));
                return;
            }
            this.failed = true;
            this.status.accept ("Input cleanup timed out; restart required");
        }
        final String receipt = this.failed ? "FAILED" : "OFF";
        this.background (() -> {
            Files.deleteIfExists (this.directory.resolve ("enabled"));
            this.writeStatus (receipt);
        }, () -> {
            if (!this.failed) this.status.accept ("Off");
            this.finished ();
        });
    }

    private void finished ()
    {
        this.transitioning = false;
        if (!this.failed) this.advance ();
    }

    private void writeStatus (final String state) throws java.io.IOException
    {
        Files.createDirectories (this.directory);
        final Path temporary = this.directory.resolve ("runtime-status.txt.tmp");
        Files.writeString (temporary, ProcessHandle.current ().pid () + "\t" + state + "\n");
        PushDebugging.replaceAtomically (temporary, this.directory.resolve ("runtime-status.txt"));
    }

    private void background (final Work work, final Runnable completion)
    {
        final Thread thread = new Thread ( () -> {
            try
            {
                work.run ();
                this.post (completion);
            }
            catch (final Exception ex)
            {
                this.post ( () -> {
                    this.failed = true;
                    this.transitioning = false;
                    if (this.active != null)
                    {
                        this.desired = false;
                        this.transitioning = true;
                        this.stop ();
                    }
                    this.status.accept ("Failed: " + ex.getMessage ());
                });
            }
        }, "Pull debug lifecycle");
        thread.setDaemon (true);
        thread.start ();
    }

    /** Post only while this extension still owns the session; never schedule after exit. */
    public synchronized void post (final Runnable callback)
    {
        if (!this.closed)
            this.dispatch.accept ( () -> {
                if (!this.closed) callback.run ();
            });
    }

    @Override
    public synchronized void close ()
    {
        if (this.closed) return;
        this.closed = true;
        final AutoCloseable previous = this.active;
        this.active = null;
        final PushDebugging.Workers retiring = this.workers;
        this.workers = null;
        try
        {
            if (previous != null) previous.close ();
        }
        catch (final Exception ignored)
        {
            // Terminal cleanup is best-effort; no post-exit Bitwig callbacks are promised.
        }
        if (retiring != null)
            this.background (retiring::awaitStopped, () -> { });
    }

    @FunctionalInterface
    private interface Work { void run () throws Exception; }

    /** Detached input may still need a later router drain before its admission can retire. */
    @FunctionalInterface
    public interface Session extends AutoCloseable
    {
        @Override void close ();
        default boolean retired () { return true; }
    }
}
