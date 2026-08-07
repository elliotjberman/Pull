// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.dev;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.Properties;

/**
 * Pointer to one immutable core artifact.
 *
 * @param formatVersion Publication manifest format
 * @param apiVersion Exact parent-loaded core API version
 * @param shellFingerprint Exact parent-loaded core API source fingerprint expected by this core
 * @param buildId Exact core descriptor build identifier
 * @param jar Immutable artifact file name
 * @param sha256 Lowercase SHA-256 of the artifact
 */
public record CorePublication (int formatVersion, int apiVersion, String shellFingerprint, String buildId, String jar, String sha256)
{
    /** Current publication manifest format. */
    public static final int FORMAT_VERSION = 1;

    /** Stable file watched by the shell. */
    public static final String MANIFEST_FILE = "candidate.properties";


    /**
     * Validate publication fields.
     */
    public CorePublication
    {
        if (formatVersion != FORMAT_VERSION)
            throw new IllegalArgumentException ("Unsupported formatVersion: " + formatVersion);
        if (apiVersion <= 0)
            throw new IllegalArgumentException ("apiVersion must be positive");
        shellFingerprint = requireFingerprint (shellFingerprint);

        buildId = requireBuildId (buildId);
        jar = Objects.requireNonNull (jar, "jar");
        if (!jar.equals (artifactFileName (buildId)))
            throw new IllegalArgumentException ("jar must be " + artifactFileName (buildId));

        sha256 = Objects.requireNonNull (sha256, "sha256");
        if (!sha256.matches ("[0-9a-f]{64}"))
            throw new IllegalArgumentException ("sha256 must contain 64 lowercase hexadecimal characters");
    }


    /**
     * Return the immutable artifact file name for a build.
     *
     * @param buildId Build identifier
     * @return Artifact file name
     */
    public static String artifactFileName (final String buildId)
    {
        return "pull-core-" + requireBuildId (buildId) + ".jar";
    }


    /**
     * Read a publication manifest.
     *
     * @param path Manifest path
     * @return Parsed publication
     * @throws IOException If the file cannot be read
     */
    public static CorePublication read (final Path path) throws IOException
    {
        final Properties properties = new Properties ();
        try (Reader reader = Files.newBufferedReader (path, StandardCharsets.UTF_8))
        {
            properties.load (reader);
        }

        return new CorePublication (
            parseInteger (properties, "formatVersion"),
            parseInteger (properties, "apiVersion"),
            required (properties, "shellFingerprint"),
            required (properties, "buildId"),
            required (properties, "jar"),
            required (properties, "sha256"));
    }


    /**
     * Write the deterministic properties representation.
     *
     * @param path Destination path
     * @throws IOException If the file cannot be written
     */
    void write (final Path path) throws IOException
    {
        try (Writer writer = Files.newBufferedWriter (path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
        {
            writer.write ("formatVersion=" + this.formatVersion + "\n");
            writer.write ("apiVersion=" + this.apiVersion + "\n");
            writer.write ("shellFingerprint=" + this.shellFingerprint + "\n");
            writer.write ("buildId=" + this.buildId + "\n");
            writer.write ("jar=" + this.jar + "\n");
            writer.write ("sha256=" + this.sha256 + "\n");
        }
    }


    private static String requireBuildId (final String value)
    {
        Objects.requireNonNull (value, "buildId");
        if (!value.matches ("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))
            throw new IllegalArgumentException ("buildId must match [A-Za-z0-9][A-Za-z0-9._-]{0,127}");
        if ("unpublished".equals (value))
            throw new IllegalArgumentException ("The reserved buildId 'unpublished' cannot be published");
        return value;
    }


    private static String requireFingerprint (final String value)
    {
        Objects.requireNonNull (value, "shellFingerprint");
        if (!value.matches ("[0-9a-f]{40}|[0-9a-f]{64}"))
            throw new IllegalArgumentException ("shellFingerprint must contain 40 or 64 lowercase hexadecimal characters");
        return value;
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
            throw new IllegalArgumentException ("Manifest is missing " + key);
        return value;
    }
}
