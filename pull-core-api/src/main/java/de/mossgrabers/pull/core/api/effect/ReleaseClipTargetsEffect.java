// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ControlId;

import java.util.Objects;

/**
 * Release the momentary clip session when the supplied owner is active. A retired ancestor or
 * absent owner is an idempotent no-op.
 *
 * @param owner Logical control requesting session release
 */
public record ReleaseClipTargetsEffect (ControlId owner) implements CoreEffect
{
    /**
     * Validate the effect.
     */
    public ReleaseClipTargetsEffect
    {
        owner = Objects.requireNonNull (owner, "owner");
    }
}
