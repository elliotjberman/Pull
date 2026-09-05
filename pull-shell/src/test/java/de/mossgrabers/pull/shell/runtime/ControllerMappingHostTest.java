// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.hardware.IHwAbsoluteControl;
import de.mossgrabers.framework.controller.hardware.IHwAbsoluteKnob;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.ControllerMappingTarget;
import de.mossgrabers.pull.core.api.CoreControllerMappings;
import de.mossgrabers.pull.core.api.PushControlIds;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Bounded semantic controller-mapping topology tests. */
class ControllerMappingHostTest
{
    @Test
    void createsAbsoluteMappingControlsAndLeavesPhysicalButtonsRawDispatchOnly ()
    {
        final FactoryHarness factory = new FactoryHarness ();
        final Map<ControlId, ButtonHarness> physicalHarnesses = physicalHarnesses ();
        final Map<ControlId, IHwButton> physicalButtons = new LinkedHashMap<> ();
        physicalHarnesses.forEach ( (control, harness) -> physicalButtons.put (control, harness.button));

        final ControllerMappingHost host = new ControllerMappingHost (factory.factory (), 0, physicalButtons);

        assertEquals (List.of (
            "CONTROLLER_MAPPING_DRUM_CONTROL_VALUE_1",
            "CONTROLLER_MAPPING_DRUM_CONTROL_VALUE_2",
            "CONTROLLER_MAPPING_DRUM_CONTROL_VALUE_3",
            "CONTROLLER_MAPPING_DRUM_CONTROL_VALUE_4"), factory.hardwareIDs.subList (0, 4));
        assertEquals (List.of (
            "Drum Controller Toggle 1",
            "Drum Controller Toggle 2",
            "Drum Controller Toggle 3",
            "Drum Controller Toggle 4"), factory.labels.subList (0, 4));
        assertEquals (516, factory.createdControls.size ());
        assertEquals (516, new LinkedHashSet<> (factory.hardwareIDs).size (), "every permanent endpoint needs its own persisted host identity");
        for (int bank = 0; bank < CoreControllerMappings.TRACK_BANK_COUNT; bank++)
            for (int slot = 0; slot < CoreControllerMappings.CONTROLS_PER_TRACK; slot++)
            {
                final int index = 4 + bank * CoreControllerMappings.CONTROLS_PER_TRACK + slot;
                assertEquals ("CONTROLLER_MAPPING_TRACK_" + (bank + 1) + "_CONTROL_VALUE_" + (slot + 1), factory.hardwareIDs.get (index));
                assertEquals ("Track " + (bank + 1) + " Toggle " + (slot + 1), factory.labels.get (index));
                assertSame (factory.createdControls.get (index), host.mappingControls ().get (CoreControllerMappings.trackBank (bank).get (slot)));
            }
        factory.controlHarnesses.forEach (harness -> assertEquals (1, harness.disableTakeOverCalls));
        assertEquals (factory.createdControls, factory.feedbackControls);
        assertEquals (64, physicalHarnesses.size ());
        physicalHarnesses.values ().forEach (harness -> assertEquals (1, harness.unbinds));
        physicalButtons.forEach ( (control, button) -> assertSame (button, host.physicalButtons ().get (control)));
        final Set<ControllerMappingId> installed = new LinkedHashSet<> (CoreControllerMappings.DRUM_CONTROL_PADS);
        installed.addAll (CoreControllerMappings.TRACK_CONTROL_PADS);
        assertEquals (installed, host.mappingControls ().keySet ());
        assertFalse (host.snapshot ().available ());
        assertTrue (host.snapshot ().targets ().isEmpty ());
        assertThrows (UnsupportedOperationException.class, host.physicalButtons ()::clear);
        assertThrows (UnsupportedOperationException.class, host.mappingControls ()::clear);
        assertThrows (UnsupportedOperationException.class, host.snapshot ().targets ()::clear);

        final var firstMapping = CoreControllerMappings.DRUM_CONTROL_PADS.getFirst ();
        final var secondMapping = CoreControllerMappings.DRUM_CONTROL_PADS.get (1);
        final var beforeUpdate = host.snapshot ();
        factory.feedbackObservers.getFirst ().accept (true, 0.8);
        factory.feedbackObservers.get (1).accept (true, 0.2);
        factory.feedbackObservers.get (2).accept (false, 0.8);
        assertTrue (host.snapshot ().available (), "ready endpoints must not wait for every unrelated bank");
        assertFalse (host.snapshot ().supports (CoreControllerMappings.DRUM_CONTROL_PADS.get (3)));
        factory.feedbackObservers.get (3).accept (false, 0.0);
        assertTrue (beforeUpdate.targets ().isEmpty ());
        assertFalse (beforeUpdate.available ());
        assertTrue (host.snapshot ().available ());
        assertTrue (host.snapshot ().supports (firstMapping));
        assertEquals (new ControllerMappingTarget (true, 0.8), host.snapshot ().targets ().get (firstMapping));
        assertEquals (new ControllerMappingTarget (true, 0.2), host.snapshot ().targets ().get (secondMapping));
        assertEquals (new ControllerMappingTarget (false, 0.8), host.snapshot ().targets ().get (CoreControllerMappings.DRUM_CONTROL_PADS.get (2)));
        final var afterFirstUpdate = host.snapshot ();
        factory.feedbackObservers.getFirst ().accept (true, 0.8);
        assertSame (afterFirstUpdate, host.snapshot ());
        factory.feedbackObservers.get (1).accept (true, 0.4);
        assertEquals (new ControllerMappingTarget (true, 0.4), host.snapshot ().targets ().get (secondMapping));
        assertEquals (new ControllerMappingTarget (true, 0.2), afterFirstUpdate.targets ().get (secondMapping));
        factory.feedbackObservers.getFirst ().accept (false, 0.8);
        assertEquals (new ControllerMappingTarget (false, 0.8), host.snapshot ().targets ().get (firstMapping));

    }


