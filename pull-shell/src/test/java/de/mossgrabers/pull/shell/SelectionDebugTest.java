package de.mossgrabers.pull.shell;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class SelectionDebugTest
{
    @TempDir Path directory;

    @Test
    void noteCopyHoldRequiresAnArmedIntervalAndStopsWithoutAnotherPoll () throws Exception
    {
        final Path request = this.directory.resolve ("selection-request.txt");
        final AtomicLong clock = new AtomicLong (1);
        assertFalse (SelectionDebug.noteCopiesPaused ());
        try (final SelectionDebug debug = new SelectionDebug (this.directory, clock::get, null))
        {
            debug.poll ();
            assertFalse (debug.isNoteCopiesPaused ());
            Files.writeString (request, "hold1\tHOLD_NOTE_COPIES\t2");
            debug.poll ();
            assertTrue (debug.isNoteCopiesPaused ());
            assertFalse (debug.isScannerPaused (), "Holding note expressions must not pause the catalog scanner");
            clock.addAndGet (2_000_000_000L);
            assertFalse (debug.isNoteCopiesPaused (), "The controller path must expire even if the worker has not polled");
            debug.poll ();
            assertFalse (debug.isNoteCopiesPaused (), "A retained request cannot renew its deadline");
            Files.writeString (request, "hold2\tHOLD_NOTE_COPIES\t60");
            debug.poll ();
            assertTrue (debug.isNoteCopiesPaused ());
            Files.writeString (request, "stop1\tOFF\t0");
            debug.poll ();
            assertFalse (debug.isNoteCopiesPaused ());
            assertFalse (debug.isRecording ());
            assertTrue (Files.readString (this.directory.resolve ("selection-status.txt")).startsWith ("stop1\tOFF"));
            Files.writeString (request, "hold3\tHOLD_NOTE_COPIES\t60");
            debug.poll ();
            Files.writeString (request, "resume\tRUN\t5");
            debug.poll ();
            assertFalse (debug.isNoteCopiesPaused ());
            assertTrue (debug.isRecording ());
        }
        assertFalse (SelectionDebug.noteCopiesPaused ());
    }

    @Test
    void disabledNoteDiagnosticsDoNotReadHostObjects ()
    {
        final com.bitwig.extension.controller.api.NoteStep step = (com.bitwig.extension.controller.api.NoteStep) java.lang.reflect.Proxy.newProxyInstance (
            getClass ().getClassLoader (), new Class<?>[] {com.bitwig.extension.controller.api.NoteStep.class}, (proxy, method, arguments) -> {
                throw new AssertionError ("Disabled note diagnostics must not read " + method.getName ());
            });
        NoteStepDebug.recordObserved ("cursor", "track", 0, step);
        NoteStepDebug.recordObserved ("destination", "track", 0, step, true);
    }

    @Test
    void ignoresStaleRequestsExpiresWithoutPollingAndAcknowledgesResume () throws Exception
    {
        final Path request = this.directory.resolve ("selection-request.txt");
        Files.writeString (request, "stale\tPAUSE\t60");
        final AtomicLong clock = new AtomicLong (1);
        try (final SelectionDebug debug = new SelectionDebug (this.directory, clock::get, null))
        {
            debug.poll ();
            assertFalse (debug.isScannerPaused ());
            Files.writeString (request, "pause1\tPAUSE\t2");
            debug.poll ();
            assertTrue (debug.isScannerPaused ());
            assertTrue (Files.readString (this.directory.resolve ("selection-status.txt")).startsWith ("pause1\tPAUSE"));
            clock.addAndGet (2_000_000_001L);
            assertFalse (debug.isScannerPaused ());
            debug.poll ();
            assertFalse (debug.isScannerPaused (), "The same request must not renew its deadline");
            Files.writeString (request, "invalid\tPAUSE\t61");
            debug.poll ();
            assertFalse (debug.isScannerPaused ());
            Files.writeString (request, "pause2\tPAUSE\t60");
            debug.poll ();
            assertTrue (debug.isScannerPaused ());
            Files.writeString (request, "resume\tRUN\t5");
            debug.poll ();
            assertFalse (debug.isScannerPaused ());
            assertTrue (debug.isRecording ());
        }
        assertTrue (Files.readString (this.directory.resolve ("selection-status.txt")).contains ("\tOFF\t"));
    }
}
