// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControlId;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Lifecycle tests for the single learned identity and raw ordinary-dispatch lane. */
class HardwareMappingActivationHostTest
{
    @Test
    void switchesIdleControlBetweenMatcherAndRawDispatch ()
    {
        final ControlId control = new ControlId ("push.pad.29");
        final Binding mapping = new Binding ();
        final AtomicBoolean idle = new AtomicBoolean (true);
        final HardwareMappingActivationHost host = new HardwareMappingActivationHost (Map.of (control, mapping.button ()), ignored -> idle.get ());

        assertEquals (1, mapping.unbindReleases);
        assertEquals (Set.of (control), host.activeMappings ());
        assertFalse (host.dispatchRaw (control, ButtonEvent.DOWN, 0.5));

        host.request (Set.of ());
        assertEquals (1, mapping.unbindPresses);
        assertEquals (Set.of (), host.activeMappings ());
        assertTrue (host.dispatchRaw (control, ButtonEvent.DOWN, 0.5));
        idle.set (false);
        assertTrue (host.dispatchRaw (control, ButtonEvent.UP, 0));
        assertEquals (2, mapping.manualEvents);

        idle.set (true);
        host.request (Set.of (control));
        assertEquals (1, mapping.rebinds);
        assertEquals (2, mapping.unbindReleases);
        assertEquals (Set.of (control), host.activeMappings ());
        assertFalse (host.dispatchRaw (control, ButtonEvent.DOWN, 0.5));
    }


    @Test
    void retainsOnlyInstalledMatcherReleaseUntilMappingGestureEnds ()
    {
        final ControlId control = new ControlId ("push.pad.29");
        final Binding mapping = new Binding ();
        final AtomicBoolean idle = new AtomicBoolean (false);
        final HardwareMappingActivationHost host = new HardwareMappingActivationHost (Map.of (control, mapping.button ()), candidate -> idle.get ());

        host.request (Set.of ());
        assertEquals (1, mapping.unbindPresses);
        assertFalse (host.dispatchRaw (control, ButtonEvent.DOWN, 0.5));
        assertTrue (host.dispatchRaw (control, ButtonEvent.UP, 0));
        assertEquals (Set.of (), host.activeMappings ());

        idle.set (true);
        host.request (Set.of ());
        assertTrue (host.dispatchRaw (control, ButtonEvent.DOWN, 0.5));
    }


    @Test
    void rawReleaseFinishesOldDispatchBeforeLatestDesiredMatcherActivates ()
    {
        final ControlId control = new ControlId ("push.pad.29");
        final Binding mapping = new Binding ();
        final AtomicBoolean idle = new AtomicBoolean (true);
        final HardwareMappingActivationHost host = new HardwareMappingActivationHost (Map.of (control, mapping.button ()), candidate -> idle.get ());
        host.request (Set.of ());

        idle.set (false);
        assertTrue (host.dispatchRaw (control, ButtonEvent.DOWN, 0.5));
        host.request (Set.of (control));
        host.request (Set.of ());
        host.request (Set.of (control));
        assertFalse (host.dispatchRaw (control, ButtonEvent.DOWN, 0.5));
        assertEquals (1, mapping.manualEvents);
        assertEquals (Set.of (), host.activeMappings ());

        assertTrue (host.dispatchRaw (control, ButtonEvent.UP, 0));
        idle.set (true);
        host.request (Set.of (control));
        assertEquals (2, mapping.manualEvents);
        assertEquals (1, mapping.rebinds);
        assertEquals (Set.of (control), host.activeMappings ());
    }


    @Test
    void rejectsUnknownControls ()
    {
        final ControlId installed = new ControlId ("push.pad.29");
        final ControlId other = new ControlId ("push.pad.30");
        final HardwareMappingActivationHost host = new HardwareMappingActivationHost (Map.of (installed, new Binding ().button ()), ignored -> true);

        assertThrows (IllegalArgumentException.class, () -> host.request (Set.of (other)));
        assertThrows (IllegalArgumentException.class, () -> host.dispatchRaw (other, ButtonEvent.DOWN, 1));
    }


    private static final class Binding
    {
        private int unbindPresses;
        private int unbindReleases;
        private int rebinds;
        private int manualEvents;


        private IHwButton button ()
        {
            return (IHwButton) Proxy.newProxyInstance (IHwButton.class.getClassLoader (), new Class<?> [] {IHwButton.class}, (proxy, method, arguments) -> {
                switch (method.getName ())
                {
                    case "unbindPress" -> this.unbindPresses++;
                    case "unbindRelease" -> this.unbindReleases++;
                    case "rebind" -> this.rebinds++;
                    case "trigger" -> this.manualEvents++;
                    default -> { }
                }
                return null;
            });
        }
    }
}
