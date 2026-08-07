// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.CoreApi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * File-protocol tests for the parent-only candidate watcher.
 */
class CoreCandidateWatcherTest
{
    private static final String SHELL_FINGERPRINT = "a".repeat (40);

    @TempDir
    Path temporaryDirectory;


    @Test
    void verifiesAndPublishesAnImmutableCandidate () throws Exception
    {
        final RuntimePaths paths = new RuntimePaths (this.temporaryDirectory.resolve ("reload"));
        final Path jar = writeCoreJar (paths.root (), "build-a", CoreApi.VERSION);
        final String publishedDigest = digest (jar);
        writeManifest (paths, "build-a", CoreApi.VERSION, publishedDigest);
        final CoreCandidateWatcher watcher = watcher (paths);

        watcher.scanExternalNow ();
        final PreparedCoreCandidate candidate = watcher.takeCandidate ().orElseThrow ();

        assertEquals ("build-a", candidate.buildId ());
        assertNotEquals (jar.toAbsolutePath ().normalize (), candidate.jarPath ());
        Files.writeString (jar, "publisher path was replaced after verification");
        assertEquals (publishedDigest, digest (candidate.jarPath ()));
        assertEquals ("build-a", CoreArtifactMetadata.read (candidate.jarPath ()).buildId ());
        assertTrue (watcher.isLatest (candidate.requestGeneration ()));
        watcher.close ();
    }


    @Test
    void hashFailureAcknowledgesRequestedAndStillActiveBuild () throws Exception
    {
        final RuntimePaths paths = new RuntimePaths (this.temporaryDirectory.resolve ("reload"));
        writeCoreJar (paths.root (), "broken-hash", CoreApi.VERSION);
        writeManifest (paths, "broken-hash", CoreApi.VERSION, "0".repeat (64));
        final ScheduledExecutorService worker = testWorker ();
        final CoreCandidateWatcher watcher = watcher (paths, worker);

        watcher.scanExternalNow ();

        assertTrue (watcher.takeCandidate ().isEmpty ());
        publishRejection (watcher, "stable", worker);
        final Properties status = readProperties (paths.status ());
        assertEquals ("failed", status.getProperty ("state"));
        assertEquals ("broken-hash", status.getProperty ("requestedBuildId"));
        assertEquals ("stable", status.getProperty ("activeBuildId"));
        watcher.close ();
    }


    @Test
    void apiMismatchRequiresOneShellRestartBeforeClassLoading () throws Exception
    {
        final RuntimePaths paths = new RuntimePaths (this.temporaryDirectory.resolve ("reload"));
        final int incompatibleApi = CoreApi.VERSION + 1;
        final Path jar = writeCoreJar (paths.root (), "new-api", incompatibleApi);
        writeManifest (paths, "new-api", incompatibleApi, digest (jar));
        final ScheduledExecutorService worker = testWorker ();
        final CoreCandidateWatcher watcher = watcher (paths, worker);

        watcher.scanExternalNow ();

        assertTrue (watcher.takeCandidate ().isEmpty ());
        publishRejection (watcher, "stable", worker);
        final Properties status = readProperties (paths.status ());
        assertEquals ("restartRequired", status.getProperty ("state"));
        assertEquals ("stable", status.getProperty ("activeBuildId"));
        watcher.close ();
    }


    @Test
    void coreApiFingerprintMismatchRequiresRestartBeforeCopyingCandidate () throws Exception
    {
        final RuntimePaths paths = new RuntimePaths (this.temporaryDirectory.resolve ("reload"));
        final Path jar = writeCoreJar (paths.root (), "old-shell", CoreApi.VERSION);
        writeManifest (paths, "old-shell", CoreApi.VERSION, "b".repeat (40), digest (jar));
        final CoreCandidateWatcher watcher = watcher (paths);

        watcher.scanExternalNow ();

        assertTrue (watcher.takeCandidate ().isEmpty ());
        final CoreCandidateWatcher.RejectedCandidate rejection = watcher.takeRejection ().orElseThrow ();
        assertEquals (RuntimeStatus.State.RESTART_REQUIRED, rejection.state ());
        assertTrue (rejection.message ().contains ("different core API sources"));
        watcher.close ();
    }


