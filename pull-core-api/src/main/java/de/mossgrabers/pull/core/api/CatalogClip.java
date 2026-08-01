// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/**
 * Immutable clip information observed by the stable shell on the selected track.
 *
 * @param targetId Shell-issued target identity
 * @param name Current clip name
 */
public record CatalogClip (ClipTargetId targetId, String name)
{
    /**
     * Validate the catalog entry.
     */
    public CatalogClip
    {
        targetId = Objects.requireNonNull (targetId, "targetId");
        name = Objects.requireNonNull (name, "name");
    }
}
