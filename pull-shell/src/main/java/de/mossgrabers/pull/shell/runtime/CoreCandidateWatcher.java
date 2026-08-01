// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.CoreApi;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * One shell-owned worker that verifies immutable artifacts without loading child classes.
 */
final class CoreCandidateWatcher implements AutoCloseable
{
    static final String EMBEDDED_CORE_RESOURCE = "/META-INF/pull/core/pull-core.jar";

    private static final int MAX_MANIFEST_BYTES = 16_384;
    private static final long MAX_CORE_JAR_BYTES = 64L * 1024 * 1024;
    private static final long POLL_INTERVAL_MILLIS = 200;
    private static final long SHUTDOWN_WAIT_MILLIS = 250;

    private final RuntimePaths paths;
    private final String shellFingerprint;
    private final ScheduledExecutorService worker;
    private final long maximumCoreJarBytes;
    private final long shutdownWaitMillis;
    private final AtomicReference<PreparedCoreCandidate> pendingCandidate = new AtomicReference<> ();
    private final AtomicReference<RejectedCandidate> pendingRejection = new AtomicReference<> ();
    private final AtomicReference<WatcherNotice> pendingNotice = new AtomicReference<> ();
    private final AtomicLong latestRequestGeneration = new AtomicLong ();
    private final AtomicBoolean started = new AtomicBoolean ();
    private final AtomicBoolean closed = new AtomicBoolean ();
    private final AtomicBoolean shutdownTimeoutReported = new AtomicBoolean ();
    private final Set<Path> ownedJarPaths = ConcurrentHashMap.newKeySet ();
    private final Object lifecycleLock = new Object ();
    private String lastManifestFingerprint = "";
    private volatile boolean workerTerminated;
    private boolean workerBusy;
    private boolean cleanupRequested;


    CoreCandidateWatcher (final RuntimePaths paths)
    {
        this (paths, ShellBuildMetadata.load ().fingerprint (), Executors.newSingleThreadScheduledExecutor (task -> {
            final Thread thread = new Thread (task, "Pull core candidate watcher");
            thread.setDaemon (true);
            return thread;
        }));
    }


    CoreCandidateWatcher (final RuntimePaths paths, final String shellFingerprint, final ScheduledExecutorService worker)
    {
        this (paths, shellFingerprint, worker, MAX_CORE_JAR_BYTES, SHUTDOWN_WAIT_MILLIS);
    }


    CoreCandidateWatcher (final RuntimePaths paths, final String shellFingerprint, final ScheduledExecutorService worker, final long maximumCoreJarBytes, final long shutdownWaitMillis)
    {
        this.paths = Objects.requireNonNull (paths, "paths");
        this.shellFingerprint = new ShellBuildMetadata (shellFingerprint).fingerprint ();
        this.worker = Objects.requireNonNull (worker, "worker");
        if (maximumCoreJarBytes <= 0)
            throw new IllegalArgumentException ("maximumCoreJarBytes must be positive");
        if (shutdownWaitMillis < 0)
            throw new IllegalArgumentException ("shutdownWaitMillis cannot be negative");
        this.maximumCoreJarBytes = maximumCoreJarBytes;
        this.shutdownWaitMillis = shutdownWaitMillis;
    }


