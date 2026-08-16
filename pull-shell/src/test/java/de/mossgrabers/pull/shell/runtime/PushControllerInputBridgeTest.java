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
import de.mossgrabers.pull.core.api.DesiredInputRoutes;
import de.mossgrabers.pull.core.api.InputRoute;
import de.mossgrabers.pull.core.api.InputRouteMode;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.CoreControls;
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
        assertFalse (PushControllerInputBridge.isCoreOwnedInput (PushControlIds.button ("SCALES"), InputKind.BUTTON));
        for (final var control: CoreControls.DRUM_CONTROL_PADS)
        {
            assertTrue (PushControllerInputBridge.isCoreOwnedInput (control, InputKind.PAD));
            assertFalse (PushControllerInputBridge.isCoreOwnedInput (control, InputKind.POLY_PRESSURE));
        }
    }


    @Test
    void releaseAndImmediateRepressStayOnTheInstalledSingleButtonPath ()
    {
        final Fixture fixture = new Fixture ();

        fixture.pressMappingPad ();
        assertEquals (List.of (InputPhase.BEGIN), fixture.phases ());

        fixture.routes.set (DesiredInputRoutes.empty ());
        fixture.mappings.set (Set.of ());
        fixture.bridge.flush ();
        assertEquals (Set.of (), fixture.bridge.activeHardwareMappings ());

        // Raw release first closes the frozen routed gesture. The deliberately absent Bitwig
        // release matcher cannot duplicate END afterward.
        fixture.rawRelease ();
        fixture.pad.physicalRelease ();
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.END), fixture.phases ());

        // The next gesture belongs to ordinary dispatch and therefore emits no core event.
        fixture.rawPress ();
        fixture.rawRelease ();
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.END), fixture.phases ());

        fixture.routes.set (fixture.exclusiveRoute);
        fixture.mappings.set (Set.of (fixture.control));
        fixture.bridge.flush ();
        fixture.pressMappingPad ();
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.END, InputPhase.BEGIN), fixture.phases ());

        // Reverse the callback order while the desired lane changes twice. The old hardware
        // release is inert; raw release completes exactly once and activates only latest desire.
        fixture.mappings.set (Set.of ());
        fixture.bridge.flush ();
        fixture.mappings.set (Set.of (fixture.control));
        fixture.bridge.flush ();
        fixture.pad.physicalRelease ();
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.END, InputPhase.BEGIN), fixture.phases ());
        fixture.rawRelease ();
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.END, InputPhase.BEGIN, InputPhase.END), fixture.phases ());
        assertEquals (Set.of (fixture.control), fixture.bridge.activeHardwareMappings ());

        fixture.pressMappingPad ();
        fixture.rawRelease ();
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.END, InputPhase.BEGIN, InputPhase.END, InputPhase.BEGIN, InputPhase.END), fixture.phases ());
    }


    private static final class Fixture
    {
        private static final int PAD_NOTE = 64;

        private final ControlId control = CoreControls.DRUM_CONTROL_PADS.get (0);
        private final DesiredInputRoutes exclusiveRoute = new DesiredInputRoutes (Set.of (new InputRoute (this.control, de.mossgrabers.pull.core.api.event.InputKind.PAD, InputRouteMode.EXCLUSIVE)));
        private final AtomicReference<DesiredInputRoutes> routes = new AtomicReference<> (this.exclusiveRoute);
        private final AtomicReference<Set<ControlId>> mappings = new AtomicReference<> (Set.of (this.control));
        private final List<PhysicalInputEvent<ControlId>> events = new ArrayList<> ();
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
            final Map<ControlId, IHwButton> mappingButtons = new LinkedHashMap<> ();
            for (int slot = 0; slot < CoreControls.DRUM_CONTROL_PADS.size (); slot++)
                mappingButtons.put (CoreControls.DRUM_CONTROL_PADS.get (slot), this.surface.getButton (ButtonID.get (ButtonID.PAD1, 28 + slot)));
            this.bridge = new PushControllerInputBridge (
                this.surface,
                valueChanger,
                (ignoredID, ignoredControl, mutation) -> mutation.run (),
                this.routes::get,
                this.mappings::get,
                mappingButtons,
                (ignoredControl, ignoredKind, ignoredAction) -> false,
                this.events::add,
                () -> 1);
        }


        private void pressMappingPad ()
        {
            this.rawPress ();
            this.pad.physicalPress (100);
        }


        private void rawPress ()
        {
            assertTrue (this.bridge.routeMidi (0x90, PAD_NOTE, 100, () -> {}));
        }


        private void rawRelease ()
        {
            assertTrue (this.bridge.routeMidi (0x80, PAD_NOTE, 0, () -> {}));
        }


        private List<InputPhase> phases ()
        {
            return this.events.stream ().map (PhysicalInputEvent::phase).toList ();
        }
    }


    private static final class TestButton extends AbstractHwButton
    {
        private boolean pressMatcher;
        private boolean releaseMatcher;


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
