// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.controller.hardware.BindType;
import de.mossgrabers.framework.controller.hardware.ButtonEventArbitrator;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwLight;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.MappedPadLightsSnapshot;
import de.mossgrabers.pull.core.api.output.RgbColor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Bounded manual-mapping feedback and alternate-dispatch topology tests. */
class MappedPadLightHostTest
{
    @Test
    void createsFourInitiallyDisabledDispatchControlsForwardingToOriginalPads ()
    {
        final List<String> hardwareIDs = new ArrayList<> ();
        final List<TopologyButton> createdButtons = new ArrayList<> ();
        final IHwSurfaceFactory factory = (IHwSurfaceFactory) Proxy.newProxyInstance (IHwSurfaceFactory.class.getClassLoader (), new Class<?> [] {IHwSurfaceFactory.class}, (proxy, method, arguments) -> {
            if (method.getName ().equals ("createButton"))
            {
                hardwareIDs.add ((String) arguments[1]);
                final TopologyButton button = new TopologyButton ();
                createdButtons.add (button);
                return button.button ();
            }
            return null;
        });
        final IMidiInput input = (IMidiInput) Proxy.newProxyInstance (IMidiInput.class.getClassLoader (), new Class<?> [] {IMidiInput.class}, (proxy, method, arguments) -> null);
        final IPadGrid grid = (IPadGrid) Proxy.newProxyInstance (IPadGrid.class.getClassLoader (), new Class<?> [] {IPadGrid.class}, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getStartNote" -> 36;
            case "translateToController" -> new int [] {2, (Integer) arguments[0]};
            default -> null;
        });
        final Map<ControlId, ForwardedButton> originals = CoreControls.DRUM_CONTROL_PADS.stream ().collect (Collectors.toUnmodifiableMap (control -> control, control -> new ForwardedButton ()));
        final Map<ControlId, IHwButton> originalButtons = originals.entrySet ().stream ().collect (Collectors.toUnmodifiableMap (Map.Entry::getKey, entry -> entry.getValue ().button ()));
        final List<IHwLight> lights = List.of (fakeLight (), fakeLight (), fakeLight (), fakeLight ());

        final MappedPadLightHost host = new MappedPadLightHost (factory, 0, input, grid, originalButtons, lights);

        assertEquals (List.of ("DRUM_CONTROL_PAD_DISPATCH_1", "DRUM_CONTROL_PAD_DISPATCH_2", "DRUM_CONTROL_PAD_DISPATCH_3", "DRUM_CONTROL_PAD_DISPATCH_4"), hardwareIDs);
        assertEquals (originalButtons, host.mappingButtons ());
        assertEquals (Set.copyOf (CoreControls.DRUM_CONTROL_PADS), host.dispatchButtons ().keySet ());
        for (int slot = 0; slot < createdButtons.size (); slot++)
        {
            final TopologyButton button = createdButtons.get (slot);
            assertEquals (1, button.midiBinds);
            assertEquals (1, button.commandBinds);
            assertEquals (1, button.arbitratorInstalls);
            assertEquals (1, button.unbinds);
            assertEquals (BindType.NOTE, button.type);
            assertEquals (2, button.channel);
            assertEquals (64 + slot, button.control);
        }

        final TopologyButton dispatch = createdButtons.get (0);
        final ForwardedButton original = originals.get (CoreControls.DRUM_CONTROL_PADS.get (0));
        dispatch.arbitrator.arbitrate (ButtonEvent.DOWN, 64, () -> {});
        dispatch.arbitrator.arbitrate (ButtonEvent.LONG, 64, () -> {});
        dispatch.arbitrator.arbitrate (ButtonEvent.UP, 0, () -> {});
        assertEquals (List.of (ButtonEvent.DOWN, ButtonEvent.UP), original.events);
        assertEquals (Math.nextUp (64 / 127.0), original.velocities.get (0));
        assertEquals (0, original.velocities.get (1));
    }


    @Test
    void installsObserversOnOriginalPadLightsAndPublishesLaterMappedCallbacks ()
    {
        final List<Consumer<Optional<ColorEx>>> observers = new ArrayList<> ();
        final List<IHwLight> lights = new ArrayList<> ();
        for (int index = 0; index < MappedPadLightsSnapshot.CAPACITY; index++)
        {
            observers.add (null);
            final int capturedIndex = index;
            lights.add ((IHwLight) Proxy.newProxyInstance (IHwLight.class.getClassLoader (), new Class<?> [] {IHwLight.class}, (proxy, method, arguments) -> {
                if (method.getName ().equals ("installMappedColorObserver"))
                {
                    @SuppressWarnings("unchecked")
                    final Consumer<Optional<ColorEx>> observer = (Consumer<Optional<ColorEx>>) arguments[0];
                    observers.set (capturedIndex, observer);
                }
                return null;
            }));
        }

        final Map<ControlId, IHwButton> mappingButtons = buttons ();
        final Map<ControlId, IHwButton> dispatchButtons = buttons ();
        final MappedPadLightHost host = new MappedPadLightHost (mappingButtons, dispatchButtons, lights);
        assertTrue (observers.stream ().allMatch (java.util.Objects::nonNull));
        assertTrue (host.snapshot ().available ());
        assertEquals (mappingButtons, host.mappingButtons ());
        assertEquals (dispatchButtons, host.dispatchButtons ());
        assertFalse (host.snapshot ().controlPad (0).mapped ());

        observers.get (0).accept (Optional.of (new ColorEx (0.25, 0.5, 1)));
        assertEquals (new MappedPadLightsSnapshot.Pad (true, new RgbColor (64, 128, 255)), host.snapshot ().controlPad (0));

        observers.get (0).accept (Optional.empty ());
        assertFalse (host.snapshot ().controlPad (0).mapped ());
    }


    @Test
    void rejectsIncompleteOrMismatchedTopologies ()
    {
        final Map<ControlId, IHwButton> complete = buttons ();
        final Map<ControlId, IHwButton> incomplete = Map.of (CoreControls.DRUM_CONTROL_PADS.get (0), fakeButton ());
        final List<IHwLight> lights = List.of (fakeLight (), fakeLight (), fakeLight (), fakeLight ());
        assertThrows (IllegalArgumentException.class, () -> new MappedPadLightHost (incomplete, complete, lights));
        assertThrows (IllegalArgumentException.class, () -> new MappedPadLightHost (complete, incomplete, lights));
        assertThrows (IllegalArgumentException.class, () -> new MappedPadLightHost (complete, complete, List.of (fakeLight ())));
    }


    private static Map<ControlId, IHwButton> buttons ()
    {
        return CoreControls.DRUM_CONTROL_PADS.stream ().collect (Collectors.toUnmodifiableMap (control -> control, control -> fakeButton ()));
    }


    private static IHwLight fakeLight ()
    {
        return (IHwLight) Proxy.newProxyInstance (IHwLight.class.getClassLoader (), new Class<?> [] {IHwLight.class}, (proxy, method, arguments) -> null);
    }


    private static IHwButton fakeButton ()
    {
        return (IHwButton) Proxy.newProxyInstance (IHwButton.class.getClassLoader (), new Class<?> [] {IHwButton.class}, (proxy, method, arguments) -> null);
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
