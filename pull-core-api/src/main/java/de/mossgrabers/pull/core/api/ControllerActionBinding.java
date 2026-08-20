// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.event.InputKind;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


/**
 * View-owned mapping from one physical edge input to a semantic action.
 *
 * @param controlId Physical control
 * @param inputKind Edge input kind
 * @param intents Complete semantic variants admitted for this physical input
 */
public record ControllerActionBinding (ControlId controlId, InputKind inputKind, Set<ControllerActionIntent> intents)
{
    /** Validate and copy the binding. */
    public ControllerActionBinding
    {
        controlId = Objects.requireNonNull (controlId, "controlId");
        inputKind = Objects.requireNonNull (inputKind, "inputKind");
        intents = Set.copyOf (Objects.requireNonNull (intents, "intents"));
        if (!inputKind.isEdge ())
            throw new IllegalArgumentException ("controller actions require an edge input");
        if (intents.isEmpty ())
            throw new IllegalArgumentException ("controller action bindings require at least one semantic intent");

        final Set<ControllerActionId> actions = new HashSet<> (intents.size ());
        for (final ControllerActionIntent intent: intents)
        {
            final ControllerActionIntent checkedIntent = Objects.requireNonNull (intent, "controller action intent");
            if (!actions.add (checkedIntent.action ()))
                throw new IllegalArgumentException ("controller action bindings cannot declare one action twice");
        }
    }


    /** Create a single-intent binding. */
    public ControllerActionBinding (final ControlId controlId, final InputKind inputKind, final ControllerActionId action, final Set<ControllerStateScope> invalidates)
    {
        this (controlId, inputKind, Set.of (new ControllerActionIntent (action, invalidates)));
    }


    /** Resolve a single-intent physical binding to its semantic metadata. */
    public ControllerActionIntent intent ()
    {
        if (this.intents.size () != 1)
            throw new IllegalStateException ("controller action variant must be selected explicitly");
        return this.intents.iterator ().next ();
    }


    /** Resolve one declared semantic variant. */
    public ControllerActionIntent intent (final ControllerActionId action)
    {
        final ControllerActionId checkedAction = Objects.requireNonNull (action, "action");
        for (final ControllerActionIntent intent: this.intents)
        {
            if (intent.action () == checkedAction)
                return intent;
        }
        throw new IllegalArgumentException ("semantic action is not declared by this physical binding");
    }
}
