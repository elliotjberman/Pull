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


/** Lifecycle tests for view-scoped Bitwig hardware-action activation. */
class HardwareMappingActivationHostTest
{
    @Test
    void fencesEachMappingActionWithoutChangingPermanentPadDispatch ()
    {
        final ControlId first = new ControlId ("push.pad.29");
        final ControlId second = new ControlId ("push.pad.30");
        final Binding firstBinding = new Binding ();
        final Binding secondBinding = new Binding ();
        final Binding permanentPadDispatch = new Binding (true);
        final IHwButton firstMappingPad = firstBinding.button ();
        final IHwButton permanentPad = permanentPadDispatch.button ();
        final Set<ControlId> held = new HashSet<> ();
        final Map<ControlId, IHwButton> buttons = Map.of (first, firstMappingPad, second, secondBinding.button ());

        final HardwareMappingActivationHost host = new HardwareMappingActivationHost (buttons, control -> !held.contains (control));
        permanentPad.trigger ();
        assertEquals (0, firstBinding.unbinds);
        assertEquals (0, secondBinding.unbinds);

        host.request (Set.of (first, second));
        host.request (Set.of (first, second));
        firstMappingPad.trigger ();
        permanentPad.trigger ();
        assertEquals (1, firstBinding.rebinds);
        assertEquals (1, secondBinding.rebinds);
        assertEquals (Set.of (first, second), host.activeMappings ());
        assertEquals (1, firstBinding.fires);

        held.add (first);
        host.request (Set.of ());
        firstMappingPad.trigger ();
        permanentPad.trigger ();
        assertEquals (0, firstBinding.unbinds);
        assertEquals (1, firstBinding.unbindPresses);
        assertEquals (1, secondBinding.unbinds);
        assertEquals (Set.of (), host.activeMappings ());
        assertEquals (1, firstBinding.fires);

        held.remove (first);
        host.request (Set.of ());
        firstMappingPad.trigger ();
        assertEquals (1, firstBinding.unbinds);
        assertEquals (1, secondBinding.unbinds);
        assertEquals (1, firstBinding.fires);
        assertEquals (0, permanentPadDispatch.unbinds);
        assertEquals (0, permanentPadDispatch.rebinds);
        assertEquals (3, permanentPadDispatch.fires);
    }


    @Test
    void rejectsControlsOutsideTheInstalledInventory ()
    {
        final HardwareMappingActivationHost host = new HardwareMappingActivationHost (Map.of (new ControlId ("push.pad.29"), new Binding ().button ()), ignored -> true);
        assertThrows (IllegalArgumentException.class, () -> host.request (Set.of (new ControlId ("push.pad.28"))));
    }


    private static final class Binding
    {
        private int unbinds;
        private int unbindPresses;
        private int rebinds;
        private int fires;
        private boolean pressActive;


        private Binding ()
        {
            this (false);
        }


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
                else if (method.getName ().equals ("trigger"))
                {
                    if (this.pressActive)
                        this.fires++;
                }
                return null;
            });
        }
    }
}
