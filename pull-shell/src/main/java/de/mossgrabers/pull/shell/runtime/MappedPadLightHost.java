// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.controller.hardware.BindType;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwLight;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.MappedPadLightsSnapshot;
import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;


/** Fixed host controls and feedback observers for the four Bitwig-mappable control pads. */
final class MappedPadLightHost
{
    private static final int FIRST_PHYSICAL_PAD_INDEX = 28;
    private static final MappedPadLightsSnapshot.Pad UNMAPPED = new MappedPadLightsSnapshot.Pad (false, new RgbColor (0, 0, 0));

    private final Map<ControlId, IHwButton> mappingButtons;
    private final Map<ControlId, IHwButton> dispatchButtons;
    private final List<MappedPadLightsSnapshot.Pad> states = new ArrayList<> (Collections.nCopies (MappedPadLightsSnapshot.CAPACITY, UNMAPPED));

    private volatile MappedPadLightsSnapshot snapshot = new MappedPadLightsSnapshot (true, this.states);


    MappedPadLightHost (final PushControlSurface surface)
    {
        this (createTopology (Objects.requireNonNull (surface, "surface")));
    }


    /** Test seam for creating the fixed alternate-dispatch topology. */
    MappedPadLightHost (final IHwSurfaceFactory factory, final int surfaceID, final IMidiInput input, final IPadGrid padGrid, final Map<ControlId, IHwButton> mappingButtons, final List<IHwLight> lights)
    {
        this (createTopology (factory, surfaceID, input, padGrid, mappingButtons, lights));
    }


    private MappedPadLightHost (final Topology topology)
    {
        this (topology.mappingButtons (), topology.dispatchButtons (), topology.lights ());
    }


    /** Test seam for the fixed host-control topology. */
    MappedPadLightHost (final Map<ControlId, IHwButton> mappingButtons, final Map<ControlId, IHwButton> dispatchButtons, final List<IHwLight> lights)
    {
        this.mappingButtons = Map.copyOf (Objects.requireNonNull (mappingButtons, "mappingButtons"));
        this.dispatchButtons = Map.copyOf (Objects.requireNonNull (dispatchButtons, "dispatchButtons"));
        final List<IHwLight> checkedLights = List.copyOf (Objects.requireNonNull (lights, "lights"));
        final Set<ControlId> installed = Set.copyOf (CoreControls.DRUM_CONTROL_PADS);
        if (!this.mappingButtons.keySet ().equals (installed) || !this.dispatchButtons.keySet ().equals (installed) || checkedLights.size () != MappedPadLightsSnapshot.CAPACITY)
            throw new IllegalArgumentException ("mapped pad-light host requires the four installed control pads");
        for (int slot = 0; slot < checkedLights.size (); slot++)
        {
            final int capturedSlot = slot;
            checkedLights.get (slot).installMappedColorObserver (feedback -> this.accept (capturedSlot, feedback));
        }
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
        return this.snapshot;
    }


    private synchronized void accept (final int slot, final Optional<ColorEx> feedback)
    {
        final Optional<ColorEx> checked = Objects.requireNonNull (feedback, "feedback");
        final MappedPadLightsSnapshot.Pad next = checked
            .map (color -> new MappedPadLightsSnapshot.Pad (true, rgb (color)))
            .orElse (UNMAPPED);
        if (next.equals (this.states.get (slot)))
            return;
        this.states.set (slot, next);
        this.snapshot = new MappedPadLightsSnapshot (true, this.states);
    }


    private static Topology createTopology (final PushControlSurface surface)
    {
        final Map<ControlId, IHwButton> mappingButtons = new LinkedHashMap<> ();
        final List<IHwLight> lights = new ArrayList<> (MappedPadLightsSnapshot.CAPACITY);
        for (int slot = 0; slot < MappedPadLightsSnapshot.CAPACITY; slot++)
        {
            final IHwButton button = Objects.requireNonNull (surface.getButton (ButtonID.get (ButtonID.PAD1, FIRST_PHYSICAL_PAD_INDEX + slot)), "control pad");
            mappingButtons.put (CoreControls.DRUM_CONTROL_PADS.get (slot), button);
            lights.add (Objects.requireNonNull (button.getLight (), "control-pad light"));
        }
        return createTopology (surface.getSurfaceFactory (), surface.getSurfaceID (), surface.getMidiInput (), surface.getPadGrid (), mappingButtons, lights);
    }


    private static Topology createTopology (final IHwSurfaceFactory factory, final int surfaceID, final IMidiInput input, final IPadGrid padGrid, final Map<ControlId, IHwButton> mappingButtons, final List<IHwLight> lights)
    {
        final IHwSurfaceFactory checkedFactory = Objects.requireNonNull (factory, "factory");
        final IMidiInput checkedInput = Objects.requireNonNull (input, "input");
        final IPadGrid checkedGrid = Objects.requireNonNull (padGrid, "padGrid");
        final Map<ControlId, IHwButton> checkedMappingButtons = Map.copyOf (Objects.requireNonNull (mappingButtons, "mappingButtons"));
        final Map<ControlId, IHwButton> dispatchButtons = new LinkedHashMap<> ();
        for (int slot = 0; slot < MappedPadLightsSnapshot.CAPACITY; slot++)
        {
            final ControlId control = CoreControls.DRUM_CONTROL_PADS.get (slot);
            final int number = slot + 1;
            final IHwButton mappingButton = Objects.requireNonNull (checkedMappingButtons.get (control), "mapping button");
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
        return new Topology (checkedMappingButtons, dispatchButtons, lights);
    }


    private static double normalizedVelocity (final int velocity)
    {
        if (velocity <= 0)
            return 0;
        if (velocity >= 127)
            return 1;
        return Math.nextUp (velocity / 127.0);
    }


    private static RgbColor rgb (final ColorEx color)
    {
        return new RgbColor (channel (color.getRed ()), channel (color.getGreen ()), channel (color.getBlue ()));
    }


    private static int channel (final double value)
    {
        return (int) Math.round (Math.max (0, Math.min (1, value)) * 255);
    }


    private record Topology (Map<ControlId, IHwButton> mappingButtons, Map<ControlId, IHwButton> dispatchButtons, List<IHwLight> lights)
    {}
}
