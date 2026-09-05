// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/** Exact project and native panel-layout origin of an application UI request. */
public record ApplicationUiContext (long generation, String projectId, String panelLayout)
{
    public ApplicationUiContext
    {
        projectId = Objects.requireNonNull (projectId, "projectId");
        panelLayout = Objects.requireNonNull (panelLayout, "panelLayout");
        if (generation <= 0 || projectId.isBlank () || panelLayout.length () > 128)
            throw new IllegalArgumentException ("application UI context requires a live project and bounded panel layout");
    }
}
