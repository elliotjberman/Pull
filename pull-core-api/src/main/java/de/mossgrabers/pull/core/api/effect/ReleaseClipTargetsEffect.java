// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ControlId;

import java.util.Objects;

/**
 * Release the active momentary clip or cancel the pending intent owned by this control. An absent
 * owner is an idempotent no-op.
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
