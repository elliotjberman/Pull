// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ControllerHost;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.command.core.TriggerCommand;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.hardware.AbstractHwButton;
import de.mossgrabers.framework.controller.hardware.BindType;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwLight;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.core.api.ControlId;
import de.mossgrabers.pull.core.api.ControllerMappingBinding;
import de.mossgrabers.pull.core.api.ControllerMappingId;
import de.mossgrabers.pull.core.api.DesiredControllerMappings;
import de.mossgrabers.pull.core.api.DesiredInputRoutes;
import de.mossgrabers.pull.core.api.InputRoute;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.CoreControls;
import de.mossgrabers.pull.core.api.CoreControllerMappings;
import de.mossgrabers.pull.shell.input.InputKind;
import de.mossgrabers.pull.shell.input.InputPhase;
import de.mossgrabers.pull.shell.input.PhysicalInputEvent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Exact permanent-input whitelist tests for core-exclusive Push controls. */
class PushControllerInputBridgeTest
{
    @Test
    void admitsMigratedControllerButtonsToExclusiveRouting ()
    {
        assertTrue (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("PLAY"), InputKind.BUTTON));
        assertTrue (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("RECORD"), InputKind.BUTTON));
        assertTrue (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("SESSION"), InputKind.BUTTON));
        assertTrue (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("NOTE"), InputKind.BUTTON));
        assertTrue (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("LAYOUT"), InputKind.BUTTON));
        assertFalse (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("STOP_CLIP"), InputKind.BUTTON));
        assertTrue (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("MUTE"), InputKind.BUTTON));
        assertTrue (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("SOLO"), InputKind.BUTTON));
        assertFalse (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("SCALES"), InputKind.BUTTON));
        for (final var control: CoreControls.DRUM_CONTROL_PADS)
        {
            assertTrue (PushControllerInputBridge.isCoreOwnedInput (control, InputKind.PAD));
            assertFalse (PushControllerInputBridge.isCoreOwnedInput (control, InputKind.POLY_PRESSURE));
        }
    }


    @Test
    void translatesEveryShellInputPhaseAtOneBoundary ()
    {
        assertEquals (de.mossgrabers.pull.core.api.event.InputPhase.BEGIN, PushControllerInputBridge.toCorePhase (InputPhase.BEGIN));
        assertEquals (de.mossgrabers.pull.core.api.event.InputPhase.UPDATE, PushControllerInputBridge.toCorePhase (InputPhase.CHANGE));
        assertEquals (de.mossgrabers.pull.core.api.event.InputPhase.LONG, PushControllerInputBridge.toCorePhase (InputPhase.LONG));
        assertEquals (de.mossgrabers.pull.core.api.event.InputPhase.END, PushControllerInputBridge.toCorePhase (InputPhase.END));
    }


    @Test
    void browserPadIngressSharesMappedAndOrdinaryLaneTransitions ()
    {
        final Fixture fixture = new Fixture ();

        fixture.pressMappingPad ();
        assertEquals (List.of (InputPhase.BEGIN), fixture.phases ());

        fixture.routes.set (DesiredInputRoutes.empty ());
        fixture.mappings.set (DesiredControllerMappings.empty ());
        fixture.bridge.flush ();
        assertEquals (DesiredControllerMappings.empty (), fixture.bridge.activeControllerMappings ());
        assertFalse (fixture.semanticButtons.get (fixture.mappingId).pressMatcher);

        // Raw release first closes the frozen routed gesture. The deliberately absent Bitwig
        // release matcher cannot duplicate END afterward.
        fixture.debugRelease ();
        fixture.pad.physicalRelease ();
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.END), fixture.phases ());

        // The next gesture belongs to ordinary dispatch and therefore emits no core event.
        fixture.debugPress ();
        fixture.debugRelease ();
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.END), fixture.phases ());

        fixture.routes.set (fixture.exclusiveRoute);
        fixture.mappings.set (fixture.desiredMapping);
        fixture.bridge.flush ();
        fixture.pressMappingPad ();
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.END, InputPhase.BEGIN), fixture.phases ());

        // Reverse the callback order while the desired lane changes twice. The old hardware
        // release is inert; raw release completes exactly once and activates only latest desire.
        fixture.mappings.set (DesiredControllerMappings.empty ());
        fixture.bridge.flush ();
        fixture.mappings.set (fixture.desiredMapping);
        fixture.bridge.flush ();
        fixture.pad.physicalRelease ();
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.END, InputPhase.BEGIN), fixture.phases ());
        fixture.debugRelease ();
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.END, InputPhase.BEGIN, InputPhase.END), fixture.phases ());
        assertEquals (fixture.desiredMapping, fixture.bridge.activeControllerMappings ());

        fixture.pressMappingPad ();
        fixture.debugRelease ();
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.END, InputPhase.BEGIN, InputPhase.END, InputPhase.BEGIN, InputPhase.END), fixture.phases ());
    }


    @Test
    void routesAllFourVirtualPadsAndFreezesEachRawGestureGeneration ()
    {
        final Fixture fixture = new Fixture ();
        fixture.routes.set (fixture.allExclusiveRoutes ());
        fixture.mappings.set (fixture.allMappings ());
        fixture.bridge.flush ();
        fixture.bridge.flush ();

        for (int slot = 0; slot < CoreControls.DRUM_CONTROL_PADS.size (); slot++)
        {
            final TestButton semanticButton = fixture.semanticButtons.get (CoreControllerMappings.DRUM_CONTROL_PADS.get (slot));
            assertTrue (semanticButton.pressMatcher);
            assertFalse (semanticButton.releaseMatcher);
            assertEquals (0, semanticButton.boundChannel);
            assertEquals (Fixture.PAD_NOTE + slot, semanticButton.boundControl);
            assertEquals (1, semanticButton.bindCount);

            fixture.generation.set (10 + slot);
            fixture.rawPress (slot, 80 + slot);
            fixture.generation.set (100 + slot);
            fixture.rawRelease (slot);
        }

        assertEquals (8, fixture.events.size ());
        for (int slot = 0; slot < CoreControls.DRUM_CONTROL_PADS.size (); slot++)
        {
            final PhysicalInputEvent<ControlId> begin = fixture.events.get (slot * 2);
            final PhysicalInputEvent<ControlId> end = fixture.events.get (slot * 2 + 1);
            assertEquals (CoreControls.DRUM_CONTROL_PADS.get (slot), begin.control ());
            assertEquals (CoreControls.DRUM_CONTROL_PADS.get (slot), end.control ());
            assertEquals (InputPhase.BEGIN, begin.phase ());
            assertEquals (InputPhase.END, end.phase ());
            assertEquals (80 + slot, begin.value ());
            assertEquals (0, end.value ());
            assertEquals (10 + slot, begin.ownerGeneration ());
            assertEquals (10 + slot, end.ownerGeneration ());
        }

        final int eventCount = fixture.events.size ();
        assertFalse (fixture.bridge.routeMidi (0x91, Fixture.PAD_NOTE, 100, () -> {}));
        assertFalse (fixture.bridge.routeMidi (0x90, Fixture.GRID_START_NOTE - 1, 100, () -> {}));
        assertFalse (fixture.bridge.routeMidi (0x90, Fixture.GRID_START_NOTE + 64, 100, () -> {}));
        assertTrue (fixture.bridge.routeMidi (0x90, Fixture.PAD_NOTE, 0, () -> {}));
        assertEquals (eventCount, fixture.events.size ());
    }


    @Test
    void detachesEveryPhysicalPadMatcherAndPreservesOrdinaryRawDispatch ()
    {
        final Fixture fixture = new Fixture ();
        fixture.routes.set (DesiredInputRoutes.empty ());
        fixture.mappings.set (DesiredControllerMappings.empty ());
        fixture.bridge.flush ();

        final List<Integer> downs = new ArrayList<> ();
        final List<Integer> ups = new ArrayList<> ();
        for (int index = 0; index < 64; index++)
        {
            final int padNumber = index + 1;
            final TestButton button = fixture.physicalPads.get (PushControlIds.pad (padNumber));
            assertFalse (button.pressMatcher);
            assertFalse (button.releaseMatcher);
            button.addEventHandler (ButtonEvent.DOWN, ignored -> downs.add (padNumber));
            button.addEventHandler (ButtonEvent.UP, ignored -> ups.add (padNumber));

            assertTrue (fixture.bridge.routeMidi (0x90, Fixture.GRID_START_NOTE + index, 63, () -> {}));
            assertEquals (63, button.getPressedVelocity ());
            assertTrue (fixture.bridge.routeMidi (0x80, Fixture.GRID_START_NOTE + index, 0, () -> {}));
        }

        assertEquals (java.util.stream.IntStream.rangeClosed (1, 64).boxed ().toList (), downs);
        assertEquals (downs, ups);
        assertTrue (fixture.events.isEmpty ());
    }


    private static final class Fixture
    {
        private static final int GRID_START_NOTE = 36;
        private static final int PAD_NOTE = 64;

        private final ControlId control = CoreControls.DRUM_CONTROL_PADS.get (0);
        private final ControllerMappingId mappingId = CoreControllerMappings.DRUM_CONTROL_PADS.get (0);
        private final DesiredControllerMappings desiredMapping = new DesiredControllerMappings (Set.of (new ControllerMappingBinding (this.control, this.mappingId)));
        private final DesiredInputRoutes exclusiveRoute = new DesiredInputRoutes (Set.of (new InputRoute (this.control, de.mossgrabers.pull.core.api.event.InputKind.PAD, InputRouteMode.EXCLUSIVE)));
        private final AtomicReference<DesiredInputRoutes> routes = new AtomicReference<> (this.exclusiveRoute);
        private final AtomicReference<DesiredControllerMappings> mappings = new AtomicReference<> (this.desiredMapping);
        private final AtomicLong generation = new AtomicLong (1);
        private final List<PhysicalInputEvent<ControlId>> events = new ArrayList<> ();
        private final Map<ControlId, TestButton> physicalPads = new LinkedHashMap<> ();
        private final Map<ControllerMappingId, TestButton> semanticButtons = new LinkedHashMap<> ();
        private final PushControlSurface surface;
        private final TestButton pad;
        private final PushControllerInputBridge bridge;


        private Fixture ()
        {
            final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
            final IHost [] hostRef = new IHost [1];
            final IHwSurfaceFactory factory = proxy (IHwSurfaceFactory.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "createButton" -> arguments[1] instanceof ButtonID ? new TestButton (hostRef[0], (String) arguments[2]) : relaxedValue (method.getReturnType ());
                case "createLight" -> relaxedProxy (IHwLight.class);
                default -> relaxedValue (method.getReturnType ());
            });
            hostRef[0] = proxy (IHost.class, (proxy, method, arguments) -> "createSurfaceFactory".equals (method.getName ()) ? factory : relaxedValue (method.getReturnType ()));
            final IMidiInput input = relaxedProxy (IMidiInput.class);
            this.surface = new PushControlSurface (
                hostRef[0],
                new PushColorManager (),
                new PushConfiguration (hostRef[0], valueChanger, List.of ()),
                relaxedProxy (IMidiOutput.class),
                input,
                relaxedProxy (ISelectedTrackNoteTarget.class),
                relaxedProxy (ITrack.class),
                () -> false,
                new ReloadableControllerRuntime (relaxedProxy (ControllerHost.class)));
            this.pad = (TestButton) this.surface.getButton (ButtonID.get (ButtonID.PAD1, 28));
            final Map<ControlId, IHwButton> physicalButtons = new LinkedHashMap<> ();
            for (int index = 0; index < 64; index++)
            {
                final ControlId physicalControl = PushControlIds.pad (index + 1);
                final TestButton physicalButton = (TestButton) this.surface.getButton (ButtonID.get (ButtonID.PAD1, index));
                this.physicalPads.put (physicalControl, physicalButton);
                physicalButtons.put (physicalControl, physicalButton);
            }
            physicalButtons.values ().forEach (IHwButton::unbind);
            for (int slot = 0; slot < CoreControllerMappings.DRUM_CONTROL_PADS.size (); slot++)
                this.semanticButtons.put (CoreControllerMappings.DRUM_CONTROL_PADS.get (slot), new TestButton (hostRef[0], "Drum Controller Control " + (slot + 1)));
            this.bridge = new PushControllerInputBridge (
                this.surface,
                valueChanger,
                (ignoredID, ignoredControl, mutation) -> mutation.run (),
                this.routes::get,
                this.mappings::get,
                physicalButtons,
                new LinkedHashMap<> (this.semanticButtons),
                (ignoredControl, ignoredKind, ignoredAction) -> false,
                this.events::add,
                this.generation::get);
        }


        private void pressMappingPad ()
        {
            this.debugPress ();
        }


        private void debugPress ()
        {
            this.bridge.triggerDebugPad (this.control, InputPhase.BEGIN, 100);
        }


        private void debugRelease ()
        {
            this.bridge.triggerDebugPad (this.control, InputPhase.END, 0);
        }


        private void rawPress ()
        {
            this.rawPress (0, 100);
        }


        private void rawRelease ()
        {
            this.rawRelease (0);
        }


        private void rawPress (final int slot, final int velocity)
        {
            assertTrue (this.bridge.routeMidi (0x90, PAD_NOTE + slot, velocity, () -> {}));
        }


        private void rawRelease (final int slot)
        {
            assertTrue (this.bridge.routeMidi (0x80, PAD_NOTE + slot, 0, () -> {}));
        }


        private List<InputPhase> phases ()
        {
            return this.events.stream ().map (PhysicalInputEvent::phase).toList ();
        }


        private DesiredControllerMappings allMappings ()
        {
            final Set<ControllerMappingBinding> bindings = new java.util.LinkedHashSet<> ();
            for (int slot = 0; slot < CoreControls.DRUM_CONTROL_PADS.size (); slot++)
                bindings.add (new ControllerMappingBinding (CoreControls.DRUM_CONTROL_PADS.get (slot), CoreControllerMappings.DRUM_CONTROL_PADS.get (slot)));
            return new DesiredControllerMappings (bindings);
        }


        private DesiredInputRoutes allExclusiveRoutes ()
        {
            final Set<InputRoute> inputRoutes = new java.util.LinkedHashSet<> ();
            for (final ControlId mappingPad: CoreControls.DRUM_CONTROL_PADS)
                inputRoutes.add (new InputRoute (mappingPad, de.mossgrabers.pull.core.api.event.InputKind.PAD, InputRouteMode.EXCLUSIVE));
            return new DesiredInputRoutes (inputRoutes);
        }
    }


    private static final class TestButton extends AbstractHwButton
    {
        private boolean pressMatcher;
        private boolean releaseMatcher;
        private int boundChannel = -1;
        private int boundControl = -1;
        private int bindCount;


        private TestButton (final IHost host, final String label)
        {
            super (host, label);
        }


        @Override
        public void bind (final TriggerCommand command)
        {
            this.command = command;
        }


        @Override
        public void bind (final IMidiInput input, final BindType type, final int channel, final int control)
        {
            this.input = input;
            this.type = type;
            this.channel = channel;
            this.boundChannel = channel;
            this.boundControl = control;
            this.bindCount++;
            this.pressMatcher = true;
            this.releaseMatcher = true;
        }


        @Override
        public void bind (final IMidiInput input, final BindType type, final int channel, final int control, final int value)
        {
            this.bind (input, type, channel, control);
        }


        @Override
        public void unbind ()
        {
            this.pressMatcher = false;
            this.releaseMatcher = false;
        }


        @Override
        public void unbindPress ()
        {
            this.pressMatcher = false;
        }


        @Override
        public void unbindRelease ()
        {
            this.releaseMatcher = false;
        }


        @Override
        public void rebind ()
        {
            this.pressMatcher = true;
            this.releaseMatcher = true;
        }


        @Override
        public void setBounds (final double x, final double y, final double width, final double height)
        {
            // Not relevant to the installed input-path test.
        }


        private void physicalPress (final int velocity)
        {
            if (this.pressMatcher)
                this.trigger (ButtonEvent.DOWN, Math.nextUp (velocity / 127.0));
        }


        private void physicalRelease ()
        {
            if (this.releaseMatcher)
                this.trigger (ButtonEvent.UP, 0);
        }
    }


    private static <T> T proxy (final Class<T> type, final java.lang.reflect.InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] {type}, handler));
    }


    private static <T> T relaxedProxy (final Class<T> type)
    {
        return proxy (type, (proxy, method, arguments) -> relaxedValue (method.getReturnType ()));
    }


    private static Object relaxedValue (final Class<?> type)
    {
        if (type.isInterface ())
            return relaxedProxy (type);
        if (!type.isPrimitive () || void.class.equals (type))
            return null;
        if (boolean.class.equals (type))
            return Boolean.FALSE;
        if (char.class.equals (type))
            return Character.valueOf ('\0');
        if (byte.class.equals (type))
            return Byte.valueOf ((byte) 0);
        if (short.class.equals (type))
            return Short.valueOf ((short) 0);
        if (int.class.equals (type))
            return Integer.valueOf (0);
        if (long.class.equals (type))
            return Long.valueOf (0);
        if (float.class.equals (type))
            return Float.valueOf (0);
        return Double.valueOf (0);
    }
}
