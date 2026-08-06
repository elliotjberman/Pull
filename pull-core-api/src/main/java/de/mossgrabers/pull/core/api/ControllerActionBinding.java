// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.event.InputKind;

import java.util.Objects;
import java.util.Set;


/**
 * View-owned mapping from one physical edge input to a semantic action.
 *
 * @param controlId Physical control
 * @param inputKind Edge input kind
 * @param action Semantic action
 * @param invalidates State scopes which may change after the action executes
 */
public record ControllerActionBinding (ControlId controlId, InputKind inputKind, ControllerActionId action, Set<ControllerStateScope> invalidates)
{
    /** Validate and copy the binding. */
    public ControllerActionBinding
    {
        controlId = Objects.requireNonNull (controlId, "controlId");
        inputKind = Objects.requireNonNull (inputKind, "inputKind");
        action = Objects.requireNonNull (action, "action");
        invalidates = Set.copyOf (Objects.requireNonNull (invalidates, "invalidates"));
        if (!inputKind.isEdge ())
            throw new IllegalArgumentException ("controller actions require an edge input");
        if (invalidates.isEmpty ())
            throw new IllegalArgumentException ("controller action invalidation scopes must not be empty");
    }


    /** Resolve this physical binding to its semantic action metadata. */
    public ControllerActionIntent intent ()
    {
        return new ControllerActionIntent (this.action, this.invalidates);
    }
}
