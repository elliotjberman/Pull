// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.ControllerLayoutSnapshot;
import de.mossgrabers.pull.core.api.ControllerNoteView;

import java.util.Objects;


/**
 * Shared core-owned selection of one compiled controller workspace.
 */
public final class WorkspaceSelection
{
    /** Available compiled workspaces. */
    public enum Id
    {
        /** Established controller behavior without a stable layout override. */
        DEFAULT,
        /** Project macros, upper Session grid, and lower Drum Controller. */
        VS_LIVE
    }

    /** Explicit stable destination currently awaiting controller-layout acknowledgement. */
    public enum Destination
    {
        /** No destination handoff is pending. */
        NONE,
        /** Track/Mix page with the full Session view. */
        SESSION,
        /** Track/Mix page around the stable preferred Note view. */
        NOTE
    }

    private Id          active;
    private Destination pendingDestination;
    private long        requestSequence;


    /**
     * Constructor.
     *
     * @param active Initial workspace
     */
    public WorkspaceSelection (final Id active)
    {
        this (active, Destination.NONE);
    }


    /**
     * Constructor with a replayed destination handoff.
     *
     * @param active Initial workspace
     * @param pendingDestination Destination still awaiting layout read-back
     */
    public WorkspaceSelection (final Id active, final Destination pendingDestination)
    {
        this.active = Objects.requireNonNull (active, "active");
        this.pendingDestination = Objects.requireNonNull (pendingDestination, "pendingDestination");
    }


    /**
     * Get the active workspace.
     *
     * @return Workspace ID
     */
    public Id active ()
    {
        return this.active;
    }


    /**
     * Get the sequence of explicit workspace-selection requests.
     *
     * @return Request sequence
     */
    public long requestSequence ()
    {
        return this.requestSequence;
    }


    /** Get the stable destination still awaiting authoritative layout read-back. */
    public Destination pendingDestination ()
    {
        return this.pendingDestination;
    }


    /**
     * Select a workspace.
     *
     * @param workspace Workspace ID
     */
    public void select (final Id workspace)
    {
        this.select (workspace, Destination.NONE);
    }


    /**
     * Select a workspace and an optional stable destination handoff.
     *
     * @param workspace Workspace ID
     * @param destination Stable destination to realize and acknowledge
     */
    public void select (final Id workspace, final Destination destination)
    {
        this.active = Objects.requireNonNull (workspace, "workspace");
        this.pendingDestination = Objects.requireNonNull (destination, "destination");
        this.requestSequence++;
    }


    /**
     * Retire a destination request only after the stable controller reports the requested layout.
     *
     * @param layout Authoritative stable layout read-back
     */
    public void observe (final ControllerLayoutSnapshot layout)
    {
        final ControllerLayoutSnapshot observed = Objects.requireNonNull (layout, "layout");
        final boolean trackPage = "TRACK".equals (observed.modeId ());
        if (this.pendingDestination == Destination.SESSION && trackPage && "SESSION".equals (observed.viewId ()))
            this.pendingDestination = Destination.NONE;
        else if (this.pendingDestination == Destination.NOTE && trackPage && ControllerNoteView.fromStableId (observed.viewId ()).isPresent ())
            this.pendingDestination = Destination.NONE;
    }
}
