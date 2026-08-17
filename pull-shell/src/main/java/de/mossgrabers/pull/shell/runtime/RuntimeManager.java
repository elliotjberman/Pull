// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreApi;
import de.mossgrabers.pull.core.api.CoreDescriptor;
import de.mossgrabers.pull.core.api.CoreProvider;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.MixerControlSnapshot;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.MixerControlsSnapshot;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.api.output.MixerControlDisplay;
import de.mossgrabers.pull.core.api.output.MixerControlsDisplay;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Serial controller-thread owner of the active reloadable core.
 */
final class RuntimeManager implements AutoCloseable
{
    private static final long SLOW_EVENT_NANOS = 8_000_000;
    private static final long SLOW_WARNING_INTERVAL_NANOS = 5_000_000_000L;

    private final CoreRuntimeEnvironment environment;
    private final RuntimeLog log;
    private final RuntimeTraceSink trace;
    private Lifecycle lifecycle = Lifecycle.NEW;
    private Thread controllerThread;
    private ActiveCore active;
    private long generation;
    private long lastSlowEventWarningNanos = Long.MIN_VALUE;


    RuntimeManager (final CoreRuntimeEnvironment environment, final RuntimeLog log)
    {
        this (environment, log, null);
    }


    RuntimeManager (final CoreRuntimeEnvironment environment, final RuntimeLog log, final RuntimeTraceSink trace)
    {
        this.environment = Objects.requireNonNull (environment, "environment");
        this.log = Objects.requireNonNull (log, "log");
        this.trace = trace;
    }


    /**
     * Bind this manager to the current Bitwig controller thread.
     */
    void start ()
    {
        if (this.lifecycle != Lifecycle.NEW)
            throw new IllegalStateException ("RuntimeManager can only be started once");

        this.controllerThread = Thread.currentThread ();
        this.lifecycle = Lifecycle.RUNNING;
    }


    /**
     * Transactionally activate a verified candidate.
     *
     * @param expectedBuildId Build ID from the parent-owned manifest
     * @param source Candidate provider source
     * @param isLatest True while this request is still the newest observed manifest
     * @return The activation result
     */
    ActivationResult activate (final String expectedBuildId, final CoreProviderSource source, final BooleanSupplier isLatest)
    {
        this.requireRunningOnControllerThread ();
        Objects.requireNonNull (expectedBuildId, "expectedBuildId");
        Objects.requireNonNull (source, "source");
        Objects.requireNonNull (isLatest, "isLatest");

        if (!this.environment.canReplaceActiveCore ())
        {
            this.closeCandidate (source);
            return new ActivationResult (ActivationResult.State.BLOCKED, expectedBuildId, this.activeBuildId (), "Waiting for the active semantic transaction");
        }

        final long nextGeneration = Math.incrementExact (this.generation);
        ControllerCore candidateCore = null;
        CoreDescriptor descriptor;
        PreparedCoreResult preparedStartupResult;
        try
        {
            if (!isLatest.getAsBoolean ())
                return this.supersede (expectedBuildId, source);

            final CoreProvider provider = source.instantiateProvider ();
            descriptor = source.invokeWithContext (provider::descriptor);
            this.validateDescriptor (expectedBuildId, descriptor);

            final Optional<StateEnvelope> previousState = this.compatibleCheckpoint (descriptor);
            final ControllerSnapshot snapshot = this.environment.snapshot ();
            candidateCore = source.invokeWithContext (provider::create);
            if (candidateCore == null)
                throw new IllegalStateException ("CoreProvider.create returned null");

            final ControllerCore startedCore = candidateCore;
            final CoreResult startupResult = source.invokeWithContext ( () -> startedCore.start (snapshot, previousState));
            final CoreResult checkedStartupResult = Objects.requireNonNull (startupResult, "ControllerCore.start result");
            preparedStartupResult = this.environment.prepare (checkedStartupResult);
            Objects.requireNonNull (preparedStartupResult, "CoreRuntimeEnvironment.prepare result");
            this.traceStartup (nextGeneration, expectedBuildId, snapshot, checkedStartupResult);

            if (!isLatest.getAsBoolean ())
            {
                this.closeCandidate (source);
                return new ActivationResult (ActivationResult.State.SUPERSEDED, expectedBuildId, this.activeBuildId (), "Superseded by a newer build");
            }

        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.traceFailure (nextGeneration, "STARTUP", failure);
            this.closeCandidate (source);
            final String message = sanitize (failure);
            this.warn ("Core " + expectedBuildId + " was rejected: " + message);
            return new ActivationResult (ActivationResult.State.FAILED, expectedBuildId, this.activeBuildId (), message);
        }

        final ActiveCore previous = this.active;
        try
        {
            this.environment.commit (nextGeneration, preparedStartupResult);
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.traceFailure (nextGeneration, "COMMIT", failure);
            this.closeCandidate (source);
            final String message = "Atomic shell state commit failed: " + sanitize (failure);
            this.warn ("Core " + expectedBuildId + " was rejected: " + message);
            return new ActivationResult (ActivationResult.State.FAILED, expectedBuildId, this.activeBuildId (), message);
        }

        this.active = new ActiveCore (descriptor, source, candidateCore, nextGeneration);
        this.generation = nextGeneration;
        this.applyCommittedResult (nextGeneration);
        this.closeActive (previous);
        this.traceLifecycle (nextGeneration, "CORE_ACTIVATED", descriptor.buildId ());
        this.info ("Activated reloadable core " + descriptor.buildId ());
        return new ActivationResult (ActivationResult.State.ACTIVE, expectedBuildId, descriptor.buildId (), "Activated");
    }


