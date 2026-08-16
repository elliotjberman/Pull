// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.pull.core.api.ControlId;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;


/** Switches each physical control between its Bitwig-learnable action and stable dispatch lane. */
final class HardwareMappingActivationHost
{
    private final Map<ControlId, IHwButton> mappingButtons;
    private final Map<ControlId, IHwButton> dispatchButtons;
    private final Predicate<ControlId> lifecycleIdle;
    private final Map<ControlId, LaneState> states = new LinkedHashMap<> ();


    HardwareMappingActivationHost (final Map<ControlId, IHwButton> mappingButtons, final Map<ControlId, IHwButton> dispatchButtons, final Predicate<ControlId> lifecycleIdle)
    {
        this.mappingButtons = Map.copyOf (Objects.requireNonNull (mappingButtons, "mappingButtons"));
        this.dispatchButtons = Map.copyOf (Objects.requireNonNull (dispatchButtons, "dispatchButtons"));
        this.lifecycleIdle = Objects.requireNonNull (lifecycleIdle, "lifecycleIdle");
        if (!this.mappingButtons.keySet ().equals (this.dispatchButtons.keySet ()))
            throw new IllegalArgumentException ("Mapping and dispatch controls must have identical inventories");
        this.mappingButtons.keySet ().forEach (control -> this.states.put (control, LaneState.MAPPING));
    }


    /** Replace the complete desired mapping lane set. */
    void request (final Set<ControlId> controls)
    {
        final Set<ControlId> requested = Set.copyOf (Objects.requireNonNull (controls, "controls"));
        if (!this.mappingButtons.keySet ().containsAll (requested))
            throw new IllegalArgumentException ("Requested hardware mapping is not installed");

        for (final ControlId control: this.mappingButtons.keySet ())
        {
            final boolean mappingDesired = requested.contains (control);
            switch (this.states.get (control))
            {
                case MAPPING:
                    if (!mappingDesired)
                        this.leave (control, this.mappingButtons.get (control), this.dispatchButtons.get (control), LaneState.RELEASING_MAPPING, LaneState.DISPATCH);
                    break;
                case DISPATCH:
                    if (mappingDesired)
                        this.leave (control, this.dispatchButtons.get (control), this.mappingButtons.get (control), LaneState.RELEASING_DISPATCH, LaneState.MAPPING);
                    break;
                case RELEASING_MAPPING:
                    this.finishRelease (control, this.mappingButtons.get (control), mappingDesired);
                    break;
                case RELEASING_DISPATCH:
                    this.finishRelease (control, this.dispatchButtons.get (control), mappingDesired);
                    break;
            }
        }
    }


    /** Get original Bitwig-learnable actions currently admitting new note-on presses. */
    Set<ControlId> activeMappings ()
    {
        return this.states.entrySet ().stream ()
            .filter (entry -> entry.getValue () == LaneState.MAPPING)
            .map (Map.Entry::getKey)
            .collect (java.util.stream.Collectors.toUnmodifiableSet ());
    }


    private void leave (final ControlId control, final IHwButton current, final IHwButton next, final LaneState releasing, final LaneState active)
    {
        if (this.lifecycleIdle.test (control))
        {
            current.unbind ();
            next.rebind ();
            this.states.put (control, active);
            return;
        }

        current.unbindPress ();
        this.states.put (control, releasing);
    }


    private void finishRelease (final ControlId control, final IHwButton releasing, final boolean mappingDesired)
    {
        if (!this.lifecycleIdle.test (control))
            return;

        releasing.unbind ();
        final IHwButton next = mappingDesired ? this.mappingButtons.get (control) : this.dispatchButtons.get (control);
        next.rebind ();
        this.states.put (control, mappingDesired ? LaneState.MAPPING : LaneState.DISPATCH);
    }


    private enum LaneState
    {
        MAPPING,
        DISPATCH,
        RELEASING_MAPPING,
        RELEASING_DISPATCH
    }
}
