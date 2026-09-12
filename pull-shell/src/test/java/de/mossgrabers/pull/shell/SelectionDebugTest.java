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
