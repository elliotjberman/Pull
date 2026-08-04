// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.ControllerCore;
import de.mossgrabers.pull.core.api.ControllerSnapshot;
import de.mossgrabers.pull.core.api.CoreResult;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.api.event.CoreEvent;
import de.mossgrabers.pull.core.runtime.view.ControllerWorkspaceView;
import de.mossgrabers.pull.core.runtime.view.DefaultWorkspace;
import de.mossgrabers.pull.core.view.CompiledWorkspace;

import java.util.Objects;
import java.util.Optional;


/**
 * Reloadable Pull behavior. The stable shell owns physical mappings and all effect execution.
 */
final class PullControllerCore implements ControllerCore
{
    private CompiledWorkspace       workspace;
    private ControllerWorkspaceView controllerWorkspaceView;
    private Lifecycle               lifecycle = Lifecycle.NEW;


    /** {@inheritDoc} */
    @Override
    public CoreResult start (final ControllerSnapshot snapshot, final Optional<StateEnvelope> previousState)
    {
        Objects.requireNonNull (snapshot, "snapshot");
        Objects.requireNonNull (previousState, "previousState");
        if (this.lifecycle != Lifecycle.NEW)
            throw new IllegalStateException ("Core can only be started once");

        this.controllerWorkspaceView = new ControllerWorkspaceView (restoreWorkspace (previousState));
        this.workspace = DefaultWorkspace.create (this.controllerWorkspaceView);
        this.lifecycle = Lifecycle.RUNNING;
        return this.workspace.start (snapshot);
    }


    /** {@inheritDoc} */
    @Override
    public CoreResult handle (final CoreEvent event, final ControllerSnapshot snapshot)
    {
        this.requireRunning ();
        Objects.requireNonNull (event, "event");
        Objects.requireNonNull (snapshot, "snapshot");
        return this.workspace.handle (event, snapshot);
    }


    /** {@inheritDoc} */
    @Override
    public StateEnvelope checkpoint ()
    {
        this.requireRunning ();
        return new StateEnvelope (
            PullCoreProvider.STATE_SCHEMA,
            PullCoreProvider.STATE_SCHEMA_VERSION,
            new byte []
            {
                (byte) (this.controllerWorkspaceView.isActive () ? 1 : 0)
            });
    }


    private static boolean restoreWorkspace (final Optional<StateEnvelope> previousState)
    {
        if (previousState.isEmpty ())
            return false;
        final byte [] payload = previousState.get ().payload ();
        return payload.length == 1 && payload[0] == 1;
    }


    private void requireRunning ()
    {
        if (this.lifecycle != Lifecycle.RUNNING)
            throw new IllegalStateException ("Core is not running");
    }


    private enum Lifecycle
    {
        NEW,
        RUNNING
    }
}