    @Test
    void newestManifestSupersedesAnUnactivatedCandidate () throws Exception
    {
        final RuntimePaths paths = new RuntimePaths (this.temporaryDirectory.resolve ("reload"));
        final CoreCandidateWatcher watcher = watcher (paths);
        final Path firstJar = writeCoreJar (paths.root (), "first", CoreApi.VERSION);
        writeManifest (paths, "first", CoreApi.VERSION, digest (firstJar));
        watcher.scanExternalNow ();
        final PreparedCoreCandidate first = watcher.takeCandidate ().orElseThrow ();

        final Path secondJar = writeCoreJar (paths.root (), "second", CoreApi.VERSION);
        writeManifest (paths, "second", CoreApi.VERSION, digest (secondJar));
        watcher.scanExternalNow ();
        final PreparedCoreCandidate second = watcher.takeCandidate ().orElseThrow ();

        assertFalse (watcher.isLatest (first.requestGeneration ()));
        assertTrue (watcher.isLatest (second.requestGeneration ()));
        assertEquals ("second", second.buildId ());
        watcher.close ();
    }


    @Test
    void staleActivationStatusCannotOverwriteNewerCandidateFailure () throws Exception
    {
        final RuntimePaths paths = new RuntimePaths (this.temporaryDirectory.resolve ("reload"));
        final ScheduledExecutorService worker = testWorker ();
        final CoreCandidateWatcher watcher = watcher (paths, worker);
        final Path firstJar = writeCoreJar (paths.root (), "first", CoreApi.VERSION);
        writeManifest (paths, "first", CoreApi.VERSION, digest (firstJar));
        watcher.scanExternalNow ();
        final PreparedCoreCandidate first = watcher.takeCandidate ().orElseThrow ();

        writeCoreJar (paths.root (), "broken", CoreApi.VERSION);
        writeManifest (paths, "broken", CoreApi.VERSION, "0".repeat (64));
        watcher.scanExternalNow ();
        publishRejection (watcher, "stable", worker);
        assertEquals ("broken", readProperties (paths.status ()).getProperty ("requestedBuildId"));

        watcher.publishStatus (first.requestGeneration (), new RuntimeStatus (RuntimeStatus.State.ACTIVE, "first", "first", "Activated"));
        worker.submit ( () -> {
            // Queue barrier after the stale status task.
        }).get ();

        final Properties status = readProperties (paths.status ());
        assertEquals ("broken", status.getProperty ("requestedBuildId"));
        assertEquals ("failed", status.getProperty ("state"));
        watcher.close ();
    }


    @Test
    void rejectionUsesActiveBuildAtControllerThreadAcknowledgement () throws Exception
    {
        final RuntimePaths paths = new RuntimePaths (this.temporaryDirectory.resolve ("reload"));
        writeCoreJar (paths.root (), "broken", CoreApi.VERSION);
        writeManifest (paths, "broken", CoreApi.VERSION, "0".repeat (64));
        final ScheduledExecutorService worker = testWorker ();
        final CoreCandidateWatcher watcher = watcher (paths, worker);

        watcher.scanExternalNow ();
        publishRejection (watcher, "just-committed", worker);

        final Properties status = readProperties (paths.status ());
        assertEquals ("broken", status.getProperty ("requestedBuildId"));
        assertEquals ("just-committed", status.getProperty ("activeBuildId"));
        watcher.close ();
    }


