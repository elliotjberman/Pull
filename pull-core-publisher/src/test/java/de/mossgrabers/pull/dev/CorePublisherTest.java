// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.dev;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Immutable publication and integrity tests.
 */
class CorePublisherTest
{
    private static final String SHELL_FINGERPRINT = "a".repeat (40);

    @TempDir
    Path temporaryDirectory;


    @Test
    void publishesImmutableJarHashAndAtomicManifest () throws Exception
    {
        final Path source = createCoreJar ("build-a", 1, "payload-a");
        final Path publicationDirectory = this.temporaryDirectory.resolve ("published");

        final CorePublication published = new CorePublisher ().publish (source, publicationDirectory, "build-a", SHELL_FINGERPRINT);

        final Path artifact = publicationDirectory.resolve (published.jar ());
        assertTrue (Files.isRegularFile (artifact));
        assertEquals (sha256 (artifact), published.sha256 ());
        assertEquals (published, CorePublication.read (publicationDirectory.resolve (CorePublication.MANIFEST_FILE)));
        try (var files = Files.list (publicationDirectory))
        {
            assertFalse (files.anyMatch (path -> path.getFileName ().toString ().endsWith (".tmp")));
        }
    }


    @Test
    void neverOverwritesAnExistingBuildArtifactOrManifest () throws Exception
    {
        final Path publicationDirectory = this.temporaryDirectory.resolve ("published");
        final CorePublisher publisher = new CorePublisher ();
        publisher.publish (createCoreJar ("same-id", 1, "first"), publicationDirectory, "same-id", SHELL_FINGERPRINT);
        final Path artifact = publicationDirectory.resolve (CorePublication.artifactFileName ("same-id"));
        final byte [] originalArtifact = Files.readAllBytes (artifact);
        final byte [] originalManifest = Files.readAllBytes (publicationDirectory.resolve (CorePublication.MANIFEST_FILE));

        assertThrows (FileAlreadyExistsException.class, () -> publisher.publish (createCoreJar ("same-id", 1, "second"), publicationDirectory, "same-id", SHELL_FINGERPRINT));

        assertArrayEquals (originalArtifact, Files.readAllBytes (artifact));
        assertArrayEquals (originalManifest, Files.readAllBytes (publicationDirectory.resolve (CorePublication.MANIFEST_FILE)));
    }


    @Test
    void rejectsBuildMismatchAndUsesEmbeddedApiVersion () throws Exception
    {
        final Path publicationDirectory = this.temporaryDirectory.resolve ("published");
        final CorePublisher publisher = new CorePublisher ();

        final IllegalArgumentException buildFailure = assertThrows (IllegalArgumentException.class, () -> publisher.publish (createCoreJar ("actual", 1, "payload"), publicationDirectory, "requested", SHELL_FINGERPRINT));
        assertTrue (buildFailure.getMessage ().contains ("does not match requested build"));
        assertFalse (Files.exists (publicationDirectory.resolve (CorePublication.MANIFEST_FILE)));
        assertFalse (Files.exists (publicationDirectory.resolve (CorePublication.artifactFileName ("requested"))));

        final CorePublication apiTwo = publisher.publish (createCoreJar ("api-two", 2, "payload"), publicationDirectory, "api-two", SHELL_FINGERPRINT);
        assertEquals (2, apiTwo.apiVersion ());
    }


    @Test
    void promotingNewBuildKeepsPriorImmutableArtifact () throws Exception
    {
        final Path publicationDirectory = this.temporaryDirectory.resolve ("published");
        final CorePublisher publisher = new CorePublisher ();
        final CorePublication first = publisher.publish (createCoreJar ("build-a", 1, "a"), publicationDirectory, "build-a", SHELL_FINGERPRINT);
        final CorePublication second = publisher.publish (createCoreJar ("build-b", 1, "b"), publicationDirectory, "build-b", SHELL_FINGERPRINT);

        assertTrue (Files.isRegularFile (publicationDirectory.resolve (first.jar ())));
        assertTrue (Files.isRegularFile (publicationDirectory.resolve (second.jar ())));
        assertEquals (second, CorePublication.read (publicationDirectory.resolve (CorePublication.MANIFEST_FILE)));
    }


    @Test
    void reservedDefaultBuildCannotBePublished ()
    {
        final IllegalArgumentException failure = assertThrows (IllegalArgumentException.class, () -> CorePublication.artifactFileName ("unpublished"));

        assertTrue (failure.getMessage ().contains ("reserved"));
    }


    @Test
    void rejectsInvalidShellFingerprint ()
    {
        assertThrows (IllegalArgumentException.class, () -> new CorePublication (1, 1, "not-a-fingerprint", "build-a", "pull-core-build-a.jar", "0".repeat (64)));
    }


    private Path createCoreJar (final String buildId, final int apiVersion, final String payload) throws IOException
    {
        final Path jar = this.temporaryDirectory.resolve (buildId + "-" + apiVersion + "-" + payload + ".jar");
        try (ZipOutputStream output = new ZipOutputStream (Files.newOutputStream (jar)))
        {
            output.putNextEntry (new ZipEntry ("META-INF/pull-core.properties"));
            output.write (("formatVersion=1\napiVersion=" + apiVersion + "\nbuildId=" + buildId + "\n").getBytes (StandardCharsets.UTF_8));
            output.closeEntry ();
            output.putNextEntry (new ZipEntry ("core.txt"));
            output.write (payload.getBytes (StandardCharsets.UTF_8));
            output.closeEntry ();
        }
        return jar;
    }


    private static String sha256 (final Path path) throws IOException, NoSuchAlgorithmException
    {
        return HexFormat.of ().formatHex (MessageDigest.getInstance ("SHA-256").digest (Files.readAllBytes (path)));
    }
}
