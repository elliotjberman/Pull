// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.MappedPadLightsSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;


/** Fixed host controls and feedback observers for the four Bitwig-mappable control pads. */
final class MappedPadLightHost
{
    private static final int FIRST_PHYSICAL_PAD_INDEX = 28;

    private final Map<ControlId, IHwButton> mappingButtons;
    private final FeedbackState feedback;


    MappedPadLightHost (final PushControlSurface surface)
    {
        this (createTopology (Objects.requireNonNull (surface, "surface")));
    }


    /** Test seam for installing feedback on the fixed original-button topology. */
    MappedPadLightHost (final IHwSurfaceFactory factory, final int surfaceID, final Map<ControlId, IHwButton> mappingButtons)
    {
        this (createTopology (factory, surfaceID, mappingButtons));
    }


    private MappedPadLightHost (final Topology topology)
    {
        this (topology.mappingButtons (), topology.feedback ());
    }


    /** Test seam for the fixed host-control topology. */
    MappedPadLightHost (final Map<ControlId, IHwButton> mappingButtons)
    {
        this (mappingButtons, new FeedbackState ());
    }


    private MappedPadLightHost (final Map<ControlId, IHwButton> mappingButtons, final FeedbackState feedback)
    {
        this.mappingButtons = Map.copyOf (Objects.requireNonNull (mappingButtons, "mappingButtons"));
        this.feedback = Objects.requireNonNull (feedback, "feedback");
        if (!this.mappingButtons.keySet ().equals (Set.copyOf (CoreControls.DRUM_CONTROL_PADS)))
            throw new IllegalArgumentException ("mapped pad-light host requires the four installed control pads");
    }


    Map<ControlId, IHwButton> mappingButtons ()
    {
        return this.mappingButtons;
    }


    MappedPadLightsSnapshot snapshot ()
    {
        return this.feedback.snapshot ();
    }


    private static Topology createTopology (final PushControlSurface surface)
    {
        final Map<ControlId, IHwButton> mappingButtons = new LinkedHashMap<> ();
        for (int slot = 0; slot < MappedPadLightsSnapshot.CAPACITY; slot++)
        {
            final IHwButton button = Objects.requireNonNull (surface.getButton (ButtonID.get (ButtonID.PAD1, FIRST_PHYSICAL_PAD_INDEX + slot)), "control pad");
            mappingButtons.put (CoreControls.DRUM_CONTROL_PADS.get (slot), button);
        }
        return createTopology (surface.getSurfaceFactory (), surface.getSurfaceID (), mappingButtons);
    }


    private static Topology createTopology (final IHwSurfaceFactory factory, final int surfaceID, final Map<ControlId, IHwButton> mappingButtons)
    {
        final IHwSurfaceFactory checkedFactory = Objects.requireNonNull (factory, "factory");
        final Map<ControlId, IHwButton> checkedMappingButtons = Map.copyOf (Objects.requireNonNull (mappingButtons, "mappingButtons"));
        final FeedbackState feedback = new FeedbackState ();
        for (int slot = 0; slot < MappedPadLightsSnapshot.CAPACITY; slot++)
        {
            final ControlId control = CoreControls.DRUM_CONTROL_PADS.get (slot);
            final int number = slot + 1;
            final IHwButton mappingButton = Objects.requireNonNull (checkedMappingButtons.get (control), "mapping button");
            final int capturedSlot = slot;
            checkedFactory.installMappedBooleanFeedback (surfaceID, "DRUM_CONTROL_PAD_MAPPING_STATE_" + number, mappingButton, on -> feedback.accept (capturedSlot, on));
        }
        return new Topology (checkedMappingButtons, feedback);
    }


    private static final class FeedbackState
    {
        private final List<Boolean> states = new ArrayList<> (Collections.nCopies (MappedPadLightsSnapshot.CAPACITY, Boolean.FALSE));
        private volatile MappedPadLightsSnapshot snapshot = new MappedPadLightsSnapshot (true, this.states);


        private MappedPadLightsSnapshot snapshot ()
        {
            return this.snapshot;
        }


        private synchronized void accept (final int slot, final Boolean on)
        {
            final Boolean next = Boolean.valueOf (Boolean.TRUE.equals (on));
            if (next.equals (this.states.get (slot)))
                return;
            this.states.set (slot, next);
            this.snapshot = new MappedPadLightsSnapshot (true, this.states);
        }
    }


    private record Topology (Map<ControlId, IHwButton> mappingButtons, FeedbackState feedback)
    {}
}
