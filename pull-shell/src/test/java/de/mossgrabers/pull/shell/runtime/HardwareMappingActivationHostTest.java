// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerMappingBinding;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


/** Lifecycle tests for semantic mapping projection and raw ordinary dispatch. */
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
    void switchesIdleControlBetweenSemanticMatcherAndRawDispatch ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29), Set.of (DRUM_1));

        fixture.host.request (desired (PAD_29, DRUM_1));
        assertEquals (desired (PAD_29, DRUM_1), fixture.host.activeMappings ());
        assertEquals (PAD_29, fixture.bindings.get (DRUM_1));
        assertEquals (1, fixture.semantic.get (DRUM_1).unbindReleases);
        assertEquals (HardwareMappingActivationHost.RawDisposition.MAPPED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.DOWN, 0.5));

        fixture.host.request (DesiredControllerMappings.empty ());
        assertEquals (1, fixture.semantic.get (DRUM_1).unbindPresses);
        assertEquals (DesiredControllerMappings.empty (), fixture.host.activeMappings ());
        assertEquals (HardwareMappingActivationHost.RawDisposition.DISPATCHED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.DOWN, 0.5));
        fixture.idle.put (PAD_29, Boolean.FALSE);
        assertEquals (HardwareMappingActivationHost.RawDisposition.DISPATCHED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.UP, 0));
        assertEquals (2, fixture.physical.get (PAD_29).manualEvents);

        fixture.idle.put (PAD_29, Boolean.TRUE);
        fixture.host.request (desired (PAD_29, DRUM_1));
        assertEquals (2, fixture.semantic.get (DRUM_1).unbindReleases);
        assertEquals (desired (PAD_29, DRUM_1), fixture.host.activeMappings ());
    }


    @Test
    void replayingUnchangedProjectionDoesNotChurnMatcher ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29), Set.of (DRUM_1));
        final DesiredControllerMappings projection = desired (PAD_29, DRUM_1);

        fixture.host.request (projection);
        fixture.host.request (projection);
        fixture.host.request (new DesiredControllerMappings (Set.of (new ControllerMappingBinding (PAD_29, DRUM_1))));

        assertEquals (1, fixture.bindingCalls);
        assertEquals (1, fixture.semantic.get (DRUM_1).unbindReleases);
        assertEquals (0, fixture.semantic.get (DRUM_1).unbindPresses);
        assertEquals (projection, fixture.host.activeMappings ());
    }


    @Test
    void mappedReleaseWithoutAnAcceptedPressCannotLeakIntoOrdinaryDispatch ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29), Set.of (DRUM_1));
        fixture.host.request (desired (PAD_29, DRUM_1));

        assertEquals (HardwareMappingActivationHost.RawDisposition.SUPPRESSED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.UP, 0));
        assertEquals (0, fixture.physical.get (PAD_29).manualEvents);
        assertEquals (desired (PAD_29, DRUM_1), fixture.host.activeMappings ());
    }


    @Test
    void switchesOnePhysicalControlBetweenIndependentSemanticEndpoints ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29), Set.of (DRUM_1, DRUM_2));

        fixture.host.request (desired (PAD_29, DRUM_1));
        fixture.host.request (desired (PAD_29, DRUM_2));

        assertEquals (desired (PAD_29, DRUM_2), fixture.host.activeMappings ());
        assertEquals (1, fixture.semantic.get (DRUM_1).unbindPresses);
        assertEquals (1, fixture.semantic.get (DRUM_1).unbindReleases);
        assertEquals (0, fixture.semantic.get (DRUM_2).unbindPresses);
        assertEquals (1, fixture.semantic.get (DRUM_2).unbindReleases);
        assertEquals (PAD_29, fixture.bindings.get (DRUM_1));
        assertEquals (PAD_29, fixture.bindings.get (DRUM_2));
    }


    @Test
    void retiringMappedGestureSuppressesRawInputUntilItsExactRelease ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29), Set.of (DRUM_1));
        fixture.host.request (desired (PAD_29, DRUM_1));
        fixture.idle.put (PAD_29, Boolean.FALSE);

        fixture.host.request (DesiredControllerMappings.empty ());
        assertEquals (1, fixture.semantic.get (DRUM_1).unbindPresses);
        assertEquals (HardwareMappingActivationHost.RawDisposition.SUPPRESSED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.DOWN, 0.5));
        assertEquals (HardwareMappingActivationHost.RawDisposition.MAPPED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.UP, 0));
        assertEquals (DesiredControllerMappings.empty (), fixture.host.activeMappings ());

        fixture.idle.put (PAD_29, Boolean.TRUE);
        fixture.host.request (DesiredControllerMappings.empty ());
        assertEquals (HardwareMappingActivationHost.RawDisposition.DISPATCHED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.DOWN, 0.5));
    }


    @Test
    void rawReleaseFinishesOldDispatchBeforeLatestDesiredMatcherActivates ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29), Set.of (DRUM_1));
        assertEquals (HardwareMappingActivationHost.RawDisposition.DISPATCHED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.DOWN, 0.5));
        fixture.idle.put (PAD_29, Boolean.FALSE);

        fixture.host.request (desired (PAD_29, DRUM_1));
        fixture.host.request (DesiredControllerMappings.empty ());
        fixture.host.request (desired (PAD_29, DRUM_1));
        assertEquals (HardwareMappingActivationHost.RawDisposition.SUPPRESSED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.DOWN, 0.5));
        assertEquals (DesiredControllerMappings.empty (), fixture.host.activeMappings ());

        assertEquals (HardwareMappingActivationHost.RawDisposition.DISPATCHED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.UP, 0));
        fixture.idle.put (PAD_29, Boolean.TRUE);
        fixture.host.request (desired (PAD_29, DRUM_1));
        assertEquals (2, fixture.physical.get (PAD_29).manualEvents);
        assertEquals (desired (PAD_29, DRUM_1), fixture.host.activeMappings ());
    }


    @Test
    void semanticEndpointCannotMoveUntilItsOldPhysicalGestureIsIdle ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29, PAD_30), Set.of (DRUM_1));
        fixture.host.request (desired (PAD_29, DRUM_1));
        fixture.idle.put (PAD_29, Boolean.FALSE);

        fixture.host.request (desired (PAD_30, DRUM_1));
        assertEquals (DesiredControllerMappings.empty (), fixture.host.activeMappings ());
        assertEquals (HardwareMappingActivationHost.RawDisposition.SUPPRESSED, fixture.host.dispatchRaw (PAD_30, ButtonEvent.DOWN, 1));

        fixture.idle.put (PAD_29, Boolean.TRUE);
        fixture.host.request (desired (PAD_30, DRUM_1));
        assertEquals (desired (PAD_30, DRUM_1), fixture.host.activeMappings ());
        assertEquals (PAD_30, fixture.bindings.get (DRUM_1));
    }


    @Test
    void fourLanesActivateIndependentlyAndOneHeldRetirementDoesNotChurnTheOthers ()
    {
        final Set<ControlId> physicalControls = Set.of (PAD_29, PAD_30, PAD_31, PAD_32);
        final Set<ControllerMappingId> mappingIds = Set.of (DRUM_1, DRUM_2, DRUM_3, DRUM_4);
        final Fixture fixture = new Fixture (physicalControls, mappingIds);
        final DesiredControllerMappings all = new DesiredControllerMappings (Set.of (
            new ControllerMappingBinding (PAD_29, DRUM_1),
            new ControllerMappingBinding (PAD_30, DRUM_2),
            new ControllerMappingBinding (PAD_31, DRUM_3),
            new ControllerMappingBinding (PAD_32, DRUM_4)));
        final DesiredControllerMappings remaining = new DesiredControllerMappings (Set.of (
            new ControllerMappingBinding (PAD_30, DRUM_2),
            new ControllerMappingBinding (PAD_31, DRUM_3),
            new ControllerMappingBinding (PAD_32, DRUM_4)));

        fixture.host.request (all);
        assertEquals (all, fixture.host.activeMappings ());
        assertEquals (4, fixture.bindingCalls);
        assertTrueMatchers (fixture, DRUM_1, DRUM_2, DRUM_3, DRUM_4);

        fixture.idle.put (PAD_29, Boolean.FALSE);
        fixture.host.request (remaining);
        assertEquals (remaining, fixture.host.activeMappings ());
        assertEquals (4, fixture.bindingCalls);
        assertEquals (false, fixture.semantic.get (DRUM_1).pressMatcher);
        assertTrueMatchers (fixture, DRUM_2, DRUM_3, DRUM_4);
        assertEquals (HardwareMappingActivationHost.RawDisposition.SUPPRESSED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.DOWN, 1));
        assertEquals (HardwareMappingActivationHost.RawDisposition.MAPPED, fixture.host.dispatchRaw (PAD_29, ButtonEvent.UP, 0));
        assertEquals (HardwareMappingActivationHost.RawDisposition.MAPPED, fixture.host.dispatchRaw (PAD_30, ButtonEvent.DOWN, 1));

        fixture.idle.put (PAD_29, Boolean.TRUE);
        fixture.host.request (remaining);
        assertEquals (4, fixture.bindingCalls);
        assertTrueMatchers (fixture, DRUM_2, DRUM_3, DRUM_4);
    }


    @Test
    void swapsTwoInstalledSemanticEndpointsWithoutLosingEitherIdentity ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29, PAD_30), Set.of (DRUM_1, DRUM_2));
        fixture.host.request (new DesiredControllerMappings (Set.of (
            new ControllerMappingBinding (PAD_29, DRUM_1),
            new ControllerMappingBinding (PAD_30, DRUM_2))));

        final DesiredControllerMappings swapped = new DesiredControllerMappings (Set.of (
            new ControllerMappingBinding (PAD_29, DRUM_2),
            new ControllerMappingBinding (PAD_30, DRUM_1)));
        fixture.host.request (swapped);

        assertEquals (swapped, fixture.host.activeMappings ());
        assertEquals (4, fixture.bindingCalls);
        assertEquals (PAD_30, fixture.bindings.get (DRUM_1));
        assertEquals (PAD_29, fixture.bindings.get (DRUM_2));
        assertEquals (1, fixture.semantic.get (DRUM_1).unbindPresses);
        assertEquals (1, fixture.semantic.get (DRUM_2).unbindPresses);
        assertTrueMatchers (fixture, DRUM_1, DRUM_2);
    }


    @Test
    void invalidReplacementLeavesThePreviouslyActiveProjectionUntouched ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29, PAD_30), Set.of (DRUM_1));
        final DesiredControllerMappings original = desired (PAD_29, DRUM_1);
        fixture.host.request (original);
        final DesiredControllerMappings invalid = new DesiredControllerMappings (Set.of (
            new ControllerMappingBinding (PAD_29, DRUM_1),
            new ControllerMappingBinding (PAD_30, DRUM_2)));

        assertThrows (IllegalArgumentException.class, () -> fixture.host.request (invalid));
        assertEquals (original, fixture.host.activeMappings ());
        assertEquals (1, fixture.bindingCalls);
        assertEquals (0, fixture.semantic.get (DRUM_1).unbindPresses);
        assertTrueMatchers (fixture, DRUM_1);
    }


    @Test
    void rejectsUnknownPhysicalControlsAndSemanticEndpoints ()
    {
        final Fixture fixture = new Fixture (Set.of (PAD_29), Set.of (DRUM_1));

        assertThrows (IllegalArgumentException.class, () -> fixture.host.request (desired (PAD_30, DRUM_1)));
        assertThrows (IllegalArgumentException.class, () -> fixture.host.request (desired (PAD_29, DRUM_2)));
        assertThrows (IllegalArgumentException.class, () -> fixture.host.dispatchRaw (PAD_30, ButtonEvent.DOWN, 1));
    }


    private static DesiredControllerMappings desired (final ControlId physicalControl, final ControllerMappingId mappingId)
    {
        return new DesiredControllerMappings (Set.of (new ControllerMappingBinding (physicalControl, mappingId)));
    }


    private static void assertTrueMatchers (final Fixture fixture, final ControllerMappingId... mappingIds)
    {
        for (final ControllerMappingId mappingId: mappingIds)
            assertEquals (true, fixture.semantic.get (mappingId).pressMatcher);
    }


    private static final class Fixture
    {
        private final Map<ControlId, ButtonHarness> physical = new LinkedHashMap<> ();
        private final Map<ControllerMappingId, ButtonHarness> semantic = new LinkedHashMap<> ();
        private final Map<ControlId, Boolean> idle = new LinkedHashMap<> ();
        private final Map<ControllerMappingId, ControlId> bindings = new LinkedHashMap<> ();
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
                physicalButtons.put (control, harness.button ());
            }

            final Map<ControllerMappingId, IHwButton> semanticButtons = new LinkedHashMap<> ();
            for (final ControllerMappingId mappingId: mappingIds)
            {
                final ButtonHarness harness = new ButtonHarness ();
                this.semantic.put (mappingId, harness);
                semanticButtons.put (mappingId, harness.button ());
            }

            this.host = new HardwareMappingActivationHost (
                physicalButtons,
                semanticButtons,
                control -> Boolean.TRUE.equals (this.idle.get (control)),
                (button, control) -> {
                    this.bindingCalls++;
                    final ControllerMappingId mappingId = this.mappingId (button);
                    this.bindings.put (mappingId, control);
                    this.semantic.get (mappingId).pressMatcher = true;
                });
        }


        private ControllerMappingId mappingId (final IHwButton button)
        {
            for (final Map.Entry<ControllerMappingId, ButtonHarness> entry: this.semantic.entrySet ())
            {
                if (entry.getValue ().button () == button)
                    return entry.getKey ();
            }
            throw new IllegalArgumentException ("unknown semantic mapping button");
        }
    }


    private static final class ButtonHarness
    {
        private int unbindPresses;
        private int unbindReleases;
        private int manualEvents;
        private final IHwButton button = (IHwButton) Proxy.newProxyInstance (IHwButton.class.getClassLoader (), new Class<?> [] {IHwButton.class}, (proxy, method, arguments) -> {
            switch (method.getName ())
            {
                case "unbindPress" -> this.unbindPresses++;
                case "unbindRelease" -> this.unbindReleases++;
                case "trigger" -> this.manualEvents++;
                default -> { }
            }
            if (method.getName ().equals ("unbindPress"))
                this.pressMatcher = false;
            return null;
        });

        private boolean pressMatcher;


        private IHwButton button ()
        {
            return this.button;
        }
    }
}
