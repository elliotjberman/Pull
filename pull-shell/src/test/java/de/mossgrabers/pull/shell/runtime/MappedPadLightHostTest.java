// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.controller.hardware.BindType;
import de.mossgrabers.framework.controller.hardware.ButtonEventArbitrator;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.MappedPadLightsSnapshot;

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


/** Bounded manual-mapping feedback and alternate-dispatch topology tests. */
class MappedPadLightHostTest
{
    @Test
    void createsBooleanFeedbackAndInitiallyDisabledDispatchControlsOnOriginalPads ()
    {
        final FactoryHarness factory = new FactoryHarness ();
        final IMidiInput input = (IMidiInput) Proxy.newProxyInstance (IMidiInput.class.getClassLoader (), new Class<?> [] {IMidiInput.class}, (proxy, method, arguments) -> null);
        final IPadGrid grid = (IPadGrid) Proxy.newProxyInstance (IPadGrid.class.getClassLoader (), new Class<?> [] {IPadGrid.class}, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getStartNote" -> 36;
            case "translateToController" -> new int [] {2, (Integer) arguments[0]};
            default -> null;
        });
        final Map<ControlId, ForwardedButton> originals = new LinkedHashMap<> ();
        final Map<ControlId, IHwButton> originalButtons = new LinkedHashMap<> ();
        for (final ControlId control: CoreControls.DRUM_CONTROL_PADS)
        {
            final ForwardedButton original = new ForwardedButton ();
            originals.put (control, original);
            originalButtons.put (control, original.button ());
        }

        final MappedPadLightHost host = new MappedPadLightHost (factory.factory (), 0, input, grid, originalButtons);

        assertEquals (List.of ("DRUM_CONTROL_PAD_MAPPING_STATE_1", "DRUM_CONTROL_PAD_MAPPING_STATE_2", "DRUM_CONTROL_PAD_MAPPING_STATE_3", "DRUM_CONTROL_PAD_MAPPING_STATE_4"), factory.feedbackHardwareIDs);
        assertEquals (List.copyOf (originalButtons.values ()), factory.feedbackButtons);
        assertEquals (List.of ("DRUM_CONTROL_PAD_DISPATCH_1", "DRUM_CONTROL_PAD_DISPATCH_2", "DRUM_CONTROL_PAD_DISPATCH_3", "DRUM_CONTROL_PAD_DISPATCH_4"), factory.dispatchHardwareIDs);
        assertEquals (originalButtons.keySet (), host.mappingButtons ().keySet ());
        for (final ControlId control: CoreControls.DRUM_CONTROL_PADS)
            assertSame (originalButtons.get (control), host.mappingButtons ().get (control));
        assertEquals (Set.copyOf (CoreControls.DRUM_CONTROL_PADS), host.dispatchButtons ().keySet ());
        assertTrue (host.snapshot ().available ());
        assertFalse (host.snapshot ().controlPad (0).on ());

        factory.feedbackObservers.get (0).accept (true);
        assertEquals (new MappedPadLightsSnapshot.Pad (true), host.snapshot ().controlPad (0));
        factory.feedbackObservers.get (0).accept (false);
        assertFalse (host.snapshot ().controlPad (0).on ());

        for (int slot = 0; slot < factory.createdButtons.size (); slot++)
        {
            final TopologyButton button = factory.createdButtons.get (slot);
            assertEquals (1, button.midiBinds);
            assertEquals (1, button.commandBinds);
            assertEquals (1, button.arbitratorInstalls);
            assertEquals (1, button.unbinds);
            assertEquals (BindType.NOTE, button.type);
            assertEquals (2, button.channel);
            assertEquals (64 + slot, button.control);
        }

