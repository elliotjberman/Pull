// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.runtime;

import de.mossgrabers.pull.core.api.ControllerPageRef;
import de.mossgrabers.pull.core.api.DesiredControllerPageState;
import de.mossgrabers.pull.core.api.StateEnvelope;
import de.mossgrabers.pull.core.runtime.view.LegacyPageAliases;
import de.mossgrabers.pull.core.runtime.view.WorkspaceSelection;
import java.util.Optional;

/** A saved ordinary workspace for scenarios that exercise its selected page and grid. */
public final class WorkspaceCheckpoints
{
    private WorkspaceCheckpoints () { }

    public static StateEnvelope ordinary (final String mode)
    {
        final var page = new DesiredControllerPageState (0, LegacyPageAliases.resolve (mode), ControllerPageRef.none (), Optional.empty (), 0);
        return new ControllerCheckpoint (WorkspaceSelection.Id.DEFAULT, WorkspaceSelection.Destination.NONE, WorkspaceSelection.Destination.NONE,
            "", false, false, 0, Optional.of (page)).encode ();
    }
}
