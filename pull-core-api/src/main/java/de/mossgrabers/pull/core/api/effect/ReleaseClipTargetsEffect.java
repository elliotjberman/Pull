// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

import de.mossgrabers.pull.core.api.ControlId;

import java.util.Objects;

/**
 * Release the clip target currently leased to a logical owner.
 *
 * @param owner Logical control whose lease is released
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
