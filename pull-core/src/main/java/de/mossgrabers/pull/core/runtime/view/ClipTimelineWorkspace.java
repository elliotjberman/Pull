// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.api.SessionBankShape;
import de.mossgrabers.pull.core.view.CompiledWorkspace;
import de.mossgrabers.pull.core.view.ControllerView;

import java.util.List;


/** Compiles Clip Timeline over either the normal Track page or the temporary Master page. */
public final class ClipTimelineWorkspace
{
    private ClipTimelineWorkspace ()
    {
        // Utility class.
    }


    /** Create the normal Clip Timeline and Track/Mix page. */
    public static CompiledWorkspace create (final ControllerLevelViews controllerViews, final ControllerView timelineView)
    {
        return CompiledWorkspace.compile ("Clip Timeline", SessionBankShape.empty (), controllerViews.composeWithoutNoteController (List.of (new TrackMixerPageView (), timelineView)));
    }


    /** Create the Master page while preserving Clip Timeline grid ownership. */
    public static CompiledWorkspace master (final ControllerLevelViews controllerViews, final ControllerView timelineView)
    {
        return MasterWorkspace.create (controllerViews, SessionBankShape.empty (), List.of (timelineView), false);
    }
}