        final TopologyButton dispatch = factory.createdButtons.get (0);
        final ForwardedButton original = originals.get (CoreControls.DRUM_CONTROL_PADS.get (0));
        dispatch.arbitrator.arbitrate (ButtonEvent.DOWN, 64, () -> {});
        dispatch.arbitrator.arbitrate (ButtonEvent.LONG, 64, () -> {});
        dispatch.arbitrator.arbitrate (ButtonEvent.UP, 0, () -> {});
        assertEquals (List.of (ButtonEvent.DOWN, ButtonEvent.UP), original.events);
        assertEquals (Math.nextUp (64 / 127.0), original.velocities.get (0));
        assertEquals (0, original.velocities.get (1));
    }


    @Test
    void rejectsIncompleteOrMismatchedTopologies ()
    {
        final Map<ControlId, IHwButton> complete = buttons ();
        final Map<ControlId, IHwButton> incomplete = Map.of (CoreControls.DRUM_CONTROL_PADS.get (0), fakeButton ());
        assertThrows (IllegalArgumentException.class, () -> new MappedPadLightHost (incomplete, complete));
        assertThrows (IllegalArgumentException.class, () -> new MappedPadLightHost (complete, incomplete));
    }


    private static Map<ControlId, IHwButton> buttons ()
    {
        final Map<ControlId, IHwButton> buttons = new LinkedHashMap<> ();
        for (final ControlId control: CoreControls.DRUM_CONTROL_PADS)
            buttons.put (control, fakeButton ());
        return Map.copyOf (buttons);
    }


    private static IHwButton fakeButton ()
    {
        return (IHwButton) Proxy.newProxyInstance (IHwButton.class.getClassLoader (), new Class<?> [] {IHwButton.class}, (proxy, method, arguments) -> null);
    }


    private static final class FactoryHarness
    {
        private final List<String> dispatchHardwareIDs = new ArrayList<> ();
        private final List<TopologyButton> createdButtons = new ArrayList<> ();
        private final List<String> feedbackHardwareIDs = new ArrayList<> ();
        private final List<IHwButton> feedbackButtons = new ArrayList<> ();
        private final List<Consumer<Boolean>> feedbackObservers = new ArrayList<> ();


        private IHwSurfaceFactory factory ()
        {
            return (IHwSurfaceFactory) Proxy.newProxyInstance (IHwSurfaceFactory.class.getClassLoader (), new Class<?> [] {IHwSurfaceFactory.class}, (proxy, method, arguments) -> {
                if (method.getName ().equals ("createButton") && arguments[1] instanceof final String hardwareID)
                {
                    this.dispatchHardwareIDs.add (hardwareID);
                    final TopologyButton button = new TopologyButton ();
                    this.createdButtons.add (button);
                    return button.button ();
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


    private static final class ForwardedButton
    {
        private final List<ButtonEvent> events = new ArrayList<> ();
        private final List<Double> velocities = new ArrayList<> ();


        private IHwButton button ()
        {
            return (IHwButton) Proxy.newProxyInstance (IHwButton.class.getClassLoader (), new Class<?> [] {IHwButton.class}, (proxy, method, arguments) -> {
                if (method.getName ().equals ("trigger") && arguments != null && arguments.length == 2)
                {
                    this.events.add ((ButtonEvent) arguments[0]);
                    this.velocities.add ((Double) arguments[1]);
                }
                return null;
            });
        }
    }


    private static final class TopologyButton
    {
        private int midiBinds;
        private int commandBinds;
        private int arbitratorInstalls;
        private int unbinds;
        private BindType type;
        private int channel;
        private int control;
        private ButtonEventArbitrator arbitrator;


        private IHwButton button ()
        {
            return (IHwButton) Proxy.newProxyInstance (IHwButton.class.getClassLoader (), new Class<?> [] {IHwButton.class}, (proxy, method, arguments) -> {
                if (method.getName ().equals ("bind") && arguments.length == 4)
                {
                    this.midiBinds++;
                    this.type = (BindType) arguments[1];
                    this.channel = (Integer) arguments[2];
                    this.control = (Integer) arguments[3];
                }
                else if (method.getName ().equals ("bind") && arguments.length == 1)
                    this.commandBinds++;
                else if (method.getName ().equals ("installEventArbitrator"))
                {
                    this.arbitratorInstalls++;
                    this.arbitrator = (ButtonEventArbitrator) arguments[0];
                }
                else if (method.getName ().equals ("unbind"))
                    this.unbinds++;
                return null;
            });
        }
    }
}