    @Test
    void publishesOnlyObservedEndpointsWithoutWaitingForOtherTrackBanks ()
    {
        final FactoryHarness factory = new FactoryHarness ();
        final Map<ControlId, IHwButton> physical = new LinkedHashMap<> ();
        physicalHarnesses ().forEach ((control, harness) -> physical.put (control, harness.button));
        final ControllerMappingHost host = new ControllerMappingHost (factory.factory (), 0, physical);
        final int bank = CoreControllerMappings.TRACK_BANK_COUNT - 1;
        for (int slot = 0; slot < CoreControllerMappings.CONTROLS_PER_TRACK; slot++)
            factory.feedbackObservers.get (4 + bank * CoreControllerMappings.CONTROLS_PER_TRACK + slot).accept (false, 0.0);

        assertEquals (Set.copyOf (CoreControllerMappings.trackBank (bank)), host.snapshot ().targets ().keySet ());
        assertFalse (host.snapshot ().supports (CoreControllerMappings.trackBank (0).getFirst ()));
        CoreControllerMappings.trackBank (bank).forEach (mapping -> assertEquals (new ControllerMappingTarget (false, 0), host.snapshot ().targets ().get (mapping)));
    }


    @Test
    void rejectsIncompletePhysicalTopology ()
    {
        final Map<ControlId, IHwButton> incomplete = Map.of (PushControlIds.pad (29), new ButtonHarness ().button);
        assertThrows (IllegalArgumentException.class, () -> new ControllerMappingHost (new FactoryHarness ().factory (), 0, incomplete));
    }


    private static Map<ControlId, ButtonHarness> physicalHarnesses ()
    {
        final Map<ControlId, ButtonHarness> buttons = new LinkedHashMap<> ();
        for (int index = 1; index <= 64; index++)
            buttons.put (PushControlIds.pad (index), new ButtonHarness ());
        return buttons;
    }


    private static final class ButtonHarness
    {
        private int unbinds;
        private final IHwButton button = proxy (IHwButton.class, (proxy, method, arguments) -> {
            if (method.getName ().equals ("unbind"))
                this.unbinds++;
            return relaxedValue (method.getReturnType ());
        });
    }


    private static final class AbsoluteHarness
    {
        private int disableTakeOverCalls;
        private final IHwAbsoluteKnob control = proxy (IHwAbsoluteKnob.class, (proxy, method, arguments) -> {
            if (method.getName ().equals ("disableTakeOver"))
                this.disableTakeOverCalls++;
            return relaxedValue (method.getReturnType ());
        });
    }


    private static final class FactoryHarness
    {
        private final List<String> hardwareIDs = new ArrayList<> ();
        private final List<String> labels = new ArrayList<> ();
        private final List<AbsoluteHarness> controlHarnesses = new ArrayList<> ();
        private final List<IHwAbsoluteControl> createdControls = new ArrayList<> ();
        private final List<IHwAbsoluteControl> feedbackControls = new ArrayList<> ();
        private final List<BiConsumer<Boolean, Double>> feedbackObservers = new ArrayList<> ();


        private IHwSurfaceFactory factory ()
        {
            return proxy (IHwSurfaceFactory.class, (proxy, method, arguments) -> {
                if (method.getName ().equals ("createAbsoluteKnob") && arguments[1] instanceof final String hardwareID)
                {
                    final AbsoluteHarness harness = new AbsoluteHarness ();
                    this.hardwareIDs.add (hardwareID);
                    this.labels.add ((String) arguments[2]);
                    this.controlHarnesses.add (harness);
                    this.createdControls.add (harness.control);
                    return harness.control;
                }
                if (method.getName ().equals ("installMappedAbsoluteFeedback"))
                {
                    this.feedbackControls.add ((IHwAbsoluteControl) arguments[0]);
                    @SuppressWarnings("unchecked")
                    final BiConsumer<Boolean, Double> observer = (BiConsumer<Boolean, Double>) arguments[1];
                    this.feedbackObservers.add (observer);
                }
                return relaxedValue (method.getReturnType ());
            });
        }
    }


    private static <T> T proxy (final Class<T> type, final java.lang.reflect.InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] {type}, handler));
    }


    private static Object relaxedValue (final Class<?> type)
    {
        if (!type.isPrimitive () || void.class.equals (type))
            return null;
        if (boolean.class.equals (type))
            return Boolean.FALSE;
        return Integer.valueOf (0);
    }
}