    /**
     * Deliver an event only to the generation that scheduled it.
     *
     * @param eventGeneration The captured active generation
     * @param event The event
     * @return True when the event reached the active core
     */
    boolean handle (final long eventGeneration, final CoreEvent event)
    {
        this.requireRunningOnControllerThread ();
        Objects.requireNonNull (event, "event");
        final ActiveCore runtime = this.active;
        if (runtime == null || runtime.quarantined || runtime.generation != eventGeneration)
            return false;

        final long startedAt = System.nanoTime ();
        final ControllerSnapshot snapshot;
        try
        {
            snapshot = this.environment.snapshot ();
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.traceFailure (runtime.generation, "SNAPSHOT", failure);
            this.warn ("Controller snapshot failed; retained the last committed core result: " + sanitize (failure));
            this.reportSlowEvent (event, startedAt);
            return false;
        }

        final CoreResult result;
        try
        {
            result = Objects.requireNonNull (runtime.source.invokeWithContext ( () -> runtime.core.handle (event, snapshot)), "ControllerCore.handle result");
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.traceFailure (runtime.generation, "HANDLE", failure);
            this.quarantineActive (runtime, sanitize (failure));
            this.reportSlowEvent (event, startedAt);
            return false;
        }

        final PreparedCoreResult preparedResult;
        try
        {
            preparedResult = Objects.requireNonNull (this.environment.prepare (result), "CoreRuntimeEnvironment.prepare result");
            this.traceTransaction (runtime.generation, event, snapshot, result);
            this.environment.commit (runtime.generation, preparedResult);
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.traceFailure (runtime.generation, "PREPARE_OR_COMMIT", failure);
            this.quarantineActive (runtime, "Rejected result after child mutation: " + sanitize (failure));
            this.reportSlowEvent (event, startedAt);
            return false;
        }

        this.applyCommittedResult (runtime.generation);
        this.reportSlowEvent (event, startedAt);
        return true;
    }


    /** Purely render a stable-data mixer overlay through the active child generation. */
    MixerControlsDisplay renderMixerControls (final MixerControlsSnapshot snapshot)
    {
        this.requireRunningOnControllerThread ();
        final MixerControlsSnapshot checkedSnapshot = Objects.requireNonNull (snapshot, "snapshot");
        final ActiveCore runtime = this.active;
        if (runtime == null || runtime.quarantined)
            return MixerControlsDisplay.empty ();
        final MixerControlsDisplay result;
        try
        {
            result = Objects.requireNonNull (runtime.source.invokeWithContext ( () -> runtime.core.renderMixerControls (checkedSnapshot)), "ControllerCore.renderMixerControls result");
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.quarantineActive (runtime, sanitize (failure));
            return MixerControlsDisplay.empty ();
        }

        try
        {
            for (final MixerControlDisplay control: result.controls ())
            {
                final MixerControlSnapshot requested = checkedSnapshot.controls ().stream ().filter (candidate -> candidate.column () == control.column ()).findFirst ().orElseThrow ( () -> new IllegalArgumentException ("Mixer renderer returned an unrequested column"));
                if (requested.kind () != control.kind ())
                    throw new IllegalArgumentException ("Mixer renderer changed the requested control kind");
            }
            return result;
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.warn ("Rejected mixer rendering from active core " + runtime.descriptor.buildId () + "; retained the active core: " + sanitize (failure));
            return MixerControlsDisplay.empty ();
        }
    }


