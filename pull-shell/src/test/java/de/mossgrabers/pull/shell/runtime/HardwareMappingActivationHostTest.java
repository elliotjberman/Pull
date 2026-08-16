// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.pull.core.api.ControlId;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/** Lifecycle tests for the mutually exclusive learned-action and stable-dispatch lanes. */
class HardwareMappingActivationHostTest
{
    @Test
    void switchesIdleControlsBetweenExactlyOneInputLane ()
    {
        final ControlId first = new ControlId ("push.pad.29");
        final ControlId second = new ControlId ("push.pad.30");
        final Binding firstMapping = new Binding (true);
        final Binding secondMapping = new Binding (true);
        final Binding firstDispatch = new Binding (false);
        final Binding secondDispatch = new Binding (false);
        final HardwareMappingActivationHost host = new HardwareMappingActivationHost (
            Map.of (first, firstMapping.button (), second, secondMapping.button ()),
            Map.of (first, firstDispatch.button (), second, secondDispatch.button ()),
            ignored -> true);

        assertEquals (Set.of (first, second), host.activeMappings ());
        host.request (Set.of ());
        host.request (Set.of ());
        firstMapping.button ().trigger ();
        firstDispatch.button ().trigger ();
        assertEquals (1, firstMapping.unbinds);
        assertEquals (1, firstDispatch.rebinds);
        assertEquals (0, firstMapping.fires);
        assertEquals (1, firstDispatch.fires);
        assertEquals (Set.of (), host.activeMappings ());

        host.request (Set.of (first, second));
        firstMapping.button ().trigger ();
        firstDispatch.button ().trigger ();
        assertEquals (1, firstDispatch.unbinds);
        assertEquals (1, firstMapping.rebinds);
        assertEquals (1, firstMapping.fires);
        assertEquals (1, firstDispatch.fires);
        assertEquals (Set.of (first, second), host.activeMappings ());
    }


    @Test
    void retainsOnlyOldReleaseUntilHeldMappingGestureEnds ()
    {
        final ControlId first = new ControlId ("push.pad.29");
        final ControlId second = new ControlId ("push.pad.30");
        final Binding firstMapping = new Binding (true);
        final Binding secondMapping = new Binding (true);
        final Binding firstDispatch = new Binding (false);
        final Binding secondDispatch = new Binding (false);
        final Set<ControlId> held = new HashSet<> (Set.of (first));
        final HardwareMappingActivationHost host = new HardwareMappingActivationHost (
            Map.of (first, firstMapping.button (), second, secondMapping.button ()),
            Map.of (first, firstDispatch.button (), second, secondDispatch.button ()),
            control -> !held.contains (control));

        host.request (Set.of ());
        firstMapping.button ().trigger ();
        firstDispatch.button ().trigger ();
        assertEquals (1, firstMapping.unbindPresses);
        assertEquals (0, firstMapping.unbinds);
        assertEquals (0, firstDispatch.rebinds);
        assertEquals (1, secondMapping.unbinds);
        assertEquals (1, secondDispatch.rebinds);
        assertEquals (Set.of (), host.activeMappings ());

        held.remove (first);
        host.request (Set.of ());
        firstDispatch.button ().trigger ();
        assertEquals (1, firstMapping.unbinds);
        assertEquals (1, firstDispatch.rebinds);
        assertEquals (1, firstDispatch.fires);
    }


    @Test
    void resolvesDesiredLaneAgainOnlyAfterHeldDispatchGestureEnds ()
    {
        final ControlId control = new ControlId ("push.pad.29");
        final Binding mapping = new Binding (true);
        final Binding dispatch = new Binding (false);
        final Set<ControlId> held = new HashSet<> ();
        final HardwareMappingActivationHost host = new HardwareMappingActivationHost (
            Map.of (control, mapping.button ()),
            Map.of (control, dispatch.button ()),
            candidate -> !held.contains (candidate));
        host.request (Set.of ());

        held.add (control);
        host.request (Set.of (control));
        host.request (Set.of ());
        mapping.button ().trigger ();
        dispatch.button ().trigger ();
        assertEquals (1, dispatch.unbindPresses);
        assertEquals (0, mapping.fires);
        assertEquals (0, dispatch.fires);
        assertEquals (Set.of (), host.activeMappings ());

        held.remove (control);
        host.request (Set.of ());
        dispatch.button ().trigger ();
        assertEquals (1, dispatch.unbinds);
        assertEquals (2, dispatch.rebinds);
        assertEquals (1, dispatch.fires);
        assertEquals (Set.of (), host.activeMappings ());
    }


    @Test
    void rejectsMismatchedOrUnknownControls ()
    {
        final ControlId installed = new ControlId ("push.pad.29");
        final ControlId other = new ControlId ("push.pad.30");
        assertThrows (IllegalArgumentException.class, () -> new HardwareMappingActivationHost (Map.of (installed, new Binding (true).button ()), Map.of (other, new Binding (false).button ()), ignored -> true));

        final HardwareMappingActivationHost host = new HardwareMappingActivationHost (Map.of (installed, new Binding (true).button ()), Map.of (installed, new Binding (false).button ()), ignored -> true);
        assertThrows (IllegalArgumentException.class, () -> host.request (Set.of (other)));
    }


    private static final class Binding
    {
        private int unbinds;
        private int unbindPresses;
        private int rebinds;
        private int fires;
        private boolean pressActive;


        private Binding (final boolean pressActive)
        {
            this.pressActive = pressActive;
        }


        private IHwButton button ()
        {
            return (IHwButton) Proxy.newProxyInstance (IHwButton.class.getClassLoader (), new Class<?> [] {IHwButton.class}, (proxy, method, arguments) -> {
                if (method.getName ().equals ("unbind"))
                {
                    this.unbinds++;
                    this.pressActive = false;
                }
                else if (method.getName ().equals ("unbindPress"))
                {
                    this.unbindPresses++;
                    this.pressActive = false;
                }
                else if (method.getName ().equals ("rebind"))
                {
                    this.rebinds++;
                    this.pressActive = true;
                }
                else if (method.getName ().equals ("trigger") && this.pressActive)
                    this.fires++;
                return null;
            });
        }
    }
}
