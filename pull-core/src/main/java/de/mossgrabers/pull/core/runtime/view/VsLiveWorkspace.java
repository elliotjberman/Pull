// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.view.CompiledWorkspace;

import java.util.List;


/**
 * First post-demo composite built entirely from fixed controller views.
 */
public final class VsLiveWorkspace
{
    /** Workspace name presented across the core/shell boundary. */
    public static final String NAME = "VS Live";
    /** Fixed upper-half Session bank. */
    public static final SessionBankShape SESSION_BANK = new SessionBankShape (8, 4);


    private VsLiveWorkspace ()
    {
        // Utility class
    }


    /**
     * Compile a fresh VS Live workspace for one core generation.
     *
     * @param selection Shared workspace selection
     * @return Compiled workspace
     */
    public static CompiledWorkspace create (final WorkspaceSelection selection)
    {
        return CompiledWorkspace.compile (
            NAME,
            SESSION_BANK,
            List.of (
                new WorkspaceSelectionView (selection),
                new ProjectMacroControlsView (),
                new GlobalParameterControlsView (),
                new TrackSelectionStripView (),
                new SessionNavigationView (),
                new SessionClipGridView (true),
                new DrumControllerView (true),
                new RecordControlView ()));
    }
}