    @Test
    void shutdownAndCleanupAreSeparateForWindowsSafeLoaderClose () throws Exception
    {
        final RuntimePaths paths = new RuntimePaths (this.temporaryDirectory.resolve ("reload"));
        final Path jar = writeCoreJar (paths.root (), "private", CoreApi.VERSION);
        writeManifest (paths, "private", CoreApi.VERSION, digest (jar));
        final CoreCandidateWatcher watcher = watcher (paths);
        watcher.scanExternalNow ();
        final Path privateJar = watcher.takeCandidate ().orElseThrow ().jarPath ();

        watcher.shutdown ();
        assertTrue (Files.exists (privateJar), "The private JAR must outlive the verification worker and its active loader");

        watcher.cleanup ();
        assertFalse (Files.exists (privateJar));
    }


    @Test
    void shutdownTimeoutIsReportedWithoutLeakingAPreparedJar () throws Exception
    {
        final RuntimePaths paths = new RuntimePaths (this.temporaryDirectory.resolve ("reload"));
        final Path jar = writeCoreJar (paths.root (), "private", CoreApi.VERSION);
        writeManifest (paths, "private", CoreApi.VERSION, digest (jar));
        final ScheduledExecutorService worker = testWorker ();
        final CoreCandidateWatcher watcher = new CoreCandidateWatcher (paths, SHELL_FINGERPRINT, worker, 1024 * 1024, 10);
        watcher.scanExternalNow ();
        final Path privateJar = watcher.takeCandidate ().orElseThrow ().jarPath ();
        final CountDownLatch taskStarted = new CountDownLatch (1);
        final CountDownLatch releaseTask = new CountDownLatch (1);
        worker.execute ( () -> awaitIgnoringInterrupts (taskStarted, releaseTask));
        assertTrue (taskStarted.await (1, TimeUnit.SECONDS));

        try
        {
            watcher.shutdown ();
            final CoreCandidateWatcher.WatcherNotice notice = watcher.takeNotice ().orElseThrow ();
            assertTrue (notice.warning ());
            assertTrue (notice.message ().contains ("did not stop within 10 ms"));

            watcher.cleanup ();
            assertFalse (Files.exists (privateJar), "A fully prepared JAR is safe to delete after its loader closes");
        }
        finally
        {
            releaseTask.countDown ();
            assertTrue (worker.awaitTermination (1, TimeUnit.SECONDS));
            watcher.cleanup ();
        }
        assertFalse (Files.exists (privateJar));
    }


    @Test
    void candidateCopyHasAHardSizeLimit () throws Exception
    {
        final RuntimePaths paths = new RuntimePaths (this.temporaryDirectory.resolve ("reload"));
        final Path jar = writeCoreJar (paths.root (), "large", CoreApi.VERSION);
        writeManifest (paths, "large", CoreApi.VERSION, digest (jar));
        final ScheduledExecutorService worker = testWorker ();
        final CoreCandidateWatcher watcher = new CoreCandidateWatcher (paths, SHELL_FINGERPRINT, worker, 32, 25);

        watcher.scanExternalNow ();

        final CoreCandidateWatcher.RejectedCandidate rejection = watcher.takeRejection ().orElseThrow ();
        assertEquals (RuntimeStatus.State.FAILED, rejection.state ());
        assertTrue (rejection.message ().contains ("larger than 32 bytes"));
        watcher.close ();
    }


    @Test
    void candidateCopyHonorsCancellationBeforeReading ()
    {
        final InterruptedIOException failure = assertThrows (InterruptedIOException.class, () -> CoreCandidateWatcher.copyAndDigest (
            new ByteArrayInputStream (new byte [64]),
            new ByteArrayOutputStream (),
            64,
            () -> true,
            "Candidate JAR"));

        assertTrue (failure.getMessage ().contains ("cancelled"));
    }


    @Test
    void manifestCannotRedirectTheLoaderOutsidePublicationDirectory ()
    {
        assertThrows (IllegalArgumentException.class, () -> new CoreCandidateManifest (CoreApi.VERSION, SHELL_FINGERPRINT, "safe", "../safe.jar", "0".repeat (64)));
    }


