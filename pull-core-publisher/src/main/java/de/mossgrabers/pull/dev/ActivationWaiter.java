// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.dev;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Waits for the running shell to acknowledge the exact requested build.
 */
public final class ActivationWaiter
{
    private static final long POLL_MILLIS = 25;


    /**
     * Wait for an exact activation acknowledgement.
     *
     * @param publicationDirectory Shell-watched directory
     * @param requestedBuildId Requested candidate
     * @param timeout Maximum wait duration
     * @return Matching active status
     * @throws IOException If status cannot be read
     * @throws InterruptedException If interrupted while waiting
     */
    public ReloadStatus await (final Path publicationDirectory, final String requestedBuildId, final Duration timeout) throws IOException, InterruptedException
    {
        Objects.requireNonNull (publicationDirectory, "publicationDirectory");
        Objects.requireNonNull (requestedBuildId, "requestedBuildId");
        Objects.requireNonNull (timeout, "timeout");
        if (timeout.isNegative ())
            throw new IllegalArgumentException ("timeout must not be negative");

        final long timeoutNanos = timeout.toNanos ();
        final long started = System.nanoTime ();
        final Path statusPath = publicationDirectory.resolve (ReloadStatus.FILE_NAME);
        do
        {
            if (Files.isRegularFile (statusPath))
            {
                final ReloadStatus status = ReloadStatus.read (statusPath);
                if (requestedBuildId.equals (status.requestedBuildId ()))
                    return requireActive (status, requestedBuildId);
            }

            if (System.nanoTime () - started >= timeoutNanos)
                break;
            TimeUnit.MILLISECONDS.sleep (POLL_MILLIS);
        }
        while (true);

        throw new IllegalStateException ("Timed out waiting for Bitwig to activate core build " + requestedBuildId + ". Ensure Bitwig is running the reloadable shell; shell or API changes require rebuilding the extension and restarting Bitwig.");
    }


    private static ReloadStatus requireActive (final ReloadStatus status, final String requestedBuildId)
    {
        if (status.state () == ReloadStatus.State.ACTIVE)
        {
            if (requestedBuildId.equals (status.activeBuildId ()))
                return status;
            throw new IllegalStateException ("Bitwig reported build " + requestedBuildId + " active, but its activeBuildId is '" + status.activeBuildId () + "'");
        }

        final String stillActive = status.activeBuildId ().isBlank () ? "no core" : "core " + status.activeBuildId ();
        if (status.state () == ReloadStatus.State.RESTART_REQUIRED)
            throw new IllegalStateException ("Core build " + requestedBuildId + " requires a shell/API rebuild and Bitwig restart: " + status.message () + " (still active: " + stillActive + ")");
        throw new IllegalStateException ("Bitwig rejected core build " + requestedBuildId + ": " + status.message () + " (still active: " + stillActive + ")");
    }
}
