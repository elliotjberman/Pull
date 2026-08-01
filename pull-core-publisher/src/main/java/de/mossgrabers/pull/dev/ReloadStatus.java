// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.dev;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Activation acknowledgement written atomically by the running shell.
 *
 * @param formatVersion Status wire format
 * @param state Activation outcome
 * @param requestedBuildId Candidate acknowledged by this status
 * @param activeBuildId Core that remains active after the attempt, or blank if none
 * @param message Human-readable detail
 */
public record ReloadStatus (int formatVersion, State state, String requestedBuildId, String activeBuildId, String message)
{
    /** Stable shell acknowledgement file. */
    public static final String FILE_NAME = "status.properties";

    /** Candidate outcome. */
    public enum State
    {
        /** Candidate became the active core. */
        ACTIVE,

        /** Candidate failed; the previous core remains active. */
        FAILED,

        /** The installed shell/API cannot load this candidate. */
        RESTART_REQUIRED
    }


    /**
     * Read and validate a status file.
     *
     * @param path Status path
     * @return Parsed status
     * @throws IOException If the status cannot be read
     */
    public static ReloadStatus read (final Path path) throws IOException
    {
        final Properties properties = new Properties ();
        try (Reader reader = Files.newBufferedReader (path, StandardCharsets.UTF_8))
        {
            properties.load (reader);
        }

        final int formatVersion = parseInteger (properties, "formatVersion");
        if (formatVersion != CorePublication.FORMAT_VERSION)
            throw new IllegalArgumentException ("Unsupported status formatVersion: " + formatVersion);

        final String rawState = required (properties, "state");
        final State state = switch (rawState)
        {
            case "active" -> State.ACTIVE;
            case "failed" -> State.FAILED;
            case "restartRequired" -> State.RESTART_REQUIRED;
            default -> throw new IllegalArgumentException ("Unsupported reload state: " + rawState);
        };
        return new ReloadStatus (
            formatVersion,
            state,
            required (properties, "requestedBuildId"),
            properties.getProperty ("activeBuildId", ""),
            properties.getProperty ("message", ""));
    }


    private static int parseInteger (final Properties properties, final String key)
    {
        final String value = required (properties, key);
        try
        {
            return Integer.parseInt (value);
        }
        catch (final NumberFormatException ex)
        {
            throw new IllegalArgumentException (key + " is not an integer: " + value, ex);
        }
    }


    private static String required (final Properties properties, final String key)
    {
        final String value = properties.getProperty (key);
        if (value == null || value.isBlank ())
            throw new IllegalArgumentException ("Status is missing " + key);
        return value;
    }
}
