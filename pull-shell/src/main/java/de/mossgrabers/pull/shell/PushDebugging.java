// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell;

import java.nio.file.Files;
import java.nio.file.Path;


/** Opt-in location shared by the local Push debugging transports. */
public final class PushDebugging
{
    private static final Path DIRECTORY = Path.of (System.getProperty ("user.home"), ".drivenbymoss", "pull", "debug");
    private static final Path ENABLED   = DIRECTORY.resolve ("enabled");


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
}
