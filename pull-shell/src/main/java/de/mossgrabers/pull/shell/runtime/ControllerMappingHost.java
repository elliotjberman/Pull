// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerMappingFeedbackSnapshot;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.CoreControllerMappings;
import de.mossgrabers.pull.core.api.PushControlIds;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/** Permanent semantic Bitwig mapping endpoints and their authoritative Boolean feedback. */
final class ControllerMappingHost
{
    private static final int PAD_COUNT = 64;
    private static final Set<ControlId> PHYSICAL_PAD_CONTROLS = physicalPadControls ();

    private final Map<ControlId, IHwButton> physicalButtons;
    private final Map<ControllerMappingId, IHwButton> mappingButtons;
    private final FeedbackState feedback;


    ControllerMappingHost (final PushControlSurface surface)
    {
        this (createTopology (Objects.requireNonNull (surface, "surface")));
    }


    /** Test seam for installing the bounded endpoint inventory. */
    ControllerMappingHost (final IHwSurfaceFactory factory, final int surfaceID, final Map<ControlId, IHwButton> physicalButtons)
    {
        this (createTopology (factory, surfaceID, physicalButtons));
    }


    private ControllerMappingHost (final Topology topology)
    {
        this.physicalButtons = topology.physicalButtons ();
        this.mappingButtons = topology.mappingButtons ();
        this.feedback = topology.feedback ();
    }


    Map<ControlId, IHwButton> physicalButtons ()
    {
        return this.physicalButtons;
    }


    Map<ControllerMappingId, IHwButton> mappingButtons ()
    {
        return this.mappingButtons;
    }


    ControllerMappingFeedbackSnapshot snapshot ()
    {
        return this.feedback.snapshot;
    }


    private static Topology createTopology (final PushControlSurface surface)
    {
        final Map<ControlId, IHwButton> physicalButtons = new LinkedHashMap<> ();
        for (int index = 0; index < PAD_COUNT; index++)
        {
            final IHwButton button = Objects.requireNonNull (surface.getButton (ButtonID.get (ButtonID.PAD1, index)), "physical pad");
            physicalButtons.put (PushControlIds.pad (index + 1), button);
        }
        return createTopology (surface.getSurfaceFactory (), surface.getSurfaceID (), physicalButtons);
    }


    private static Topology createTopology (final IHwSurfaceFactory factory, final int surfaceID, final Map<ControlId, IHwButton> physicalButtons)
    {
        final IHwSurfaceFactory checkedFactory = Objects.requireNonNull (factory, "factory");
        final Map<ControlId, IHwButton> checkedPhysicalButtons = Map.copyOf (Objects.requireNonNull (physicalButtons, "physicalButtons"));
        if (!checkedPhysicalButtons.keySet ().equals (PHYSICAL_PAD_CONTROLS))
            throw new IllegalArgumentException ("controller mapping host requires the complete 64-pad physical grid");

        final FeedbackState feedback = new FeedbackState ();
        final Map<ControllerMappingId, IHwButton> mappingButtons = new LinkedHashMap<> ();
        for (int slot = 0; slot < CoreControllerMappings.DRUM_CONTROL_PADS.size (); slot++)
        {
            final int number = slot + 1;
            final ControllerMappingId mappingId = CoreControllerMappings.DRUM_CONTROL_PADS.get (slot);
            final IHwButton mappingButton = Objects.requireNonNull (checkedFactory.createButton (
                surfaceID,
                "CONTROLLER_MAPPING_DRUM_CONTROL_" + number,
                "Drum Controller Control " + number), "semantic mapping button");
            checkedFactory.installMappedBooleanFeedback (
                surfaceID,
                "CONTROLLER_MAPPING_DRUM_CONTROL_STATE_" + number,
                mappingButton,
                on -> feedback.accept (mappingId, on));
            mappingButtons.put (mappingId, mappingButton);
        }

        // Physical pads remain the sole ordinary-command dispatch objects, but none expose native
        // MIDI matchers or Bitwig-learnable identities. The permanent raw ingress drives them.
        checkedPhysicalButtons.values ().forEach (IHwButton::unbind);
        return new Topology (checkedPhysicalButtons, Map.copyOf (mappingButtons), feedback);
    }


    private static Set<ControlId> physicalPadControls ()
    {
        final Set<ControlId> controls = new LinkedHashSet<> (PAD_COUNT);
        for (int index = 1; index <= PAD_COUNT; index++)
            controls.add (PushControlIds.pad (index));
        return Set.copyOf (controls);
    }


    private static final class FeedbackState
    {
        private final Map<ControllerMappingId, Boolean> states = initialStates ();
        private volatile ControllerMappingFeedbackSnapshot snapshot = new ControllerMappingFeedbackSnapshot (true, this.states);


        private synchronized void accept (final ControllerMappingId mappingId, final Boolean on)
        {
            final Boolean next = Boolean.valueOf (Boolean.TRUE.equals (on));
            if (next.equals (this.states.get (mappingId)))
                return;
            if (!this.states.containsKey (mappingId))
                throw new IllegalArgumentException ("controller mapping feedback is not installed");
            this.states.put (mappingId, next);
            this.snapshot = new ControllerMappingFeedbackSnapshot (true, this.states);
        }


        private static Map<ControllerMappingId, Boolean> initialStates ()
        {
            final Map<ControllerMappingId, Boolean> states = new LinkedHashMap<> ();
            CoreControllerMappings.DRUM_CONTROL_PADS.forEach (mappingId -> states.put (mappingId, Boolean.FALSE));
            return states;
        }
    }


    private record Topology (Map<ControlId, IHwButton> physicalButtons, Map<ControllerMappingId, IHwButton> mappingButtons, FeedbackState feedback)
    {}
}
