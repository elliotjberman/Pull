// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ControlId;

import java.util.Objects;

/**
 * Reveal an owner already retained in the shell-managed momentary clip session. The shell unwinds
 * only newer native Return frames; the retained target and its launch policy stay frozen.
 *
 * @param owner Retained logical owner to reveal
 */
public record ReactivateClipTargetEffect (ControlId owner) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public ReactivateClipTargetEffect
    {
        owner = Objects.requireNonNull (owner, "owner");
    }
}
