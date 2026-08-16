// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.controller.hardware.BindType;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.utils.ButtonEvent;
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
    private final Map<ControlId, IHwButton> dispatchButtons;
    private final FeedbackState feedback;


    MappedPadLightHost (final PushControlSurface surface)
    {
        this (createTopology (Objects.requireNonNull (surface, "surface")));
    }


    /** Test seam for creating the fixed alternate-dispatch topology. */
    MappedPadLightHost (final IHwSurfaceFactory factory, final int surfaceID, final IMidiInput input, final IPadGrid padGrid, final Map<ControlId, IHwButton> mappingButtons)
    {
        this (createTopology (factory, surfaceID, input, padGrid, mappingButtons));
    }


    private MappedPadLightHost (final Topology topology)
    {
        this (topology.mappingButtons (), topology.dispatchButtons (), topology.feedback ());
    }


    /** Test seam for the fixed host-control topology. */
    MappedPadLightHost (final Map<ControlId, IHwButton> mappingButtons, final Map<ControlId, IHwButton> dispatchButtons)
    {
        this (mappingButtons, dispatchButtons, new FeedbackState ());
    }


    private MappedPadLightHost (final Map<ControlId, IHwButton> mappingButtons, final Map<ControlId, IHwButton> dispatchButtons, final FeedbackState feedback)
    {
        this.mappingButtons = Map.copyOf (Objects.requireNonNull (mappingButtons, "mappingButtons"));
        this.dispatchButtons = Map.copyOf (Objects.requireNonNull (dispatchButtons, "dispatchButtons"));
        this.feedback = Objects.requireNonNull (feedback, "feedback");
        final Set<ControlId> installed = Set.copyOf (CoreControls.DRUM_CONTROL_PADS);
        if (!this.mappingButtons.keySet ().equals (installed) || !this.dispatchButtons.keySet ().equals (installed))
            throw new IllegalArgumentException ("mapped pad-light host requires the four installed control pads");
    }


    Map<ControlId, IHwButton> mappingButtons ()
    {
        return this.mappingButtons;
    }


    Map<ControlId, IHwButton> dispatchButtons ()
    {
        return this.dispatchButtons;
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
        return createTopology (surface.getSurfaceFactory (), surface.getSurfaceID (), surface.getMidiInput (), surface.getPadGrid (), mappingButtons);
    }


    private static Topology createTopology (final IHwSurfaceFactory factory, final int surfaceID, final IMidiInput input, final IPadGrid padGrid, final Map<ControlId, IHwButton> mappingButtons)
    {
        final IHwSurfaceFactory checkedFactory = Objects.requireNonNull (factory, "factory");
        final IMidiInput checkedInput = Objects.requireNonNull (input, "input");
        final IPadGrid checkedGrid = Objects.requireNonNull (padGrid, "padGrid");
        final Map<ControlId, IHwButton> checkedMappingButtons = Map.copyOf (Objects.requireNonNull (mappingButtons, "mappingButtons"));
        final Map<ControlId, IHwButton> dispatchButtons = new LinkedHashMap<> ();
        final FeedbackState feedback = new FeedbackState ();
        for (int slot = 0; slot < MappedPadLightsSnapshot.CAPACITY; slot++)
        {
            final ControlId control = CoreControls.DRUM_CONTROL_PADS.get (slot);
            final int number = slot + 1;
            final IHwButton mappingButton = Objects.requireNonNull (checkedMappingButtons.get (control), "mapping button");
            final int capturedSlot = slot;
            checkedFactory.installMappedBooleanFeedback (surfaceID, "DRUM_CONTROL_PAD_MAPPING_STATE_" + number, mappingButton, on -> feedback.accept (capturedSlot, on));
            final IHwButton dispatchButton = checkedFactory.createButton (surfaceID, "DRUM_CONTROL_PAD_DISPATCH_" + number, "Drum Control Pad Dispatch " + number);
            final int note = checkedGrid.getStartNote () + FIRST_PHYSICAL_PAD_INDEX + slot;
            final int [] translated = checkedGrid.translateToController (note);
            dispatchButton.bind (checkedInput, BindType.NOTE, translated[0], translated[1]);
            dispatchButton.bind ( (event, velocity) -> {});
            dispatchButton.installEventArbitrator ( (event, velocity, ignored) -> {
                if (event != ButtonEvent.LONG)
                    mappingButton.trigger (event, normalizedVelocity (velocity));
            });
            dispatchButton.unbind ();
            dispatchButtons.put (control, dispatchButton);
        }
        return new Topology (checkedMappingButtons, dispatchButtons, feedback);
    }


    private static double normalizedVelocity (final int velocity)
    {
        if (velocity <= 0)
            return 0;
        if (velocity >= 127)
            return 1;
        return Math.nextUp (velocity / 127.0);
    }


    private static final class FeedbackState
    {
        private static final MappedPadLightsSnapshot.Pad OFF = new MappedPadLightsSnapshot.Pad (false);

        private final List<MappedPadLightsSnapshot.Pad> states = new ArrayList<> (Collections.nCopies (MappedPadLightsSnapshot.CAPACITY, OFF));
        private volatile MappedPadLightsSnapshot snapshot = new MappedPadLightsSnapshot (true, this.states);


        private MappedPadLightsSnapshot snapshot ()
        {
            return this.snapshot;
        }


        private synchronized void accept (final int slot, final Boolean on)
        {
            final MappedPadLightsSnapshot.Pad next = new MappedPadLightsSnapshot.Pad (Boolean.TRUE.equals (on));
            if (next.equals (this.states.get (slot)))
                return;
            this.states.set (slot, next);
            this.snapshot = new MappedPadLightsSnapshot (true, this.states);
        }
    }


    private record Topology (Map<ControlId, IHwButton> mappingButtons, Map<ControlId, IHwButton> dispatchButtons, FeedbackState feedback)
    {}
}
