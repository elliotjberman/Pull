package de.mossgrabers.pull.shell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class PushDebugSessionTest
{
    @TempDir Path directory;
    private final LinkedBlockingQueue<Runnable> callbacks = new LinkedBlockingQueue<> ();
    private final List<String> states = new ArrayList<> ();

    @Test
    void offStartupInstallsNoWorkersOrRepeatingCallback () throws Exception
    {
        try (PushDebugSession session = new PushDebugSession (this.directory, this.callbacks::add,
            workers -> { throw new AssertionError ("Disabled startup must not open transports"); }, this.states::add))
        {
            session.start (false);
            this.next ();
            assertTrue (Files.readString (this.directory.resolve ("runtime-status.txt")).endsWith ("\tOFF\n"));
            assertFalse (Files.exists (this.directory.resolve ("enabled")));
            session.request (false);
            assertNull (this.callbacks.poll (50, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    void stopDetachesImmediatelyButAcknowledgesAndReopensOnlyAfterWorkersRetire () throws Exception
    {
        final CountDownLatch release = new CountDownLatch (1);
        final CountDownLatch busy = new CountDownLatch (1);
        final AtomicInteger opened = new AtomicInteger ();
        final AtomicInteger detached = new AtomicInteger ();
        final List<ScheduledExecutorService> executors = new ArrayList<> ();
        final List<PushDebugging.Workers> groups = new ArrayList<> ();
        try (PushDebugSession session = new PushDebugSession (this.directory, this.callbacks::add, workers -> {
            groups.add (workers);
            opened.incrementAndGet ();
            final ScheduledExecutorService executor = workers.create ("test debug worker");
            executors.add (executor);
            executor.execute ( () -> {
                busy.countDown ();
                try { release.await (); }
                catch (final InterruptedException ex) { Thread.currentThread ().interrupt (); }
            });
            return () -> {
                detached.incrementAndGet ();
                PushDebugging.shutdownWorker (executor, () -> { });
            };
        }, this.states::add))
        {
            session.start (true);
            this.until (() -> opened.get () == 1);
            this.next (); // ON receipt publication completes.
            assertTrue (busy.await (1, TimeUnit.SECONDS));
            session.request (false);
            assertEquals (1, detached.get ());
            assertFalse (executors.get (0).isTerminated ());
            session.request (true);
            assertEquals (1, opened.get (), "Replacement must wait for the old worker");
            assertNotEquals ("Off", this.states.getLast ());
            release.countDown ();
            this.until (() -> opened.get () == 2);
            assertTrue (executors.get (0).isTerminated ());
            this.next ();

            session.requestOffFrom (groups.get (0));
            this.next ();
            assertEquals (1, detached.get (), "An old worker cannot disable the new session");
            session.requestOffFrom (groups.get (1));
            this.next ();
            this.until (() -> this.states.getLast ().equals ("Off"));
            assertTrue (executors.stream ().allMatch (ScheduledExecutorService::isTerminated));
            assertFalse (Files.exists (this.directory.resolve ("enabled")));
            assertTrue (Files.readString (this.directory.resolve ("runtime-status.txt")).endsWith ("\tOFF\n"));
        }
        finally { release.countDown (); }
    }

    @Test
    void offDuringPreparationDoesNotAttachTransportsOrReplayOldRequests () throws Exception
    {
        Files.writeString (this.directory.resolve ("trace-request.txt"), "old ARM request");
        final AtomicInteger opened = new AtomicInteger ();
        try (PushDebugSession session = new PushDebugSession (this.directory, this.callbacks::add,
            workers -> { opened.incrementAndGet (); return () -> { }; }, this.states::add))
        {
            session.request (true);
            session.request (false);
            this.until (() -> this.states.getLast ().equals ("Off"));
            assertEquals (0, opened.get ());
            assertFalse (Files.exists (this.directory.resolve ("trace-request.txt")));
            assertFalse (Files.exists (this.directory.resolve ("enabled")));
        }
    }

    @Test
    void exitSuppressesAlreadyQueuedCompletionsAndFutureHostScheduling () throws Exception
    {
        final AtomicInteger opened = new AtomicInteger ();
        final PushDebugSession session = new PushDebugSession (this.directory, this.callbacks::add,
            workers -> { opened.incrementAndGet (); return () -> { }; }, this.states::add);
        session.request (true);
        final Runnable prepared = this.callbacks.poll (2, TimeUnit.SECONDS);
        assertNotNull (prepared);
        session.close ();
        prepared.run ();
        session.post (() -> fail ("No post-exit callback"));
        session.request (true);
        assertEquals (0, opened.get ());
        assertTrue (this.callbacks.isEmpty ());
    }

    @Test
    void failedOpenRetiresCreatedWorkersAndCannotBeReenabled () throws Exception
    {
        final List<ScheduledExecutorService> executors = new ArrayList<> ();
        try (PushDebugSession session = new PushDebugSession (this.directory, this.callbacks::add, workers -> {
            executors.add (workers.create ("partial debug startup"));
            throw new IllegalStateException ("fixture failure");
        }, this.states::add))
        {
            session.request (true);
            this.next ();
            this.next ();
            this.next ();
            assertTrue (executors.getFirst ().isTerminated ());
            assertTrue (Files.readString (this.directory.resolve ("runtime-status.txt")).endsWith ("\tFAILED\n"));
            session.request (true);
            assertEquals (1, executors.size ());
        }
    }

    @Test
    void offReceiptWaitsForLaterInputRetirement () throws Exception
    {
        final boolean [] retired = {false};
        try (PushDebugSession session = new PushDebugSession (this.directory, this.callbacks::add,
            workers -> new PushDebugSession.Session ()
            {
                @Override public void close () { }
                @Override public boolean retired () { return retired[0]; }
            }, this.states::add))
        {
            session.request (true);
            this.next ();
            this.next ();
            session.request (false);
            this.next ();
            assertTrue (Files.readString (this.directory.resolve ("runtime-status.txt")).endsWith ("\tSTOPPING\n"));
            assertEquals ("Stopping", this.states.getLast ());
            retired[0] = true;
            this.until (() -> this.states.getLast ().equals ("Off"));
            assertTrue (this.callbacks.isEmpty (), "Retirement must leave no recurring callback");
        }
    }

    @ParameterizedTest
    @ValueSource (booleans = {false, true})
    void shutdownFailureStillRetiresReleasedInputWithoutClaimingOff (final boolean stuckWorker) throws Exception
    {
        final boolean [] routeIdle = {false};
        final boolean [] admissionActive = {true};
        final List<ScheduledExecutorService> executors = new ArrayList<> ();
        final CountDownLatch release = new CountDownLatch (1);
        try (PushDebugSession session = new PushDebugSession (this.directory, this.callbacks::add,
            workers -> {
                executors.add (workers.create ("receipt failure test"));
                if (stuckWorker) executors.getLast ().execute (() -> {
                    while (release.getCount () != 0)
                    {
                        try { release.await (); }
                        catch (final InterruptedException ignored) { /* Model a worker that ignores cancellation. */ }
                    }
                });
                return new PushDebugSession.Session ()
                {
                    @Override public void close () { }
                    @Override public boolean retired ()
                    {
                        if (routeIdle[0]) admissionActive[0] = false;
                        return !admissionActive[0];
                    }
                };
            }, this.states::add))
        {
            session.request (true);
            this.next ();
            this.next ();
            final Path temporary = this.directory.resolve ("runtime-status.txt.tmp");
            if (!stuckWorker) Files.createDirectory (temporary);
            session.request (false);
            this.next ();
            assertEquals (!stuckWorker, executors.getFirst ().isTerminated ());
            assertTrue (admissionActive[0]);
            assertTrue (this.states.getLast ().startsWith ("Cleanup failed:"));
            if (!stuckWorker) Files.delete (temporary);
            release.countDown ();
            assertTrue (executors.getFirst ().awaitTermination (1, TimeUnit.SECONDS));
            routeIdle[0] = true;
            this.next ();
            this.next ();
            assertFalse (admissionActive[0], "I/O failure must not abandon input retirement");
            assertTrue (Files.readString (this.directory.resolve ("runtime-status.txt")).endsWith ("\tFAILED\n"));
            assertFalse (this.states.contains ("Off"));
            session.request (true);
            assertEquals (1, executors.size (), "Failed session cannot reopen");
            assertTrue (this.callbacks.isEmpty ());
        }
        finally { release.countDown (); }
    }

    private void until (final BooleanSupplier complete) throws Exception
    {
        for (int i = 0; !complete.getAsBoolean () && i < 12; i++) this.next ();
        assertTrue (complete.getAsBoolean ());
    }

    private void next () throws Exception
    {
        final Runnable callback = this.callbacks.poll (3, TimeUnit.SECONDS);
        assertNotNull (callback, "Expected one transition completion, without controller ticking");
        callback.run ();
    }
}
