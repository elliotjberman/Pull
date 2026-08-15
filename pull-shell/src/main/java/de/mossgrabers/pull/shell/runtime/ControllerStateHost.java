// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.workspace.ControllerWorkspaceHost;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.pull.core.api.DesiredControllerLayout;
import de.mossgrabers.pull.core.api.DesiredControllerState;
import de.mossgrabers.pull.core.api.DesiredControllerWorkspace;
import de.mossgrabers.pull.core.api.DesiredNoteInputRoute;
import de.mossgrabers.pull.core.api.DesiredNotePerformance;

import java.util.Objects;
import java.util.function.BooleanSupplier;


/** Realizes one composed view state through the shared workspace, layout, and Note-route actuators. */
final class ControllerStateHost
{
    private final ISelectedTrackNoteTarget selectedTarget;
    private final Surface surface;
    private final Runnable routeNeutralizer;

    private DesiredControllerState desired = DesiredControllerState.empty ();
    private DesiredControllerState applied = DesiredControllerState.empty ();
    private DesiredNoteInputRoute submittedRoute = DesiredNoteInputRoute.disabled ();
    private DesiredControllerLayout commandedLayout = DesiredControllerLayout.empty ();
    private BooleanSupplier inputLifecycleIdle = () -> true;
    private boolean quarantinedUntilInputIdle;


    ControllerStateHost (final ISelectedTrackNoteTarget selectedTarget, final ControllerWorkspaceHost workspaceHost, final Runnable routeNeutralizer)
    {
        this (selectedTarget, new WorkspaceSurface (workspaceHost), routeNeutralizer);
    }


    ControllerStateHost (final ISelectedTrackNoteTarget selectedTarget, final Surface surface, final Runnable routeNeutralizer)
    {
        this.selectedTarget = Objects.requireNonNull (selectedTarget, "selectedTarget");
        this.surface = Objects.requireNonNull (surface, "surface");
        this.routeNeutralizer = Objects.requireNonNull (routeNeutralizer, "routeNeutralizer");
    }


    void setInputLifecycleIdle (final BooleanSupplier idle)
    {
        this.inputLifecycleIdle = Objects.requireNonNull (idle, "idle");
    }


    DesiredControllerState prepare (final DesiredControllerState state)
    {
        final DesiredControllerState requested = Objects.requireNonNull (state, "state");
        final DesiredNotePerformance performance = requested.notePerformance ();
        return new DesiredControllerState (
            this.surface.prepareWorkspace (requested.workspace ()),
            new DesiredNotePerformance (this.surface.prepareLayout (performance.layout ()), performance.inputRoute ()));
    }


    void apply (final DesiredControllerState state)
    {
        this.desired = Objects.requireNonNull (state, "state");
        this.reconcile (true);
    }


    void refresh ()
    {
        this.reconcile (false);
    }


    void invalidate ()
    {
        this.desired = DesiredControllerState.empty ();
        this.quarantinedUntilInputIdle = false;
        this.failClosed (null);
    }


    private void reconcile (final boolean reassertLayout)
    {
        final DesiredNoteInputRoute requested = this.desired.notePerformance ().inputRoute ();
        if (this.submittedRoute.active () && !this.liveTargetMatches (this.submittedRoute))
        {
            this.failClosed (null);
            this.quarantinedUntilInputIdle = !this.inputLifecycleIdle.getAsBoolean ();
            if (requested.active () && !this.liveTargetMatches (requested))
                return;
        }

        if (requested.active ())
        {
            if (!this.liveTargetMatches (requested))
            {
                this.failClosed (null);
                this.quarantinedUntilInputIdle = !this.inputLifecycleIdle.getAsBoolean ();
                return;
            }
            if (this.quarantinedUntilInputIdle && !this.inputLifecycleIdle.getAsBoolean ())
            {
                this.applyLayout (DesiredControllerLayout.neutral (), reassertLayout);
                return;
            }

            this.quarantinedUntilInputIdle = false;
            final boolean submitting = !requested.equals (this.submittedRoute);
            if (submitting)
            {
                this.selectedTarget.submitNoteInputRoute (true);
                this.submittedRoute = requested;
            }
            try
            {
                this.activateDesiredSurface (reassertLayout || submitting);
            }
            catch (final RuntimeException failure)
            {
                this.desired = DesiredControllerState.empty ();
                this.failClosed (failure);
                throw failure;
            }
            return;
        }

        if (this.submittedRoute.active ())
        {
            try
            {
                this.applyLayout (DesiredControllerLayout.neutral (), reassertLayout);
            }
            catch (final RuntimeException failure)
            {
                this.desired = DesiredControllerState.empty ();
                this.failClosed (failure);
                throw failure;
            }
            if (!this.inputLifecycleIdle.getAsBoolean ())
            {
                this.quarantinedUntilInputIdle = true;
                return;
            }
            this.detach ();
        }
        this.quarantinedUntilInputIdle = false;
        this.activateDesiredSurface (reassertLayout);
    }


