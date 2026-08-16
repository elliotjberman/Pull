// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.CoreControllerMappings;
import de.mossgrabers.pull.core.api.CoreControls;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Bounded semantic controller-mapping topology tests. */
class ControllerMappingHostTest
{
    @Test
    void createsSemanticMappingButtonsAndLeavesPhysicalButtonsRawDispatchOnly ()
    {
        final FactoryHarness factory = new FactoryHarness ();
        final Map<ControlId, ButtonHarness> physicalHarnesses = physicalHarnesses ();
        final Map<ControlId, IHwButton> physicalButtons = new LinkedHashMap<> ();
        physicalHarnesses.forEach ( (control, harness) -> physicalButtons.put (control, harness.button ()));

        final ControllerMappingHost host = new ControllerMappingHost (factory.factory (), 0, physicalButtons);

        assertEquals (List.of (
            "CONTROLLER_MAPPING_DRUM_CONTROL_1",
            "CONTROLLER_MAPPING_DRUM_CONTROL_2",
            "CONTROLLER_MAPPING_DRUM_CONTROL_3",
            "CONTROLLER_MAPPING_DRUM_CONTROL_4"), factory.buttonHardwareIDs);
        assertEquals (List.of (
            "Drum Controller Control 1",
            "Drum Controller Control 2",
            "Drum Controller Control 3",
            "Drum Controller Control 4"), factory.buttonLabels);
        assertEquals (List.of (
            "CONTROLLER_MAPPING_DRUM_CONTROL_STATE_1",
            "CONTROLLER_MAPPING_DRUM_CONTROL_STATE_2",
            "CONTROLLER_MAPPING_DRUM_CONTROL_STATE_3",
            "CONTROLLER_MAPPING_DRUM_CONTROL_STATE_4"), factory.feedbackHardwareIDs);
        assertEquals (factory.createdButtons, factory.feedbackButtons);
        physicalHarnesses.values ().forEach (harness -> assertEquals (1, harness.unbinds));
        physicalButtons.forEach ( (control, button) -> assertSame (button, host.physicalButtons ().get (control)));
        assertEquals (Set.copyOf (CoreControllerMappings.DRUM_CONTROL_PADS), host.mappingButtons ().keySet ());
        assertTrue (host.snapshot ().available ());
        assertTrue (host.snapshot ().supports (CoreControllerMappings.DRUM_CONTROL_PADS.getFirst ()));
        assertFalse (host.snapshot ().isOn (CoreControllerMappings.DRUM_CONTROL_PADS.getFirst ()));
        assertThrows (UnsupportedOperationException.class, host.physicalButtons ()::clear);
        assertThrows (UnsupportedOperationException.class, host.mappingButtons ()::clear);
        assertThrows (UnsupportedOperationException.class, host.snapshot ().states ()::clear);

        final var beforeUpdate = host.snapshot ();
        factory.feedbackObservers.getFirst ().accept (true);
        assertFalse (beforeUpdate.isOn (CoreControllerMappings.DRUM_CONTROL_PADS.getFirst ()));
        assertTrue (host.snapshot ().isOn (CoreControllerMappings.DRUM_CONTROL_PADS.getFirst ()));
        assertFalse (host.snapshot ().isOn (CoreControllerMappings.DRUM_CONTROL_PADS.get (1)));
        final var afterFirstUpdate = host.snapshot ();
        factory.feedbackObservers.getFirst ().accept (true);
        assertSame (afterFirstUpdate, host.snapshot ());
        factory.feedbackObservers.get (1).accept (true);
        assertTrue (host.snapshot ().isOn (CoreControllerMappings.DRUM_CONTROL_PADS.getFirst ()));
        assertTrue (host.snapshot ().isOn (CoreControllerMappings.DRUM_CONTROL_PADS.get (1)));
        factory.feedbackObservers.getFirst ().accept (false);
        assertFalse (host.snapshot ().isOn (CoreControllerMappings.DRUM_CONTROL_PADS.getFirst ()));
        assertTrue (host.snapshot ().isOn (CoreControllerMappings.DRUM_CONTROL_PADS.get (1)));
    }


    @Test
    void rejectsIncompletePhysicalTopology ()
    {
        final Map<ControlId, IHwButton> incomplete = Map.of (CoreControls.DRUM_CONTROL_PADS.getFirst (), new ButtonHarness ().button ());
        assertThrows (IllegalArgumentException.class, () -> new ControllerMappingHost (new FactoryHarness ().factory (), 0, incomplete));
    }


    private static Map<ControlId, ButtonHarness> physicalHarnesses ()
    {
        final Map<ControlId, ButtonHarness> buttons = new LinkedHashMap<> ();
        for (final ControlId control: CoreControls.DRUM_CONTROL_PADS)
            buttons.put (control, new ButtonHarness ());
        return buttons;
    }


    private static final class ButtonHarness
    {
        private int unbinds;
        private final IHwButton button = (IHwButton) Proxy.newProxyInstance (IHwButton.class.getClassLoader (), new Class<?> [] {IHwButton.class}, (proxy, method, arguments) -> {
            if (method.getName ().equals ("unbind"))
                this.unbinds++;
            return null;
        });


        private IHwButton button ()
        {
            return this.button;
        }
    }


    private static final class FactoryHarness
    {
        private final List<String> buttonHardwareIDs = new ArrayList<> ();
        private final List<String> buttonLabels = new ArrayList<> ();
        private final List<IHwButton> createdButtons = new ArrayList<> ();
        private final List<String> feedbackHardwareIDs = new ArrayList<> ();
        private final List<IHwButton> feedbackButtons = new ArrayList<> ();
        private final List<Consumer<Boolean>> feedbackObservers = new ArrayList<> ();


        private IHwSurfaceFactory factory ()
        {
            return (IHwSurfaceFactory) Proxy.newProxyInstance (IHwSurfaceFactory.class.getClassLoader (), new Class<?> [] {IHwSurfaceFactory.class}, (proxy, method, arguments) -> {
                if (method.getName ().equals ("createButton") && arguments[1] instanceof final String hardwareID)
                {
                    final IHwButton button = new ButtonHarness ().button ();
                    this.buttonHardwareIDs.add (hardwareID);
                    this.buttonLabels.add ((String) arguments[2]);
                    this.createdButtons.add (button);
                    return button;
                }
                if (method.getName ().equals ("installMappedBooleanFeedback"))
                {
                    this.feedbackHardwareIDs.add ((String) arguments[1]);
                    this.feedbackButtons.add ((IHwButton) arguments[2]);
                    @SuppressWarnings("unchecked")
                    final Consumer<Boolean> observer = (Consumer<Boolean>) arguments[3];
                    this.feedbackObservers.add (observer);
                }
                return null;
            });
        }
    }
}
