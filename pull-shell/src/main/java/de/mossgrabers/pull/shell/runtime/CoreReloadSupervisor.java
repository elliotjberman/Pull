// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.MixerControlsSnapshot;
import de.mossgrabers.pull.core.api.output.MixerControlsDisplay;

import java.io.IOException;
import java.util.Objects;

/**
 * Live-reload owner driven from Bitwig's controller thread.
 */
final class CoreReloadSupervisor implements AutoCloseable
{
    private final RuntimeLog log;
    private final CoreJarLoader jarLoader;
    private final RuntimeManager runtimeManager;
    private final CoreCandidateWatcher watcher;
    private PreparedCoreCandidate activeCandidate;
    private PreparedCoreCandidate pendingCandidate;
    private boolean started;
    private boolean closed;


    CoreReloadSupervisor (final CoreRuntimeEnvironment environment, final RuntimeLog log, final RuntimeTraceSink trace)
    {
        this.log = Objects.requireNonNull (log, "log");
        this.jarLoader = new CoreJarLoader ();
        this.runtimeManager = new RuntimeManager (Objects.requireNonNull (environment, "environment"), log, trace);
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
        this.watcher.takeCandidate ().ifPresent (this::queueCandidate);
        if (this.pendingCandidate != null && this.runtimeManager.canReplaceActiveCore ())
        {
            final PreparedCoreCandidate candidate = this.pendingCandidate;
            this.pendingCandidate = null;
            this.activate (candidate);
        }
    }


    /**
     * Deliver a controller-thread event to the currently active generation.
     *
     * @param event The event
     * @return True when the event reached the active core and was committed
     */
    boolean handle (final CoreEvent event)
    {
        return this.handle (this.activeGeneration (), event);
    }


    /**
     * Deliver an input event only to the generation which owned its physical gesture.
     *
     * @param eventGeneration Captured input-owner generation
     * @param event The event
     * @return True when the event reached that active generation
     */
    boolean handle (final long eventGeneration, final CoreEvent event)
    {
        Objects.requireNonNull (event, "event");
        if (!this.started || this.closed)
            return false;
        return this.runtimeManager.handle (eventGeneration, event);
    }


    long activeGeneration ()
    {
        return this.started && !this.closed ? this.runtimeManager.activeGeneration () : 0;
    }


    MixerControlsDisplay renderMixerControls (final MixerControlsSnapshot snapshot)
    {
        if (!this.started || this.closed)
            return MixerControlsDisplay.empty ();
        return this.runtimeManager.renderMixerControls (Objects.requireNonNull (snapshot, "snapshot"));
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
            this.watcher.release (this.pendingCandidate);
            this.pendingCandidate = null;
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
            if (result.state () == ActivationResult.State.BLOCKED)
            {
                this.queueCandidate (candidate);
                retained = true;
                return;
            }

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
            this.warn ("Core " + candidate.buildId () + " was rejected: " + message);
            this.watcher.publishStatus (candidate.requestGeneration (), new RuntimeStatus (RuntimeStatus.State.FAILED, candidate.buildId (), this.runtimeManager.activeBuildId (), message));
        }
        finally
        {
            if (!retained)
                this.watcher.release (candidate);
        }
    }


    private void queueCandidate (final PreparedCoreCandidate candidate)
    {
        final PreparedCoreCandidate previous = this.pendingCandidate;
        this.pendingCandidate = Objects.requireNonNull (candidate, "candidate");
        this.watcher.release (previous);
    }


    private void acknowledgeRejection (final CoreCandidateWatcher.RejectedCandidate rejection)
    {
        this.watcher.publishStatus (rejection.requestGeneration (), new RuntimeStatus (rejection.state (), rejection.requestedBuildId (), this.runtimeManager.activeBuildId (), rejection.message ()));
    }


    private void reportWatcherNotice ()
    {
        this.watcher.takeNotice ().ifPresent (notice -> {
            if (notice.warning ())
                this.warn (notice.message ());
            else
                this.info (notice.message ());
        });
    }


    private void info (final String message)
    {
        try
        {
            this.log.info (message);
        }
        catch (final RuntimeException ignored)
        {
            // Logging must never alter the watcher lifecycle.
        }
    }


    private void warn (final String message)
    {
        try
        {
            this.log.warn (message);
        }
        catch (final RuntimeException ignored)
        {
            // Logging must never alter candidate ownership or status publication.
        }
    }


    private static String sanitize (final Throwable failure)
    {
        final String detail = failure.getMessage ();
        return failure.getClass ().getSimpleName () + (detail == null || detail.isBlank () ? "" : ": " + detail);
    }
}
