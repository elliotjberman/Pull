// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ControllerHost;

import java.io.IOException;
import java.util.Objects;

/**
 * Behavior-neutral live-reload owner driven from Bitwig's controller thread.
 */
final class CoreReloadSupervisor implements AutoCloseable
{
    private static final String LOG_PREFIX = "[Pull reload] ";

    private final ControllerHost host;
    private final CoreJarLoader jarLoader;
    private final RuntimeManager runtimeManager;
    private final CoreCandidateWatcher watcher;
    private PreparedCoreCandidate activeCandidate;
    private boolean started;
    private boolean closed;


    CoreReloadSupervisor (final ControllerHost host)
    {
        this.host = Objects.requireNonNull (host, "host");
        this.jarLoader = new CoreJarLoader ();
        this.runtimeManager = new RuntimeManager (new NoOpCoreRuntimeEnvironment (), new HostRuntimeLog (host));
        this.watcher = new CoreCandidateWatcher (RuntimePaths.fromSystem ());
    }


    void start ()
    {
        if (this.started || this.closed)
            return;

        this.runtimeManager.start ();
        this.watcher.start ();
        this.started = true;
    }


    void tick ()
    {
        if (!this.started || this.closed)
            return;

        this.reportWatcherNotice ();
        this.watcher.takeRejection ().ifPresent (this::acknowledgeRejection);
        this.watcher.takeCandidate ().ifPresent (this::activate);
    }


    /** {@inheritDoc} */
    @Override
    public void close ()
    {
        if (this.closed)
            return;

        this.closed = true;
        this.watcher.shutdown ();
        this.reportWatcherNotice ();
        try
        {
            this.runtimeManager.close ();
        }
        finally
        {
            this.activeCandidate = null;
            this.watcher.cleanup ();
            this.reportWatcherNotice ();
        }
    }


    private void activate (final PreparedCoreCandidate candidate)
    {
        if (!this.watcher.isLatest (candidate.requestGeneration ()))
        {
            this.watcher.release (candidate);
            return;
        }

        boolean retained = false;
        try
        {
            final LoadedCoreProvider source = this.jarLoader.load (candidate.jarPath ());
            final ActivationResult result = this.runtimeManager.activate (candidate.buildId (), source, () -> this.watcher.isLatest (candidate.requestGeneration ()));
            if (result.state () == ActivationResult.State.SUPERSEDED)
                return;

            if (result.state () == ActivationResult.State.ACTIVE)
            {
                final PreparedCoreCandidate previous = this.activeCandidate;
                this.activeCandidate = candidate;
                retained = true;
                this.watcher.release (previous);
            }

            final RuntimeStatus.State statusState = result.state () == ActivationResult.State.ACTIVE ? RuntimeStatus.State.ACTIVE : RuntimeStatus.State.FAILED;
            this.watcher.publishStatus (candidate.requestGeneration (), new RuntimeStatus (statusState, result.requestedBuildId (), result.activeBuildId (), result.message ()));
        }
        catch (final IOException | CoreLoadException failure)
        {
            final String message = sanitize (failure);
            this.host.errorln (LOG_PREFIX + "Core " + candidate.buildId () + " was rejected: " + message);
            this.watcher.publishStatus (candidate.requestGeneration (), new RuntimeStatus (RuntimeStatus.State.FAILED, candidate.buildId (), this.runtimeManager.activeBuildId (), message));
        }
        finally
        {
            if (!retained)
                this.watcher.release (candidate);
        }
    }


    private void acknowledgeRejection (final CoreCandidateWatcher.RejectedCandidate rejection)
    {
        this.watcher.publishStatus (rejection.requestGeneration (), new RuntimeStatus (rejection.state (), rejection.requestedBuildId (), this.runtimeManager.activeBuildId (), rejection.message ()));
    }


    private void reportWatcherNotice ()
    {
        this.watcher.takeNotice ().ifPresent (notice -> {
            if (notice.warning ())
                this.host.errorln (LOG_PREFIX + notice.message ());
            else
                this.host.println (LOG_PREFIX + notice.message ());
        });
    }


    private static String sanitize (final Throwable failure)
    {
        final String detail = failure.getMessage ();
        return failure.getClass ().getSimpleName () + (detail == null || detail.isBlank () ? "" : ": " + detail);
    }


    private static final class HostRuntimeLog implements RuntimeLog
    {
        private final ControllerHost host;


        private HostRuntimeLog (final ControllerHost host)
        {
            this.host = host;
        }


        /** {@inheritDoc} */
        @Override
        public void info (final String message)
        {
            this.host.println (LOG_PREFIX + message);
        }


        /** {@inheritDoc} */
        @Override
        public void warn (final String message)
        {
            this.host.errorln (LOG_PREFIX + message);
        }
    }
}