    String activeBuildId ()
    {
        return this.active == null ? "" : this.active.descriptor.buildId ();
    }


    long activeGeneration ()
    {
        return this.active == null ? 0 : this.active.generation;
    }


    /** Test whether a candidate may replace the active core without splitting a transaction. */
    boolean canReplaceActiveCore ()
    {
        this.requireRunningOnControllerThread ();
        return this.environment.canReplaceActiveCore ();
    }


    /** {@inheritDoc} */
    @Override
    public void close ()
    {
        if (this.lifecycle == Lifecycle.CLOSED)
            return;
        if (this.lifecycle == Lifecycle.NEW)
        {
            this.lifecycle = Lifecycle.CLOSED;
            return;
        }

        this.requireControllerThread ();
        this.lifecycle = Lifecycle.CLOSING;
        this.generation = Math.incrementExact (this.generation);
        final ActiveCore previous = this.active;
        this.active = null;
        try
        {
            this.environment.invalidate (this.generation);
            this.traceLifecycle (this.generation, "RUNTIME_CLOSED", "stable generation invalidated");
        }
        catch (final RuntimeException failure)
        {
            this.traceFailure (this.generation, "INVALIDATE", failure);
            this.warn ("Core generation invalidation failed: " + sanitize (failure));
        }
        finally
        {
            this.closeActive (previous);
            this.lifecycle = Lifecycle.CLOSED;
        }
    }


    private void validateDescriptor (final String expectedBuildId, final CoreDescriptor descriptor)
    {
        Objects.requireNonNull (descriptor, "CoreProvider.descriptor result");
        if (descriptor.apiVersion () != CoreApi.VERSION)
            throw new IllegalArgumentException ("Core API version " + descriptor.apiVersion () + " does not match shell API " + CoreApi.VERSION);
        if (!expectedBuildId.equals (descriptor.buildId ()))
            throw new IllegalArgumentException ("Core descriptor build ID does not match manifest build ID " + expectedBuildId);

        final ControllerSnapshot snapshot = this.environment.snapshot ();
        if (!snapshot.capabilities ().supports (descriptor.requiredCapabilities ()))
            throw new IllegalArgumentException ("Core requires shell capabilities that are not available");
    }


    private Optional<StateEnvelope> compatibleCheckpoint (final CoreDescriptor candidateDescriptor)
    {
        if (this.active == null || this.active.quarantined)
            return Optional.empty ();

        try
        {
            final StateEnvelope checkpoint = this.active.source.invokeWithContext (this.active.core::checkpoint);
            if (checkpoint == null)
                throw new IllegalStateException ("ControllerCore.checkpoint returned null");
            if (!candidateDescriptor.stateSchema ().equals (checkpoint.schema ()) || candidateDescriptor.stateSchemaVersion () != checkpoint.version ())
                return Optional.empty ();
            return Optional.of (checkpoint);
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.warn ("Active core checkpoint was discarded: " + sanitize (failure));
            return Optional.empty ();
        }
    }


    private ActivationResult supersede (final String expectedBuildId, final CoreProviderSource source)
    {
        closeSource (source, this.log);
        return new ActivationResult (ActivationResult.State.SUPERSEDED, expectedBuildId, this.activeBuildId (), "Superseded by a newer build");
    }


    private void closeCandidate (final CoreProviderSource source)
    {
        closeSource (source, this.log);
    }


    private void closeActive (final ActiveCore runtime)
    {
        if (runtime == null)
            return;
        closeSource (runtime.source, this.log);
    }


    private void quarantineActive (final ActiveCore runtime, final String message)
    {
        if (this.active != runtime || runtime.quarantined)
            return;
        runtime.quarantined = true;
        this.traceLifecycle (runtime.generation, "CORE_QUARANTINED", message);
        try
        {
            this.environment.quarantine (runtime.generation);
        }
        catch (final RuntimeException quarantineFailure)
        {
            this.warn ("Core quarantine cleanup failed: " + sanitize (quarantineFailure));
        }
        this.warn ("Quarantined reloadable core " + runtime.descriptor.buildId () + " while retaining its last committed output: " + message);
    }


