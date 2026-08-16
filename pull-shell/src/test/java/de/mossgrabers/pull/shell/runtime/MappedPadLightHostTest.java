// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.controller.hardware.BindType;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwLight;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.framework.daw.midi.IMidiInput;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Bounded manual-mapping feedback observation tests for the four dedicated host controls. */
class MappedPadLightHostTest
{
    @Test
    void createsFourSeparateInitiallyDisabledMappingControls ()
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
            if (method.getName ().equals ("createLight"))
                return fakeLight ();
            return null;
        });
        final IMidiInput input = (IMidiInput) Proxy.newProxyInstance (IMidiInput.class.getClassLoader (), new Class<?> [] {IMidiInput.class}, (proxy, method, arguments) -> null);
        final IPadGrid grid = (IPadGrid) Proxy.newProxyInstance (IPadGrid.class.getClassLoader (), new Class<?> [] {IPadGrid.class}, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getStartNote" -> 36;
            case "translateToController" -> new int [] {2, (Integer) arguments[0]};
            default -> null;
        });

        final MappedPadLightHost host = new MappedPadLightHost (factory, 0, input, grid);

        assertEquals (List.of ("DRUM_CONTROL_PAD_MAPPING_1", "DRUM_CONTROL_PAD_MAPPING_2", "DRUM_CONTROL_PAD_MAPPING_3", "DRUM_CONTROL_PAD_MAPPING_4"), hardwareIDs);
        assertEquals (Set.copyOf (CoreControls.DRUM_CONTROL_PADS), host.mappingButtons ().keySet ());
        for (int slot = 0; slot < createdButtons.size (); slot++)
        {
            final TopologyButton button = createdButtons.get (slot);
            assertEquals (1, button.binds);
            assertEquals (1, button.unbinds);
            assertEquals (1, button.lights);
            assertEquals (BindType.NOTE, button.type);
            assertEquals (2, button.channel);
            assertEquals (64 + slot, button.control);
        }
    }


    @Test
    void installsAllObserversAndPublishesOnlyLaterMappedColorCallbacks ()
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

        final Map<ControlId, IHwButton> buttons = CoreControls.DRUM_CONTROL_PADS.stream ().collect (java.util.stream.Collectors.toUnmodifiableMap (control -> control, control -> fakeButton ()));
        final MappedPadLightHost host = new MappedPadLightHost (buttons, lights);
        assertTrue (observers.stream ().allMatch (java.util.Objects::nonNull));
        assertTrue (host.snapshot ().available ());
        assertEquals (buttons, host.mappingButtons ());
        assertFalse (host.snapshot ().controlPad (0).mapped ());

        observers.get (0).accept (Optional.of (new ColorEx (0.25, 0.5, 1)));
        assertEquals (new MappedPadLightsSnapshot.Pad (true, new RgbColor (64, 128, 255)), host.snapshot ().controlPad (0));

        observers.get (0).accept (Optional.empty ());
        assertFalse (host.snapshot ().controlPad (0).mapped ());
    }


    @Test
    void rejectsAnyTopologyOtherThanTheFourControlPads ()
    {
        assertThrows (IllegalArgumentException.class, () -> new MappedPadLightHost (Map.of (CoreControls.DRUM_CONTROL_PADS.get (0), fakeButton ()), List.of (fakeLight ())));
    }


    private static IHwLight fakeLight ()
    {
        return (IHwLight) Proxy.newProxyInstance (IHwLight.class.getClassLoader (), new Class<?> [] {IHwLight.class}, (proxy, method, arguments) -> null);
    }


    private static IHwButton fakeButton ()
    {
        return (IHwButton) Proxy.newProxyInstance (IHwButton.class.getClassLoader (), new Class<?> [] {IHwButton.class}, (proxy, method, arguments) -> null);
    }


    private static final class TopologyButton
    {
        private int binds;
        private int unbinds;
        private int lights;
        private BindType type;
        private int channel;
        private int control;


        private IHwButton button ()
        {
            return (IHwButton) Proxy.newProxyInstance (IHwButton.class.getClassLoader (), new Class<?> [] {IHwButton.class}, (proxy, method, arguments) -> {
                if (method.getName ().equals ("bind") && arguments.length == 4)
                {
                    this.binds++;
                    this.type = (BindType) arguments[1];
                    this.channel = (Integer) arguments[2];
                    this.control = (Integer) arguments[3];
                }
                else if (method.getName ().equals ("unbind"))
                    this.unbinds++;
                else if (method.getName ().equals ("addLight"))
                    this.lights++;
                return null;
            });
        }
    }
}