    private static CoreCandidateWatcher watcher (final RuntimePaths paths)
    {
        return watcher (paths, testWorker ());
    }


    private static CoreCandidateWatcher watcher (final RuntimePaths paths, final ScheduledExecutorService worker)
    {
        return new CoreCandidateWatcher (paths, SHELL_FINGERPRINT, worker);
    }


    private static ScheduledExecutorService testWorker ()
    {
        return Executors.newSingleThreadScheduledExecutor (task -> {
            final Thread thread = new Thread (task, "CoreCandidateWatcherTest");
            thread.setDaemon (true);
            return thread;
        });
    }


    private static void publishRejection (final CoreCandidateWatcher watcher, final String activeBuildId, final ScheduledExecutorService worker) throws Exception
    {
        final CoreCandidateWatcher.RejectedCandidate rejection = watcher.takeRejection ().orElseThrow ();
        watcher.publishStatus (rejection.requestGeneration (), new RuntimeStatus (rejection.state (), rejection.requestedBuildId (), activeBuildId, rejection.message ()));
        worker.submit ( () -> {
            // Queue barrier after the status write.
        }).get ();
    }


    private static void awaitIgnoringInterrupts (final CountDownLatch taskStarted, final CountDownLatch releaseTask)
    {
        taskStarted.countDown ();
        while (true)
        {
            try
            {
                releaseTask.await ();
                return;
            }
            catch (final InterruptedException ignored)
            {
                // Deliberately model an uncooperative verification dependency.
            }
        }
    }


    private static Path writeCoreJar (final Path directory, final String buildId, final int apiVersion) throws IOException
    {
        Files.createDirectories (directory);
        final Path jarPath = directory.resolve ("pull-core-" + buildId + ".jar");
        try (JarOutputStream jar = new JarOutputStream (Files.newOutputStream (jarPath)))
        {
            jar.putNextEntry (new JarEntry (CoreArtifactMetadata.RESOURCE));
            final Properties metadata = new Properties ();
            metadata.setProperty ("formatVersion", "1");
            metadata.setProperty ("apiVersion", Integer.toString (apiVersion));
            metadata.setProperty ("buildId", buildId);
            metadata.store (jar, null);
            jar.closeEntry ();
        }
        return jarPath;
    }


    private static void writeManifest (final RuntimePaths paths, final String buildId, final int apiVersion, final String sha256) throws IOException
    {
        writeManifest (paths, buildId, apiVersion, SHELL_FINGERPRINT, sha256);
    }


    private static void writeManifest (final RuntimePaths paths, final String buildId, final int apiVersion, final String shellFingerprint, final String sha256) throws IOException
    {
        final Properties manifest = new Properties ();
        manifest.setProperty ("formatVersion", "1");
        manifest.setProperty ("apiVersion", Integer.toString (apiVersion));
        manifest.setProperty ("shellFingerprint", shellFingerprint);
        manifest.setProperty ("buildId", buildId);
        manifest.setProperty ("jar", "pull-core-" + buildId + ".jar");
        manifest.setProperty ("sha256", sha256);
        try (OutputStream output = Files.newOutputStream (paths.candidate ()))
        {
            manifest.store (output, null);
        }
    }


    private static Properties readProperties (final Path path) throws IOException
    {
        final Properties properties = new Properties ();
        try (InputStream input = Files.newInputStream (path))
        {
            properties.load (input);
        }
        return properties;
    }


    private static String digest (final Path path) throws IOException, NoSuchAlgorithmException
    {
        final MessageDigest digest = MessageDigest.getInstance ("SHA-256");
        try (InputStream input = Files.newInputStream (path))
        {
            final byte [] buffer = new byte [4096];
            int read;
            while ((read = input.read (buffer)) >= 0)
            {
                if (read > 0)
                    digest.update (buffer, 0, read);
            }
        }
        return HexFormat.of ().formatHex (digest.digest ());
    }
}