    private void activateDesiredSurface (final boolean reassertLayout)
    {
        if (!reassertLayout && this.desired.equals (this.applied))
            return;
        this.surface.applyWorkspace (this.desired.workspace ());
        this.applyLayout (this.desired.notePerformance ().layout (), reassertLayout);
        this.applied = this.desired;
    }


    private boolean liveTargetMatches (final DesiredNoteInputRoute route)
    {
        return route.active () && route.targetGeneration () == this.selectedTarget.getGeneration () && route.targetChannelId ().equals (this.selectedTarget.getChannelID ()) && this.selectedTarget.doesExist () && this.selectedTarget.canHoldNotes ();
    }


    private void failClosed (final RuntimeException original)
    {
        RuntimeException failure = original;
        this.applied = DesiredControllerState.empty ();
        try
        {
            this.applyLayout (DesiredControllerLayout.neutral (), true);
        }
        catch (final RuntimeException cleanupFailure)
        {
            failure = retain (failure, cleanupFailure);
        }
        try
        {
            this.detach ();
        }
        catch (final RuntimeException cleanupFailure)
        {
            failure = retain (failure, cleanupFailure);
        }
        try
        {
            this.surface.invalidate ();
        }
        catch (final RuntimeException cleanupFailure)
        {
            failure = retain (failure, cleanupFailure);
        }
        if (original == null && failure != null)
            throw failure;
    }


    private static RuntimeException retain (final RuntimeException original, final RuntimeException cleanupFailure)
    {
        if (original == null)
            return cleanupFailure;
        if (cleanupFailure != original)
            original.addSuppressed (cleanupFailure);
        return original;
    }


    private void detach ()
    {
        if (!this.submittedRoute.active ())
            return;
        try
        {
            this.routeNeutralizer.run ();
        }
        finally
        {
            this.selectedTarget.submitNoteInputRoute (false);
            this.submittedRoute = DesiredNoteInputRoute.disabled ();
        }
    }


    private void applyLayout (final DesiredControllerLayout layout, final boolean reassert)
    {
        if (!reassert && layout.equals (this.commandedLayout))
            return;
        this.surface.applyLayout (layout);
        this.commandedLayout = layout;
    }


    /** Single stable actuator used by the composed-state lifecycle. */
    interface Surface
    {
        DesiredControllerWorkspace prepareWorkspace (DesiredControllerWorkspace workspace);

        DesiredControllerLayout prepareLayout (DesiredControllerLayout layout);

        void applyWorkspace (DesiredControllerWorkspace workspace);

        void applyLayout (DesiredControllerLayout layout);

        void invalidate ();
    }


    private record WorkspaceSurface (ControllerWorkspaceHost host) implements Surface
    {
        private WorkspaceSurface
        {
            Objects.requireNonNull (host, "host");
        }


        @Override
        public DesiredControllerWorkspace prepareWorkspace (final DesiredControllerWorkspace workspace)
        {
            return this.host.prepare (workspace);
        }


        @Override
        public DesiredControllerLayout prepareLayout (final DesiredControllerLayout layout)
        {
            return this.host.prepareLayout (layout);
        }


        @Override
        public void applyWorkspace (final DesiredControllerWorkspace workspace)
        {
            this.host.apply (workspace);
        }


        @Override
        public void applyLayout (final DesiredControllerLayout layout)
        {
            this.host.applyLayout (layout);
        }


        @Override
        public void invalidate ()
        {
            this.host.invalidate ();
        }
    }
}
