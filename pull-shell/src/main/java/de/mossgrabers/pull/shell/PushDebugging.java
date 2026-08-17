// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;


/** Opt-in location shared by the local Push debugging transports. */
public final class PushDebugging
{
    private static final Path DIRECTORY = Path.of (System.getProperty ("user.home"), ".drivenbymoss", "pull", "debug");
    private static final Path ENABLED   = DIRECTORY.resolve ("enabled");
    private static final long SHUTDOWN_WAIT_MILLIS = 250;


    private PushDebugging ()
    {
        // Utility class.
    }


    /** @return The fixed local debugger handshake directory. */
    public static Path directory ()
    {
        return DIRECTORY;
    }


    /** @return True only when debugging was explicitly enabled before extension startup. */
    public static boolean isEnabled ()
    {
        return Files.isRegularFile (ENABLED);
    }


    /** Create one named daemon worker for an enabled local debug transport. */
    public static ScheduledExecutorService createWorker (final String name)
    {
        return Executors.newSingleThreadScheduledExecutor (task -> {
            final Thread thread = new Thread (task, Objects.requireNonNull (name, "name"));
            thread.setDaemon (true);
            return thread;
        });
    }


    /** Drain and bound shutdown of one owned debug worker. */
    public static void shutdownWorker (final ScheduledExecutorService worker, final Runnable finalDrain)
    {
        worker.execute (Objects.requireNonNull (finalDrain, "finalDrain"));
        worker.shutdown ();
        try
        {
            if (!worker.awaitTermination (SHUTDOWN_WAIT_MILLIS, TimeUnit.MILLISECONDS))
                worker.shutdownNow ();
        }
        catch (final InterruptedException ex)
        {
            worker.shutdownNow ();
            Thread.currentThread ().interrupt ();
        }
    }


    /** Atomically replace a local artifact where the filesystem supports it. */
    public static void replaceAtomically (final Path temporary, final Path output) throws IOException
    {
        try
        {
            Files.move (temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (final IOException ex)
        {
            Files.move (temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }


    /** Test the bounded identifier grammar shared by local debug protocols. */
    public static boolean isIdentifier (final String value)
    {
        if (value == null || value.isEmpty () || value.length () > 80)
            return false;
        for (int index = 0; index < value.length (); index++)
        {
            final char character = value.charAt (index);
            if (!Character.isLetterOrDigit (character) && character != '.' && character != '_' && character != '-')
                return false;
        }
        return true;
    }


    /** Remove field delimiters from one local status value. */
    public static String sanitize (final String value)
    {
        return value == null ? "" : value.replace ('\t', ' ').replace ('\r', ' ').replace ('\n', ' ');
    }
}
