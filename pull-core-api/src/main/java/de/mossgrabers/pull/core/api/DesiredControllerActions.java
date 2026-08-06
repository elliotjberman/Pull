// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.event.InputKind;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/**
 * Complete replayable semantic-action bindings compiled from the active workspace.
 *
 * @param bindings Physical-to-semantic bindings
 */
public record DesiredControllerActions (Set<ControllerActionBinding> bindings)
{
    /** Installed semantic action capacity. */
    public static final int CAPACITY = 64;

    private static final DesiredControllerActions EMPTY = new DesiredControllerActions (Set.of ());


    /** Validate and copy the complete binding set. */
    public DesiredControllerActions
    {
        bindings = Set.copyOf (Objects.requireNonNull (bindings, "bindings"));
        if (bindings.size () > CAPACITY)
            throw new IllegalArgumentException ("controller actions exceed the installed capacity");

        final Map<InputAddress, ControllerActionBinding> byInput = new LinkedHashMap<> (bindings.size ());
        for (final ControllerActionBinding binding: bindings)
        {
            final ControllerActionBinding checkedBinding = Objects.requireNonNull (binding, "controller action binding");
            if (byInput.putIfAbsent (new InputAddress (checkedBinding.controlId (), checkedBinding.inputKind ()), checkedBinding) != null)
                throw new IllegalArgumentException ("multiple semantic actions are bound to one physical input");
        }
    }


    /** Resolve the semantic action for one physical input, or {@code null}. */
    public ControllerActionBinding bindingOrNull (final ControlId controlId, final InputKind inputKind)
    {
        for (final ControllerActionBinding binding: this.bindings)
        {
            if (binding.controlId ().equals (controlId) && binding.inputKind () == inputKind)
                return binding;
        }
        return null;
    }


    /** Get an empty binding set. */
    public static DesiredControllerActions empty ()
    {
        return EMPTY;
    }


    private record InputAddress (ControlId controlId, InputKind inputKind)
    {
    }
}