    void start ()
    {
        if (!this.started.compareAndSet (false, true))
            throw new IllegalStateException ("CoreCandidateWatcher can only be started once");

        this.worker.execute ( () -> this.runWorkerTask (this::prepareEmbeddedSafely));
        this.worker.scheduleWithFixedDelay ( () -> this.runWorkerTask (this::scanExternalSafely), POLL_INTERVAL_MILLIS, POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }


    Optional<PreparedCoreCandidate> takeCandidate ()
    {
        return Optional.ofNullable (this.pendingCandidate.getAndSet (null));
    }


    Optional<WatcherNotice> takeNotice ()
    {
        return Optional.ofNullable (this.pendingNotice.getAndSet (null));
    }


    Optional<RejectedCandidate> takeRejection ()
    {
        return Optional.ofNullable (this.pendingRejection.getAndSet (null));
    }


    boolean isLatest (final long requestGeneration)
    {
        return !this.closed.get () && requestGeneration == this.latestRequestGeneration.get ();
    }


    void publishStatus (final long requestGeneration, final RuntimeStatus status)
    {
        Objects.requireNonNull (status, "status");
        if (this.closed.get ())
            return;

        try
        {
            this.worker.execute ( () -> {
                if (this.isLatest (requestGeneration))
                    this.writeStatusSafely (status);
            });
        }
        catch (final RuntimeException ignored)
        {
            // Shutdown won the race with status publication.
        }
    }


    void scanExternalNow ()
    {
        this.scanExternalSafely ();
    }


    /**
     * Reject new work and wait a bounded time for the verification worker to stop. Temporary JARs
     * deliberately remain in place until {@link #cleanup()} is called after active classloaders
     * have closed.
     */
    void shutdown ()
    {
        boolean initiateShutdown = false;
        synchronized (this.lifecycleLock)
        {
            if (this.closed.compareAndSet (false, true))
            {
                this.latestRequestGeneration.incrementAndGet ();
                this.pendingCandidate.set (null);
                this.pendingRejection.set (null);
                initiateShutdown = true;
            }
        }
        if (initiateShutdown)
            this.worker.shutdownNow ();

        if (this.workerTerminated)
            return;

        try
        {
            this.workerTerminated = this.worker.awaitTermination (this.shutdownWaitMillis, TimeUnit.MILLISECONDS);
            if (!this.workerTerminated && this.shutdownTimeoutReported.compareAndSet (false, true))
                this.pendingNotice.set (WatcherNotice.warning ("Core candidate worker did not stop within " + this.shutdownWaitMillis + " ms; in-flight JAR cleanup will finish when verification returns"));
        }
        catch (final InterruptedException failure)
        {
            Thread.currentThread ().interrupt ();
            if (this.shutdownTimeoutReported.compareAndSet (false, true))
                this.pendingNotice.set (WatcherNotice.warning ("Interrupted while joining the core candidate worker; in-flight JAR cleanup will finish when verification returns"));
        }

        this.pendingCandidate.set (null);
        this.pendingRejection.set (null);
    }


    /**
     * Delete watcher-owned private JARs after all classloaders have closed.
     */
    void cleanup ()
    {
        if (!this.closed.get ())
            this.shutdown ();

        final boolean deleteNow;
        synchronized (this.lifecycleLock)
        {
            this.cleanupRequested = true;
            deleteNow = !this.workerBusy;
        }
        if (deleteNow)
            this.deleteAllOwnedJars ();
    }


    void release (final PreparedCoreCandidate candidate)
    {
        if (candidate != null)
            this.deleteOwnedJar (candidate.jarPath ());
    }


    /** {@inheritDoc} */
    @Override
    public void close ()
    {
        this.shutdown ();
        this.cleanup ();
    }


    private void prepareEmbeddedSafely ()
    {
        if (this.closed.get ())
            return;

        Path extracted = null;
        try (InputStream input = CoreCandidateWatcher.class.getResourceAsStream (EMBEDDED_CORE_RESOURCE))
        {
            if (input == null)
            {
                this.pendingNotice.set (WatcherNotice.warning ("Embedded reloadable core is not packaged in this build"));
                return;
            }

            extracted = this.copyToPrivateJar (input, "pull-embedded-core-");
            final CoreArtifactMetadata metadata = CoreArtifactMetadata.read (extracted);
            if (metadata.apiVersion () != CoreApi.VERSION)
                throw new IllegalArgumentException ("Embedded core API " + metadata.apiVersion () + " does not match shell API " + CoreApi.VERSION);

            if (this.isStopping ())
                return;

            final long requestGeneration = this.latestRequestGeneration.incrementAndGet ();
            final PreparedCoreCandidate candidate = new PreparedCoreCandidate (requestGeneration, metadata.apiVersion (), metadata.buildId (), extracted);
            this.replacePendingCandidate (candidate);
            this.pendingNotice.set (WatcherNotice.info ("Prepared embedded core " + metadata.buildId ()));
            extracted = null;
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            if (!this.isStopping ())
                this.pendingNotice.set (WatcherNotice.warning ("Embedded core preparation failed: " + sanitize (failure)));
        }
        finally
        {
            if (extracted != null)
                this.deleteOwnedJar (extracted);
        }
    }


    private void scanExternalSafely ()
    {
        if (this.closed.get ())
            return;

        final Path manifestPath = this.paths.candidate ();
        try
        {
            if (Files.isSymbolicLink (manifestPath) || !Files.isRegularFile (manifestPath, LinkOption.NOFOLLOW_LINKS))
                return;

            final byte [] manifestBytes = this.readBounded (manifestPath, MAX_MANIFEST_BYTES, "Candidate manifest");
            final String fingerprint = digest (manifestBytes);
            if (fingerprint.equals (this.lastManifestFingerprint))
                return;

            this.lastManifestFingerprint = fingerprint;
            final long requestGeneration = this.latestRequestGeneration.incrementAndGet ();
            this.clearPendingCandidate ();

            final String requestedBuildId = extractBuildId (manifestBytes);
            Path verifiedCopy = null;
            try
            {
                final CoreCandidateManifest manifest = CoreCandidateManifest.parse (manifestBytes);
                if (manifest.apiVersion () != CoreApi.VERSION)
                {
                    this.reject (requestGeneration, RuntimeStatus.State.RESTART_REQUIRED, manifest.buildId (), "Core API " + manifest.apiVersion () + " does not match shell API " + CoreApi.VERSION);
                    return;
                }
                if (!manifest.shellFingerprint ().equals (this.shellFingerprint))
                {
                    this.reject (requestGeneration, RuntimeStatus.State.RESTART_REQUIRED, manifest.buildId (), "Candidate was built against different shell/API sources; rebuild and install the extension, then restart Bitwig");
                    return;
                }

                final Path jarPath = manifest.resolveJar (this.paths.root ());
                verifiedCopy = this.copyAndVerifyCandidateJar (jarPath, manifest);

                if (!Arrays.equals (manifestBytes, this.readBounded (manifestPath, MAX_MANIFEST_BYTES, "Candidate manifest")))
                    return;
                if (this.isStopping () || requestGeneration != this.latestRequestGeneration.get ())
                    return;

                final PreparedCoreCandidate candidate = new PreparedCoreCandidate (requestGeneration, manifest.apiVersion (), manifest.buildId (), verifiedCopy);
                this.replacePendingCandidate (candidate);
                this.pendingNotice.set (WatcherNotice.info ("Prepared development core " + manifest.buildId ()));
                verifiedCopy = null;
            }
            catch (final Throwable failure)
            {
                rethrowFatal (failure);
                if (!this.isStopping ())
                    this.reject (requestGeneration, RuntimeStatus.State.FAILED, requestedBuildId, sanitize (failure));
            }
            finally
            {
                if (verifiedCopy != null)
                    this.deleteOwnedJar (verifiedCopy);
            }
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            if (!this.isStopping ())
                this.pendingNotice.set (WatcherNotice.warning ("Candidate scan failed: " + sanitize (failure)));
        }
    }


    private void reject (final long requestGeneration, final RuntimeStatus.State state, final String requestedBuildId, final String message)
    {
        final String buildId = requestedBuildId == null ? "" : requestedBuildId;
        this.pendingNotice.set (WatcherNotice.warning ("Core " + (buildId.isEmpty () ? "candidate" : buildId) + " was rejected: " + message));
        this.pendingRejection.set (new RejectedCandidate (requestGeneration, state, buildId, message));
    }


    private void writeStatusSafely (final RuntimeStatus status)
    {
        if (this.closed.get ())
            return;

        Path temporary = null;
        try
        {
            Files.createDirectories (this.paths.root ());
            temporary = Files.createTempFile (this.paths.root (), ".status-", ".tmp");
            try (FileOutputStream output = new FileOutputStream (temporary.toFile ()))
            {
                status.toProperties ().store (output, "Pull reload status");
                output.getChannel ().force (true);
            }
            Files.move (temporary, this.paths.status (), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
        }
        catch (final AtomicMoveNotSupportedException failure)
        {
            this.pendingNotice.set (WatcherNotice.warning ("Reload status requires atomic file moves"));
        }
        catch (final IOException failure)
        {
            this.pendingNotice.set (WatcherNotice.warning ("Could not write reload status: " + sanitize (failure)));
        }
        finally
        {
            if (temporary != null)
            {
                try
                {
                    Files.deleteIfExists (temporary);
                }
                catch (final IOException ignored)
                {
                    // Best effort for a private temporary file.
                }
            }
        }
    }


    private void replacePendingCandidate (final PreparedCoreCandidate candidate)
    {
        final PreparedCoreCandidate replaced = this.pendingCandidate.getAndSet (candidate);
        if (replaced != null)
            this.release (replaced);
    }


    private void clearPendingCandidate ()
    {
        final PreparedCoreCandidate discarded = this.pendingCandidate.getAndSet (null);
        if (discarded != null)
            this.release (discarded);
    }


    private void deleteOwnedJar (final Path jarPath)
    {
        if (!this.ownedJarPaths.contains (jarPath))
            return;

        try
        {
            Files.deleteIfExists (jarPath);
            this.ownedJarPaths.remove (jarPath);
        }
        catch (final IOException failure)
        {
            this.pendingNotice.set (WatcherNotice.warning ("Could not delete private core JAR: " + sanitize (failure)));
        }
    }


    private void deleteAllOwnedJars ()
    {
        for (final Path jarPath: this.ownedJarPaths)
            this.deleteOwnedJar (jarPath);
    }


    private void runWorkerTask (final Runnable task)
    {
        synchronized (this.lifecycleLock)
        {
            if (this.closed.get ())
                return;
            this.workerBusy = true;
        }

        try
        {
            task.run ();
        }
        finally
        {
            final boolean cleanup;
            synchronized (this.lifecycleLock)
            {
                this.workerBusy = false;
                cleanup = this.cleanupRequested;
            }
            if (cleanup)
                this.deleteAllOwnedJars ();
        }
    }


    private boolean isStopping ()
    {
        return this.closed.get () || Thread.currentThread ().isInterrupted ();
    }


    private Path copyAndVerifyCandidateJar (final Path jarPath, final CoreCandidateManifest manifest) throws IOException
    {
        if (Files.isSymbolicLink (jarPath) || !Files.isRegularFile (jarPath, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable (jarPath))
            throw new IOException ("Candidate JAR is not an immutable readable regular file: " + jarPath);
        if (Files.size (jarPath) > this.maximumCoreJarBytes)
            throw new IOException ("Candidate JAR is larger than " + this.maximumCoreJarBytes + " bytes");

        Path privateCopy = null;
        try (InputStream input = Files.newInputStream (jarPath, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))
        {
            privateCopy = Files.createTempFile ("pull-verified-core-", ".jar");
            this.ownedJarPaths.add (privateCopy);
            final String actualDigest;
            try (OutputStream output = Files.newOutputStream (privateCopy, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
            {
                actualDigest = copyAndDigest (input, output, this.maximumCoreJarBytes, this::isStopping, "Candidate JAR");
            }
            if (!manifest.sha256 ().equals (actualDigest))
                throw new IOException ("Candidate JAR SHA-256 does not match manifest");

            final CoreArtifactMetadata metadata = CoreArtifactMetadata.read (privateCopy);
            if (metadata.apiVersion () != manifest.apiVersion ())
                throw new IllegalArgumentException ("Candidate JAR API version does not match manifest");
            if (!metadata.buildId ().equals (manifest.buildId ()))
                throw new IllegalArgumentException ("Candidate JAR build ID does not match manifest");

            final Path verified = privateCopy;
            privateCopy = null;
            return verified;
        }
        finally
        {
            if (privateCopy != null)
                this.deleteOwnedJar (privateCopy);
        }
    }


    private Path copyToPrivateJar (final InputStream input, final String prefix) throws IOException
    {
        Path privateCopy = Files.createTempFile (prefix, ".jar");
        this.ownedJarPaths.add (privateCopy);
        try
        {
            try (OutputStream output = Files.newOutputStream (privateCopy, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
            {
                copyAndDigest (input, output, this.maximumCoreJarBytes, this::isStopping, "Core JAR");
            }
            final Path copied = privateCopy;
            privateCopy = null;
            return copied;
        }
        finally
        {
            if (privateCopy != null)
                this.deleteOwnedJar (privateCopy);
        }
    }


    private byte [] readBounded (final Path path, final long maximumBytes, final String description) throws IOException
    {
        if (Files.size (path) > maximumBytes)
            throw new IOException (description + " is larger than " + maximumBytes + " bytes");

        try (InputStream input = Files.newInputStream (path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS); ByteArrayOutputStream output = new ByteArrayOutputStream ())
        {
            copyAndDigest (input, output, maximumBytes, this::isStopping, description);
            return output.toByteArray ();
        }
    }


    static String copyAndDigest (final InputStream input, final OutputStream output, final long maximumBytes, final BooleanSupplier cancelled, final String description) throws IOException
    {
        Objects.requireNonNull (input, "input");
        Objects.requireNonNull (output, "output");
        Objects.requireNonNull (cancelled, "cancelled");
        Objects.requireNonNull (description, "description");
        if (maximumBytes <= 0)
            throw new IllegalArgumentException ("maximumBytes must be positive");

        final MessageDigest digest = sha256 ();
        final byte [] buffer = new byte [16_384];
        long totalBytes = 0;
        int read;
        while (true)
        {
            if (cancelled.getAsBoolean () || Thread.currentThread ().isInterrupted ())
                throw new InterruptedIOException (description + " verification was cancelled");

            read = input.read (buffer);
            if (cancelled.getAsBoolean () || Thread.currentThread ().isInterrupted ())
                throw new InterruptedIOException (description + " verification was cancelled");
            if (read < 0)
                break;
            if (read == 0)
                continue;

            totalBytes += read;
            if (totalBytes > maximumBytes)
                throw new IOException (description + " is larger than " + maximumBytes + " bytes");
            output.write (buffer, 0, read);
            digest.update (buffer, 0, read);
        }
        return HexFormat.of ().formatHex (digest.digest ());
    }


    private static String extractBuildId (final byte [] manifestBytes)
    {
        final Properties properties = new Properties ();
        try
        {
            properties.load (new ByteArrayInputStream (manifestBytes));
            final String buildId = properties.getProperty ("buildId", "");
            return CoreArtifactMetadata.VALID_BUILD_ID.test (buildId) ? buildId : "";
        }
        catch (final IOException failure)
        {
            return "";
        }
    }


    private static String digest (final byte [] content)
    {
        return HexFormat.of ().formatHex (sha256 ().digest (content));
    }


    private static MessageDigest sha256 ()
    {
        try
        {
            return MessageDigest.getInstance ("SHA-256");
        }
        catch (final NoSuchAlgorithmException failure)
        {
            throw new IllegalStateException ("SHA-256 is unavailable", failure);
        }
    }


    private static String sanitize (final Throwable failure)
    {
        final String detail = failure.getMessage ();
        return failure.getClass ().getSimpleName () + (detail == null || detail.isBlank () ? "" : ": " + detail);
    }


    private static void rethrowFatal (final Throwable failure)
    {
        if (failure instanceof final VirtualMachineError virtualMachineError)
            throw virtualMachineError;
    }


    record WatcherNotice (boolean warning, String message)
    {
        WatcherNotice
        {
            Objects.requireNonNull (message, "message");
        }


        static WatcherNotice info (final String message)
        {
            return new WatcherNotice (false, message);
        }


        static WatcherNotice warning (final String message)
        {
            return new WatcherNotice (true, message);
        }
    }


    record RejectedCandidate (long requestGeneration, RuntimeStatus.State state, String requestedBuildId, String message)
    {
        RejectedCandidate
        {
            if (requestGeneration <= 0)
                throw new IllegalArgumentException ("requestGeneration must be positive");
            Objects.requireNonNull (state, "state");
            Objects.requireNonNull (requestedBuildId, "requestedBuildId");
            Objects.requireNonNull (message, "message");
        }
    }


}
