// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreApi;
import de.mossgrabers.pull.core.api.CoreDescriptor;
import de.mossgrabers.pull.core.api.CoreProvider;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.event.CoreEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;

/**
 * Serial controller-thread owner of the active reloadable core.
 */
final class RuntimeManager implements AutoCloseable
{
    private static final long SLOW_STOP_NANOS = 10_000_000;

    private final CoreRuntimeEnvironment environment;
    private final RuntimeLog log;
    private Lifecycle lifecycle = Lifecycle.NEW;
    private Thread controllerThread;
    private ActiveCore active;
    private long generation;


    RuntimeManager (final CoreRuntimeEnvironment environment, final RuntimeLog log)
    {
        this.environment = Objects.requireNonNull (environment, "environment");
        this.log = Objects.requireNonNull (log, "log");
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

        ControllerCore candidateCore = null;
        CoreDescriptor descriptor;
        CoreResult startupResult;
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
            startupResult = source.invokeWithContext ( () -> startedCore.start (snapshot, previousState));
            this.environment.validate (Objects.requireNonNull (startupResult, "ControllerCore.start result"));

            if (!isLatest.getAsBoolean ())
            {
                this.stopCandidate (source, candidateCore);
                return new ActivationResult (ActivationResult.State.SUPERSEDED, expectedBuildId, this.activeBuildId (), "Superseded by a newer build");
            }

        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.stopCandidate (source, candidateCore);
            final String message = sanitize (failure);
            this.warn ("Core " + expectedBuildId + " was rejected: " + message);
            return new ActivationResult (ActivationResult.State.FAILED, expectedBuildId, this.activeBuildId (), message);
        }

        final long nextGeneration = Math.incrementExact (this.generation);
        final ActiveCore previous = this.active;
        try
        {
            this.environment.commit (nextGeneration, startupResult);
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.stopCandidate (source, candidateCore);
            final String message = "Atomic shell state commit failed: " + sanitize (failure);
            this.warn ("Core " + expectedBuildId + " was rejected: " + message);
            return new ActivationResult (ActivationResult.State.FAILED, expectedBuildId, this.activeBuildId (), message);
        }

        this.active = new ActiveCore (descriptor, source, candidateCore, nextGeneration);
        this.generation = nextGeneration;
        this.stopActive (previous);
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
        if (this.active == null || this.active.generation != eventGeneration)
            return false;

        try
        {
            final ControllerSnapshot snapshot = this.environment.snapshot ();
            final CoreResult result = this.active.source.invokeWithContext ( () -> this.active.core.handle (event, snapshot));
            this.environment.validate (Objects.requireNonNull (result, "ControllerCore.handle result"));
            this.environment.commit (this.active.generation, result);
            return true;
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.warn ("Active core event failed: " + sanitize (failure));
            return false;
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
        }
        catch (final RuntimeException failure)
        {
            this.warn ("Core generation invalidation failed: " + sanitize (failure));
        }
        finally
        {
            this.stopActive (previous);
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
        if (this.active == null)
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


    private void stopCandidate (final CoreProviderSource source, final ControllerCore core)
    {
        if (core != null)
        {
            try
            {
                source.invokeWithContext ( () -> {
                    core.stop ();
                    return null;
                });
            }
            catch (final Throwable failure)
            {
                rethrowFatal (failure);
                this.warn ("Rejected core stop failed: " + sanitize (failure));
            }
        }
        closeSource (source, this.log);
    }


    private void stopActive (final ActiveCore runtime)
    {
        if (runtime == null)
            return;

        final long startedAt = System.nanoTime ();
        try
        {
            runtime.source.invokeWithContext ( () -> {
                runtime.core.stop ();
                return null;
            });
        }
        catch (final Throwable failure)
        {
            rethrowFatal (failure);
            this.warn ("Previous core stop failed: " + sanitize (failure));
        }
        finally
        {
            closeSource (runtime.source, this.log);
            final long elapsed = System.nanoTime () - startedAt;
            if (elapsed > SLOW_STOP_NANOS)
                this.warn ("Previous core stop took " + elapsed / 1_000_000 + " ms");
        }
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


    private record ActiveCore (CoreDescriptor descriptor, CoreProviderSource source, ControllerCore core, long generation)
    {
    }


    private enum Lifecycle
    {
        NEW,
        RUNNING,
        CLOSING,
        CLOSED
    }
}
