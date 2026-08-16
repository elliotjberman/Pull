// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.CoreControls;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Bounded manual-mapping feedback topology tests. */
class MappedPadLightHostTest
{
    @Test
    void installsBooleanFeedbackOnlyOnTheFourOriginalPads ()
    {
        final FactoryHarness factory = new FactoryHarness ();
        final Map<ControlId, IHwButton> originalButtons = buttons ();

        final MappedPadLightHost host = new MappedPadLightHost (factory.factory (), 0, originalButtons);

        assertEquals (List.of ("DRUM_CONTROL_PAD_MAPPING_STATE_1", "DRUM_CONTROL_PAD_MAPPING_STATE_2", "DRUM_CONTROL_PAD_MAPPING_STATE_3", "DRUM_CONTROL_PAD_MAPPING_STATE_4"), factory.feedbackHardwareIDs);
        assertEquals (originalButtons.size (), factory.feedbackButtons.size ());
        for (int slot = 0; slot < CoreControls.DRUM_CONTROL_PADS.size (); slot++)
            assertSame (originalButtons.get (CoreControls.DRUM_CONTROL_PADS.get (slot)), factory.feedbackButtons.get (slot));
        assertEquals (0, factory.createdButtons);
        assertEquals (originalButtons.keySet (), host.mappingButtons ().keySet ());
        for (final ControlId control: CoreControls.DRUM_CONTROL_PADS)
            assertSame (originalButtons.get (control), host.mappingButtons ().get (control));
        assertTrue (host.snapshot ().available ());
        assertFalse (host.snapshot ().controlPad (0));

        factory.feedbackObservers.get (0).accept (true);
        assertTrue (host.snapshot ().controlPad (0));
        factory.feedbackObservers.get (0).accept (false);
        assertFalse (host.snapshot ().controlPad (0));
    }


    @Test
    void rejectsIncompleteTopology ()
    {
        final Map<ControlId, IHwButton> incomplete = Map.of (CoreControls.DRUM_CONTROL_PADS.get (0), fakeButton ());
        assertThrows (IllegalArgumentException.class, () -> new MappedPadLightHost (incomplete));
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
        private int createdButtons;
        private final List<String> feedbackHardwareIDs = new ArrayList<> ();
        private final List<IHwButton> feedbackButtons = new ArrayList<> ();
        private final List<Consumer<Boolean>> feedbackObservers = new ArrayList<> ();


        private IHwSurfaceFactory factory ()
        {
            return (IHwSurfaceFactory) Proxy.newProxyInstance (IHwSurfaceFactory.class.getClassLoader (), new Class<?> [] {IHwSurfaceFactory.class}, (proxy, method, arguments) -> {
                if (method.getName ().equals ("createButton"))
                    this.createdButtons++;
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
