// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

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

    private Id active;


    /**
     * Constructor.
     *
     * @param active Initial workspace
     */
    public WorkspaceSelection (final Id active)
    {
        this.active = Objects.requireNonNull (active, "active");
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
     * Select a workspace.
     *
     * @param workspace Workspace ID
     */
    public void select (final Id workspace)
    {
        this.active = Objects.requireNonNull (workspace, "workspace");
    }
}
