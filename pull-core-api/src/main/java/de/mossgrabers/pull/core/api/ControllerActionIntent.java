// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;
import java.util.Set;


/**
 * One semantic controller action resolved before its stable or core behavior executes.
 *
 * @param action Semantic action
 * @param invalidates Authoritative state scopes which may change after execution
 */
public record ControllerActionIntent (ControllerActionId action, Set<ControllerStateScope> invalidates)
{
    /** Validate and copy the resolved intent. */
    public ControllerActionIntent
    {
        action = Objects.requireNonNull (action, "action");
        invalidates = Set.copyOf (Objects.requireNonNull (invalidates, "invalidates"));
        if (invalidates.isEmpty ())
            throw new IllegalArgumentException ("controller action invalidation scopes must not be empty");
    }
}