    private void applyCommittedResult (final long committedGeneration)
    {
        try
        {
            this.environment.apply (committedGeneration);
            this.traceApplied (committedGeneration);
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.traceFailure (committedGeneration, "APPLY", failure);
            this.warn ("Committed core effects failed: " + sanitize (failure));
        }
    }


    private void traceTransaction (final long traceGeneration, final CoreEvent event, final ControllerSnapshot snapshot, final CoreResult result)
    {
        if (this.trace == null)
            return;
        try
        {
            this.trace.transaction (traceGeneration, event, snapshot, result);
        }
        catch (final RuntimeException ignored)
        {
            // Optional diagnostics must not alter the runtime transaction.
        }
    }


    private void traceStartup (final long traceGeneration, final String buildID, final ControllerSnapshot snapshot, final CoreResult result)
    {
        if (this.trace == null)
            return;
        try
        {
            this.trace.startup (traceGeneration, buildID, snapshot, result);
        }
        catch (final RuntimeException ignored)
        {
            // Optional diagnostics must not alter candidate activation.
        }
    }


    private void traceApplied (final long traceGeneration)
    {
        if (this.trace == null)
            return;
        try
        {
            this.trace.applied (traceGeneration);
        }
        catch (final RuntimeException ignored)
        {
            // Optional diagnostics must not alter effect submission.
        }
    }


    private void traceLifecycle (final long traceGeneration, final String state, final String detail)
    {
        if (this.trace == null)
            return;
        try
        {
            this.trace.lifecycle (traceGeneration, state, detail);
        }
        catch (final RuntimeException ignored)
        {
            // Optional diagnostics must not alter lifecycle state.
        }
    }


    private void traceFailure (final long traceGeneration, final String stage, final Throwable failure)
    {
        if (this.trace == null)
            return;
        try
        {
            this.trace.failure (traceGeneration, stage, sanitize (failure));
        }
        catch (final RuntimeException ignored)
        {
            // Optional diagnostics must not replace the original failure.
        }
    }


    private void reportSlowEvent (final CoreEvent event, final long startedAt)
    {
        final long now = System.nanoTime ();
        final long elapsed = now - startedAt;
        if (elapsed <= SLOW_EVENT_NANOS || this.lastSlowEventWarningNanos != Long.MIN_VALUE && now - this.lastSlowEventWarningNanos < SLOW_WARNING_INTERVAL_NANOS)
            return;

        this.lastSlowEventWarningNanos = now;
        this.warn ("Reloadable bridge " + event.getClass ().getSimpleName () + " transaction took " + elapsed / 1_000_000.0 + " ms");
    }


    private void requireRunningOnControllerThread ()
    {
        if (this.lifecycle != Lifecycle.RUNNING)
            throw new IllegalStateException ("RuntimeManager is not running");
        this.requireControllerThread ();
    }


    private void requireControllerThread ()
    {
        if (Thread.currentThread () != this.controllerThread)
            throw new IllegalStateException ("RuntimeManager must be called on the controller thread");
    }


    private static void closeSource (final CoreProviderSource source, final RuntimeLog log)
    {
        try
        {
            source.close ();
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            safeWarn (log, "Core classloader close failed: " + sanitize (failure));
        }
    }


    private void info (final String message)
    {
        try
        {
            this.log.info (message);
        }
        catch (final RuntimeException ignored)
        {
            // Logging must never alter a completed runtime transaction.
        }
    }


    private void warn (final String message)
    {
        safeWarn (this.log, message);
    }


    private static void safeWarn (final RuntimeLog log, final String message)
    {
        try
        {
            log.warn (message);
        }
        catch (final RuntimeException ignored)
        {
            // Logging must never alter runtime ownership or cleanup.
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


    private static final class ActiveCore
    {
        private final CoreDescriptor descriptor;
        private final CoreProviderSource source;
        private final ControllerCore core;
        private final long generation;
        private boolean quarantined;


        private ActiveCore (final CoreDescriptor descriptor, final CoreProviderSource source, final ControllerCore core, final long generation)
        {
            this.descriptor = descriptor;
            this.source = source;
            this.core = core;
            this.generation = generation;
        }
    }


    private enum Lifecycle
    {
        NEW,
        RUNNING,
        CLOSING,
        CLOSED
    }
}
