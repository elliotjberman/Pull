// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.dev;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Command-line entry point for core publication and activation acknowledgement.
 */
public final class CorePublisherMain
{
    private CorePublisherMain ()
    {
        // Utility class
    }


    /**
     * Run the publisher command.
     *
     * @param args Command arguments
     */
    public static void main (final String [] args)
    {
        try
        {
            run (args);
        }
        catch (final Exception ex)
        {
            System.err.println ("Core reload failed: " + ex.getMessage ());
            System.exit (1);
        }
    }


    private static void run (final String [] args) throws Exception
    {
        if (args.length == 0)
            throw usage ();

        switch (args[0])
        {
            case "publish" -> publish (args);
            case "await" -> await (args);
            default -> throw usage ();
        }
    }


    private static void publish (final String [] args) throws Exception
    {
        if (args.length != 5)
            throw usage ();
        final CorePublication publication = new CorePublisher ().publish (Path.of (args[1]), Path.of (args[2]), args[3], args[4]);
        System.out.println ("Published " + publication.jar () + " (SHA-256 " + publication.sha256 () + ")");
    }


    private static void await (final String [] args) throws Exception
    {
        if (args.length != 4)
            throw usage ();
        final ReloadStatus status = new ActivationWaiter ().await (Path.of (args[1]), args[2], Duration.ofMillis (Long.parseLong (args[3])));
        System.out.println ("Active core: " + status.activeBuildId () + (status.message ().isBlank () ? "" : " (" + status.message () + ")"));
    }


    private static IllegalArgumentException usage ()
    {
        return new IllegalArgumentException ("Usage: publish <core.jar> <directory> <buildId> <shellFingerprint> | await <directory> <buildId> <timeoutMillis>");
    }
}
