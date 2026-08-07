// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.dev;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Publishes immutable core JARs and atomically promotes their manifest.
 */
public final class CorePublisher
{
    private static final String CORE_METADATA = "META-INF/pull-core.properties";
    private static final int COPY_BUFFER_SIZE = 64 * 1024;


    /**
     * Publish one core artifact.
     *
     * @param sourceJar Packaged core JAR
     * @param publicationDirectory Shell-watched directory
     * @param expectedBuildId Exact build identifier supplied to Maven
     * @param shellFingerprint Exact local parent-loaded core API source fingerprint
     * @return Published manifest value
     * @throws IOException If publication fails
     */
    public CorePublication publish (final Path sourceJar, final Path publicationDirectory, final String expectedBuildId, final String shellFingerprint) throws IOException
    {
        Objects.requireNonNull (sourceJar, "sourceJar");
        Objects.requireNonNull (publicationDirectory, "publicationDirectory");

        final String artifactName = CorePublication.artifactFileName (expectedBuildId);
        Files.createDirectories (publicationDirectory);
        final Path artifact = publicationDirectory.resolve (artifactName);

        boolean artifactCreated = false;
        boolean retainArtifact = false;
        try
        {
            copyNewArtifact (sourceJar, artifact);
            artifactCreated = true;
            final EmbeddedMetadata metadata = readEmbeddedMetadata (artifact);
            if (!metadata.buildId ().equals (expectedBuildId))
                throw new IllegalArgumentException ("Core JAR buildId '" + metadata.buildId () + "' does not match requested build '" + expectedBuildId + "'");

            final String hash = sha256 (artifact);
            final CorePublication publication = new CorePublication (CorePublication.FORMAT_VERSION, metadata.apiVersion (), shellFingerprint, expectedBuildId, artifactName, hash);
            replaceManifestAtomically (publicationDirectory, publication);
            retainArtifact = true;
            return publication;
        }
        catch (final FileAlreadyExistsException ex)
        {
            throw new FileAlreadyExistsException ("Refusing to overwrite immutable core artifact " + artifact);
        }
        finally
        {
            if (artifactCreated && !retainArtifact)
                Files.deleteIfExists (artifact);
        }
    }


    private static void copyNewArtifact (final Path source, final Path destination) throws IOException
    {
        boolean destinationCreated = false;
        boolean copyComplete = false;
        try (FileChannel input = FileChannel.open (source, StandardOpenOption.READ);
             FileChannel output = FileChannel.open (destination, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE))
        {
            destinationCreated = true;
            final ByteBuffer buffer = ByteBuffer.allocate (COPY_BUFFER_SIZE);
            while (input.read (buffer) >= 0)
            {
                buffer.flip ();
                while (buffer.hasRemaining ())
                    output.write (buffer);
                buffer.clear ();
            }
            output.force (true);
            copyComplete = true;
        }
        finally
        {
            if (destinationCreated && !copyComplete)
                Files.deleteIfExists (destination);
        }
    }


    private static EmbeddedMetadata readEmbeddedMetadata (final Path artifact) throws IOException
    {
        final Properties properties = new Properties ();
        try (ZipFile jar = new ZipFile (artifact.toFile ()))
        {
            final ZipEntry entry = jar.getEntry (CORE_METADATA);
            if (entry == null)
                throw new IllegalArgumentException ("Core JAR is missing " + CORE_METADATA);
            try (InputStream input = jar.getInputStream (entry))
            {
                properties.load (input);
            }
        }

        final int formatVersion = parseInteger (properties, "formatVersion");
        if (formatVersion != CorePublication.FORMAT_VERSION)
            throw new IllegalArgumentException ("Unsupported core metadata formatVersion: " + formatVersion);
        return new EmbeddedMetadata (parseInteger (properties, "apiVersion"), required (properties, "buildId"));
    }


    private static String sha256 (final Path artifact) throws IOException
    {
        final MessageDigest digest;
        try
        {
            digest = MessageDigest.getInstance ("SHA-256");
        }
        catch (final NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException ("JDK does not provide SHA-256", ex);
        }

        try (InputStream input = Files.newInputStream (artifact))
        {
            final byte [] buffer = new byte[COPY_BUFFER_SIZE];
            int count;
            while ((count = input.read (buffer)) >= 0)
                digest.update (buffer, 0, count);
        }
        return HexFormat.of ().formatHex (digest.digest ());
    }


    private static void replaceManifestAtomically (final Path directory, final CorePublication publication) throws IOException
    {
        final Path temporary = directory.resolve ("." + CorePublication.MANIFEST_FILE + "." + UUID.randomUUID () + ".tmp");
        try
        {
            publication.write (temporary);
            try (FileChannel channel = FileChannel.open (temporary, StandardOpenOption.WRITE))
            {
                channel.force (true);
            }
            Files.move (temporary, directory.resolve (CorePublication.MANIFEST_FILE), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (final AtomicMoveNotSupportedException ex)
        {
            throw new IOException ("Publication directory does not support atomic manifest replacement: " + directory, ex);
        }
        finally
        {
            Files.deleteIfExists (temporary);
        }
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
            throw new IllegalArgumentException ("Core metadata " + key + " is not an integer: " + value, ex);
        }
    }


    private static String required (final Properties properties, final String key)
    {
        final String value = properties.getProperty (key);
        if (value == null || value.isBlank ())
            throw new IllegalArgumentException ("Core metadata is missing " + key);
        return value;
    }


    private record EmbeddedMetadata (int apiVersion, String buildId)
    {
    }
}
