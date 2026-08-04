// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.runtime.view;

import de.mossgrabers.pull.core.view.CompiledWorkspace;

import java.util.List;


/**
 * Existing Pull behavior expressed as one fixed-footprint workspace.
 */
public final class DefaultWorkspace
{
    private DefaultWorkspace ()
    {
        // Utility class
    }


    /**
     * Create a fresh default workspace for one core generation.
     *
     * @return Compiled workspace
     */
    public static CompiledWorkspace create ()
    {
        return CompiledWorkspace.compile ("Default", List.of (new DrumFillView (), new RecordControlView ()));
    }
}
