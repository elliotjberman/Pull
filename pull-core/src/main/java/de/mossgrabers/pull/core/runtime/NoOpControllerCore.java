// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.event.CoreEvent;

import java.util.Objects;
import java.util.Optional;

/**
 * Inert core used to prove the lifecycle and packaging seam.
 */
final class NoOpControllerCore implements ControllerCore
{
    private Lifecycle lifecycle = Lifecycle.NEW;


    /** {@inheritDoc} */
    @Override
    public CoreResult start (final ControllerSnapshot snapshot, final Optional<StateEnvelope> previousState)
    {
        Objects.requireNonNull (snapshot, "snapshot");
        Objects.requireNonNull (previousState, "previousState");
        if (this.lifecycle != Lifecycle.NEW)
            throw new IllegalStateException ("Core can only be started once");

        previousState.ifPresent (NoOpControllerCore::validateCheckpoint);
        this.lifecycle = Lifecycle.RUNNING;
        return CoreResult.empty ();
    }


    /** {@inheritDoc} */
    @Override
    public CoreResult handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.requireRunning ();
        Objects.requireNonNull (event, "event");
        Objects.requireNonNull (snapshot, "snapshot");
        return CoreResult.empty ();
    }


    /** {@inheritDoc} */
    @Override
    public StateEnvelope checkpoint ()
    {
        this.requireRunning ();
        return new StateEnvelope (CanaryCoreProvider.STATE_SCHEMA, CanaryCoreProvider.STATE_SCHEMA_VERSION, new byte [0]);
    }


    /** {@inheritDoc} */
    @Override
    public void stop ()
    {
        this.lifecycle = Lifecycle.STOPPED;
    }


    private static void validateCheckpoint (final StateEnvelope checkpoint)
    {
        if (!CanaryCoreProvider.STATE_SCHEMA.equals (checkpoint.schema ()) || CanaryCoreProvider.STATE_SCHEMA_VERSION != checkpoint.version ())
            return;
        if (checkpoint.payload ().length != 0)
            throw new IllegalArgumentException ("Canary checkpoint payload must be empty");
    }


    private void requireRunning ()
    {
        if (this.lifecycle != Lifecycle.RUNNING)
            throw new IllegalStateException ("Core is not running");
    }


    private enum Lifecycle
    {
        NEW,
        RUNNING,
        STOPPED
    }
}
