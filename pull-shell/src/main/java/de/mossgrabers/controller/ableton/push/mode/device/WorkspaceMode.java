// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.controller.ableton.push.mode.device;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.mode.CorePageMode;
import de.mossgrabers.controller.ableton.push.workspace.WorkspaceFacetAdapter;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.pull.shell.runtime.ReloadableControllerRuntime;

/** Registered name for the inert Project Macro page adapter. */
public final class WorkspaceMode extends CorePageMode implements WorkspaceFacetAdapter
{
    public WorkspaceMode (final PushControlSurface surface, final IModel model, final ReloadableControllerRuntime runtime)
    {
        super ("Workspace", surface, model, runtime);
    }

    @Override
    public void reconcileWorkspaceFacets ()
    {
        // All page semantics use the common inert adapter footprint.
    }
}
