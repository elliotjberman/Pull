// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.hardware.IHwAbsoluteControl;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerMappingBinding;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.ControllerMappingValue;
import de.mossgrabers.pull.core.api.ControllerMappingNames;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/** Lifecycle tests for absolute semantic mapping projection and raw ordinary dispatch. */
class HardwareMappingActivationHostTest
{
    private static final ControlId PAD_29 = new ControlId ("push.pad.29");
    private static final ControlId PAD_30 = new ControlId ("push.pad.30");
    private static final ControlId PAD_31 = new ControlId ("push.pad.31");
    private static final ControlId PAD_32 = new ControlId ("push.pad.32");
    private static final ControllerMappingId DRUM_1 = new ControllerMappingId ("drum-controller.control.1");
    private static final ControllerMappingId DRUM_2 = new ControllerMappingId ("drum-controller.control.2");
    private static final ControllerMappingId DRUM_3 = new ControllerMappingId ("drum-controller.control.3");
    private static final ControllerMappingId DRUM_4 = new ControllerMappingId ("drum-controller.control.4");


    @Test
    void switchesIdleControlBetweenAbsoluteMatcherAndRawDispatch ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29), Set.of (DRUM_1));
        final DesiredControllerMappings desired = desired (PAD_29, DRUM_1, ControllerMappingValue.MAXIMUM);

        fixture.host.request (desired);
        assertEquals (desired, fixture.host.activeMappings ());
        assertEquals (new BoundMatcher (PAD_29, ControllerMappingValue.MAXIMUM), fixture.bindings.get (DRUM_1));
        assertEquals (HardwareMappingActivationHost.RawDisposition.MAPPED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.DOWN, 0.5));

        fixture.host.request (DesiredControllerMappings.empty ());
        assertEquals (1, fixture.semantic.get (DRUM_1).unbinds);
        assertEquals (DesiredControllerMappings.empty (), fixture.host.activeMappings ());
        assertEquals (HardwareMappingActivationHost.RawDisposition.DISPATCHED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.DOWN, 0.5));
        fixture.idle.put (PAD_29, Boolean.FALSE);
        assertEquals (HardwareMappingActivationHost.RawDisposition.DISPATCHED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.UP, 0));
        assertEquals (2, fixture.physical.get (PAD_29).manualEvents);
    }


    @Test
    void replayingUnchangedProjectionDoesNotChurnMatcher ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29), Set.of (DRUM_1));
        final DesiredControllerMappings projection = desired (PAD_29, DRUM_1, ControllerMappingValue.MAXIMUM);

        fixture.host.request (projection);
        fixture.host.request (projection);

        assertEquals (1, fixture.bindingCalls);
        assertEquals (0, fixture.semantic.get (DRUM_1).unbinds);
        assertEquals (projection, fixture.host.activeMappings ());
    }


    @Test
    void changingOnlyTheNextValueRebindsTheSameEndpointWhenIdle ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29), Set.of (DRUM_1));
        fixture.host.request (desired (PAD_29, DRUM_1, ControllerMappingValue.MAXIMUM));

        final DesiredControllerMappings minimum = desired (PAD_29, DRUM_1, ControllerMappingValue.MINIMUM);
        fixture.host.request (minimum);

        assertEquals (minimum, fixture.host.activeMappings ());
        assertEquals (2, fixture.bindingCalls);
        assertEquals (1, fixture.semantic.get (DRUM_1).unbinds);
        assertEquals (new BoundMatcher (PAD_29, ControllerMappingValue.MINIMUM), fixture.bindings.get (DRUM_1));
    }


    @Test
    void renamingMetadataDoesNotRetireOrRebindAHeldMatcher ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29), Set.of (DRUM_1));
        final DesiredControllerMappings projection = desired (PAD_29, DRUM_1, ControllerMappingValue.MAXIMUM);
        fixture.host.request (new DesiredControllerMappings (projection.bindings (),
            new ControllerMappingNames ("document", 1, Map.of (DRUM_1, "Kick — Drum Controller Toggle 1"))));
        fixture.idle.put (PAD_29, Boolean.FALSE);
        fixture.host.request (new DesiredControllerMappings (projection.bindings (),
            new ControllerMappingNames ("document", 1, Map.of (DRUM_1, "Percussion — Drum Controller Toggle 1"))));

        assertEquals (1, fixture.bindingCalls);
        assertEquals (0, fixture.semantic.get (DRUM_1).unbinds);
        assertEquals (projection, fixture.host.activeMappings (), "presentation metadata is absent from the native matcher identity");
    }


    @Test
    void changingTheNextValueWaitsForTheCurrentPhysicalRelease ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29), Set.of (DRUM_1));
        fixture.host.request (desired (PAD_29, DRUM_1, ControllerMappingValue.MAXIMUM));
        fixture.idle.put (PAD_29, Boolean.FALSE);

        final DesiredControllerMappings minimum = desired (PAD_29, DRUM_1, ControllerMappingValue.MINIMUM);
        fixture.host.request (minimum);
        assertEquals (DesiredControllerMappings.empty (), fixture.host.activeMappings ());
        assertEquals (1, fixture.semantic.get (DRUM_1).unbinds);
        assertEquals (HardwareMappingActivationHost.RawDisposition.MAPPED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.UP, 0));

        fixture.idle.put (PAD_29, Boolean.TRUE);
        fixture.host.request (minimum);
        assertEquals (minimum, fixture.host.activeMappings ());
        assertEquals (new BoundMatcher (PAD_29, ControllerMappingValue.MINIMUM), fixture.bindings.get (DRUM_1));
    }


    @Test
    void semanticEndpointCannotMoveUntilItsOldPhysicalGestureIsIdle ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29, PAD_30), Set.of (DRUM_1));
        fixture.host.request (desired (PAD_29, DRUM_1, ControllerMappingValue.MAXIMUM));
        fixture.idle.put (PAD_29, Boolean.FALSE);

        final DesiredControllerMappings moved = desired (PAD_30, DRUM_1, ControllerMappingValue.MAXIMUM);
        fixture.host.request (moved);
        assertEquals (DesiredControllerMappings.empty (), fixture.host.activeMappings ());

        fixture.idle.put (PAD_29, Boolean.TRUE);
        fixture.host.request (moved);
        assertEquals (moved, fixture.host.activeMappings ());
        assertEquals (new BoundMatcher (PAD_30, ControllerMappingValue.MAXIMUM), fixture.bindings.get (DRUM_1));
    }


    @Test
    void fourLanesActivateAndChangeIndependently ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29, PAD_30, PAD_31, PAD_32), Set.of (DRUM_1, DRUM_2, DRUM_3, DRUM_4));
        final DesiredControllerMappings allHigh = new DesiredControllerMappings (Set.of (
            binding (PAD_29, DRUM_1, ControllerMappingValue.MAXIMUM),
            binding (PAD_30, DRUM_2, ControllerMappingValue.MAXIMUM),
            binding (PAD_31, DRUM_3, ControllerMappingValue.MAXIMUM),
            binding (PAD_32, DRUM_4, ControllerMappingValue.MAXIMUM)));
        fixture.host.request (allHigh);

        final DesiredControllerMappings firstLow = new DesiredControllerMappings (Set.of (
            binding (PAD_29, DRUM_1, ControllerMappingValue.MINIMUM),
            binding (PAD_30, DRUM_2, ControllerMappingValue.MAXIMUM),
            binding (PAD_31, DRUM_3, ControllerMappingValue.MAXIMUM),
            binding (PAD_32, DRUM_4, ControllerMappingValue.MAXIMUM)));
        fixture.host.request (firstLow);

        assertEquals (firstLow, fixture.host.activeMappings ());
        assertEquals (5, fixture.bindingCalls);
        assertEquals (1, fixture.semantic.get (DRUM_1).unbinds);
        assertEquals (0, fixture.semantic.get (DRUM_2).unbinds);
        assertEquals (0, fixture.semantic.get (DRUM_3).unbinds);
        assertEquals (0, fixture.semantic.get (DRUM_4).unbinds);
    }


    @Test
    void invalidReplacementLeavesThePreviouslyActiveProjectionUntouched ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29, PAD_30), Set.of (DRUM_1));
        final DesiredControllerMappings original = desired (PAD_29, DRUM_1, ControllerMappingValue.MAXIMUM);
        fixture.host.request (original);
        final DesiredControllerMappings invalid = new DesiredControllerMappings (Set.of (
            binding (PAD_29, DRUM_1, ControllerMappingValue.MAXIMUM),
            binding (PAD_30, DRUM_2, ControllerMappingValue.MAXIMUM)));

        assertThrows (IllegalArgumentException.class, () -> fixture.host.request (invalid));
        assertEquals (original, fixture.host.activeMappings ());
        assertEquals (1, fixture.bindingCalls);
        assertEquals (0, fixture.semantic.get (DRUM_1).unbinds);
    }


    private static DesiredControllerMappings desired (final ControlId physicalControl, final ControllerMappingId mappingId, final ControllerMappingValue value)
    {
        return new DesiredControllerMappings (Set.of (binding (physicalControl, mappingId, value)));
    }


    private static ControllerMappingBinding binding (final ControlId physicalControl, final ControllerMappingId mappingId, final ControllerMappingValue value)
    {
        return new ControllerMappingBinding (physicalControl, mappingId, value);
    }


    private static final class Fixture
    {
        private final Map<ControlId, ButtonHarness> physical = new LinkedHashMap<> ();
        private final Map<ControllerMappingId, AbsoluteHarness> semantic = new LinkedHashMap<> ();
        private final Map<ControlId, Boolean> idle = new LinkedHashMap<> ();
        private final Map<ControllerMappingId, BoundMatcher> bindings = new LinkedHashMap<> ();
        private int bindingCalls;
        private final HardwareMappingActivationHost host;


        private Fixture (final Set<ControlId> physicalControls, final Set<ControllerMappingId> mappingIds)
        {
            final Map<ControlId, IHwButton> physicalButtons = new LinkedHashMap<> ();
            for (final ControlId control: physicalControls)
            {
                final ButtonHarness harness = new ButtonHarness ();
                this.physical.put (control, harness);
                this.idle.put (control, Boolean.TRUE);
                physicalButtons.put (control, harness.button);
            }

            final Map<ControllerMappingId, IHwAbsoluteControl> semanticControls = new LinkedHashMap<> ();
            for (final ControllerMappingId mappingId: mappingIds)
            {
                final AbsoluteHarness harness = new AbsoluteHarness ();
                this.semantic.put (mappingId, harness);
                semanticControls.put (mappingId, harness.control);
            }

            this.host = new HardwareMappingActivationHost (
                physicalButtons,
                semanticControls,
                control -> Boolean.TRUE.equals (this.idle.get (control)),
                (control, physical, value) -> {
                    this.bindingCalls++;
                    this.bindings.put (this.mappingId (control), new BoundMatcher (physical, value));
                },
                control -> {
                    final ControllerMappingId mappingId = this.mappingId (control);
                    this.semantic.get (mappingId).unbinds++;
                    this.bindings.remove (mappingId);
                });
        }


        private ControllerMappingId mappingId (final IHwAbsoluteControl control)
        {
            for (final Map.Entry<ControllerMappingId, AbsoluteHarness> entry: this.semantic.entrySet ())
                if (entry.getValue ().control == control)
                    return entry.getKey ();
            throw new IllegalArgumentException ("unknown semantic mapping control");
        }
    }


    private static final class ButtonHarness
    {
        private int manualEvents;
        private final IHwButton button = (IHwButton) Proxy.newProxyInstance (IHwButton.class.getClassLoader (), new Class<?> [] {IHwButton.class}, (proxy, method, arguments) -> {
            if (method.getName ().equals ("trigger"))
                this.manualEvents++;
            return null;
        });
    }


    private static final class AbsoluteHarness
    {
        private int unbinds;
        private final IHwAbsoluteControl control = (IHwAbsoluteControl) Proxy.newProxyInstance (IHwAbsoluteControl.class.getClassLoader (), new Class<?> [] {IHwAbsoluteControl.class}, (proxy, method, arguments) -> null);
    }


    private record BoundMatcher (ControlId physicalControl, ControllerMappingValue value)
    {}
}
