// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/** Complete replayable projection of physical inputs onto installed semantic mapping endpoints. */
public record DesiredControllerMappings (Set<ControllerMappingBinding> bindings)
{
    /** Maximum mapping projections accepted across the parent-loaded API. */
    public static final int CAPACITY = 64;

    private static final DesiredControllerMappings EMPTY = new DesiredControllerMappings (Set.of ());


    /** Validate uniqueness and copy the complete projection set. */
    public DesiredControllerMappings
    {
        bindings = Set.copyOf (Objects.requireNonNull (bindings, "bindings"));
        if (bindings.size () > CAPACITY)
            throw new IllegalArgumentException ("controller mappings exceed the installed API capacity");

        final Map<ControlId, ControllerMappingId> byPhysicalControl = new LinkedHashMap<> (bindings.size ());
        final Set<ControllerMappingId> mappingIds = new LinkedHashSet<> (bindings.size ());
        for (final ControllerMappingBinding binding: bindings)
        {
            final ControllerMappingBinding checkedBinding = Objects.requireNonNull (binding, "controller mapping binding");
            if (byPhysicalControl.putIfAbsent (checkedBinding.physicalControl (), checkedBinding.mappingId ()) != null)
                throw new IllegalArgumentException ("multiple controller mappings use one physical control");
            if (!mappingIds.add (checkedBinding.mappingId ()))
                throw new IllegalArgumentException ("one controller mapping endpoint cannot drive multiple physical controls");
        }
    }


    /** Resolve the semantic endpoint projected onto one physical control, or {@code null}. */
    public ControllerMappingId mappingIdOrNull (final ControlId physicalControl)
    {
        for (final ControllerMappingBinding binding: this.bindings)
        {
            if (binding.physicalControl ().equals (physicalControl))
                return binding.mappingId ();
        }
        return null;
    }


    /** Get an empty projection set. */
    public static DesiredControllerMappings empty ()
    {
        return EMPTY;
    }
}
