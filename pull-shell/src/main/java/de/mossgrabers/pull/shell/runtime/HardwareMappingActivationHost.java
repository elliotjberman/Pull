// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerMappingBinding;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;


/** Projects replayable semantic mapping leases onto permanent Bitwig hardware actions. */
final class HardwareMappingActivationHost
{
    private final Map<ControlId, IHwButton> physicalButtons;
    private final Map<ControllerMappingId, IHwButton> mappingButtons;
    private final Predicate<ControlId> lifecycleIdle;
    private final MatcherBinder matcherBinder;
    private final Map<ControlId, ControllerMappingId> active = new LinkedHashMap<> ();
    private final Map<ControlId, ControllerMappingId> releasingMappings = new LinkedHashMap<> ();
    private final Set<ControlId> releasingDispatch = new LinkedHashSet<> ();


    HardwareMappingActivationHost (final Map<ControlId, IHwButton> physicalButtons, final Map<ControllerMappingId, IHwButton> mappingButtons, final Predicate<ControlId> lifecycleIdle, final MatcherBinder matcherBinder)
    {
        this.physicalButtons = Map.copyOf (Objects.requireNonNull (physicalButtons, "physicalButtons"));
        this.mappingButtons = Map.copyOf (Objects.requireNonNull (mappingButtons, "mappingButtons"));
        this.lifecycleIdle = Objects.requireNonNull (lifecycleIdle, "lifecycleIdle");
        this.matcherBinder = Objects.requireNonNull (matcherBinder, "matcherBinder");
        if (this.physicalButtons.isEmpty () || this.mappingButtons.isEmpty ())
            throw new IllegalArgumentException ("controller mapping topology must not be empty");
    }


    /** Replace the complete desired physical-to-semantic projection. */
    void request (final DesiredControllerMappings controllerMappings)
    {
        final DesiredControllerMappings requested = Objects.requireNonNull (controllerMappings, "controllerMappings");
        for (final ControllerMappingBinding binding: requested.bindings ())
        {
            if (!this.physicalButtons.containsKey (binding.physicalControl ()))
                throw new IllegalArgumentException ("Requested physical controller mapping input is not installed");
            if (!this.mappingButtons.containsKey (binding.mappingId ()))
                throw new IllegalArgumentException ("Requested semantic controller mapping endpoint is not installed");
        }

        this.retireChangedMappings (requested);
        this.finishRetirements ();
        this.activateRequestedMappings (requested);
    }


    /** Get semantic mapping matchers currently admitting new physical presses. */
    DesiredControllerMappings activeMappings ()
    {
        final Set<ControllerMappingBinding> bindings = new LinkedHashSet<> (this.active.size ());
        this.active.forEach ( (physicalControl, mappingId) -> bindings.add (new ControllerMappingBinding (physicalControl, mappingId)));
        return new DesiredControllerMappings (bindings);
    }


    /** Whether raw MIDI must drive the original ordinary-dispatch object for this physical input. */
    RawDisposition dispatchRaw (final ControlId control, final ButtonEvent event, final double velocity)
    {
        final ControlId checkedControl = Objects.requireNonNull (control, "control");
        if (!this.physicalButtons.containsKey (checkedControl))
            throw new IllegalArgumentException ("Physical controller mapping input is not installed");

        final boolean activeMapping = this.active.containsKey (checkedControl);
        final boolean releasingMapping = this.releasingMappings.containsKey (checkedControl);
        if (activeMapping)
            return event == ButtonEvent.DOWN || !this.lifecycleIdle.test (checkedControl) ? RawDisposition.MAPPED : RawDisposition.SUPPRESSED;
        if (releasingMapping)
            return event == ButtonEvent.UP && !this.lifecycleIdle.test (checkedControl) ? RawDisposition.MAPPED : RawDisposition.SUPPRESSED;

        final boolean dispatch = this.releasingDispatch.contains (checkedControl) ? event == ButtonEvent.UP && !this.lifecycleIdle.test (checkedControl) : event == ButtonEvent.DOWN || !this.lifecycleIdle.test (checkedControl);
        if (dispatch)
        {
            this.physicalButtons.get (checkedControl).trigger (event, velocity);
            return RawDisposition.DISPATCHED;
        }
        return RawDisposition.SUPPRESSED;
    }


    private void retireChangedMappings (final DesiredControllerMappings requested)
    {
        for (final Map.Entry<ControlId, ControllerMappingId> entry: Set.copyOf (this.active.entrySet ()))
        {
            final ControllerMappingId desired = requested.mappingIdOrNull (entry.getKey ());
            if (entry.getValue ().equals (desired))
                continue;

            this.mappingButtons.get (entry.getValue ()).unbindPress ();
            this.active.remove (entry.getKey ());
            if (!this.lifecycleIdle.test (entry.getKey ()))
                this.releasingMappings.put (entry.getKey (), entry.getValue ());
        }
    }


    private void finishRetirements ()
    {
        this.releasingMappings.keySet ().removeIf (this.lifecycleIdle::test);
        this.releasingDispatch.removeIf (this.lifecycleIdle::test);
    }


    private void activateRequestedMappings (final DesiredControllerMappings requested)
    {
        for (final ControllerMappingBinding binding: requested.bindings ())
        {
            final ControlId physicalControl = binding.physicalControl ();
            final ControllerMappingId mappingId = binding.mappingId ();
            if (mappingId.equals (this.active.get (physicalControl)))
                continue;

            this.releasingDispatch.add (physicalControl);
            if (!this.lifecycleIdle.test (physicalControl) || this.releasingMappings.containsKey (physicalControl) || this.mappingInUse (mappingId))
                continue;

            final IHwButton mappingButton = this.mappingButtons.get (mappingId);
            this.matcherBinder.bind (mappingButton, physicalControl);
            mappingButton.unbindRelease ();
            this.active.put (physicalControl, mappingId);
            this.releasingDispatch.remove (physicalControl);
        }
    }


    private boolean mappingInUse (final ControllerMappingId mappingId)
    {
        return this.active.containsValue (mappingId) || this.releasingMappings.containsValue (mappingId);
    }


    @FunctionalInterface
    interface MatcherBinder
    {
        void bind (IHwButton mappingButton, ControlId physicalControl);
    }


    enum RawDisposition
    {
        MAPPED,
        DISPATCHED,
        SUPPRESSED
    }
}
