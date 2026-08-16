// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller.hardware;

import de.mossgrabers.framework.command.core.TriggerCommand;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.framework.utils.ButtonEvent;
import de.mossgrabers.pull.shell.input.InputKind;
import de.mossgrabers.pull.shell.input.InputPhase;
import de.mossgrabers.pull.shell.input.InputRoute;
import de.mossgrabers.pull.shell.input.PhysicalControlRegistry;
import de.mossgrabers.pull.shell.input.PhysicalInputEvent;
import de.mossgrabers.pull.shell.input.PhysicalInputRouter;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests one-time hardware hooks used by stable physical-input decorators.
 */
class CommandReplacementTest
{
    private static final String BUTTON = "push.button.play";


    @Test
    void consumedObservedGestureStillPublishesItsRelease ()
    {
        final FakeButton button = new FakeButton ();
        final List<ButtonEvent> commandEvents = new ArrayList<> ();
        final List<ButtonEvent> handlerEvents = new ArrayList<> ();
        final List<PhysicalInputEvent<String>> coreEvents = new ArrayList<> ();
        final PhysicalInputRouter<String> router = router (new AtomicReference<> (InputRoute.OBSERVE), coreEvents);
        button.bind ( (event, velocity) -> {
            commandEvents.add (event);
            if (event == ButtonEvent.DOWN)
                button.setConsumed ();
        });
        button.addEventHandler (ButtonEvent.DOWN, handlerEvents::add);
        button.addEventHandler (ButtonEvent.UP, handlerEvents::add);
        installArbitrator (button, router);

        button.trigger (ButtonEvent.DOWN, 1.0);
        button.trigger (ButtonEvent.UP, 0.0);

        assertEquals (1, button.hardwareBindingCount);
        assertEquals (List.of (ButtonEvent.DOWN), commandEvents);
        assertEquals (List.of (ButtonEvent.DOWN, ButtonEvent.UP), handlerEvents);
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.END), phases (coreEvents));
        assertEquals (0, router.activeGestureCount ());
        assertFalse (button.isPressed ());
    }


    @Test
    void consumedLegacyGestureClearsItsLeaseBeforeNextExclusiveGesture ()
    {
        final FakeButton button = new FakeButton ();
        final AtomicReference<InputRoute> route = new AtomicReference<> (InputRoute.NONE);
        final AtomicInteger commandCalls = new AtomicInteger ();
        final List<PhysicalInputEvent<String>> coreEvents = new ArrayList<> ();
        final PhysicalInputRouter<String> router = router (route, coreEvents);
        button.bind ( (event, velocity) -> {
            commandCalls.incrementAndGet ();
            if (event == ButtonEvent.DOWN)
                button.setConsumed ();
        });
        installArbitrator (button, router);

        button.trigger (ButtonEvent.DOWN, 1.0);
        button.trigger (ButtonEvent.UP, 0.0);

        assertEquals (1, commandCalls.get ());
        assertEquals (0, router.activeGestureCount ());
        assertTrue (coreEvents.isEmpty ());

        route.set (InputRoute.EXCLUSIVE);
        button.trigger (ButtonEvent.DOWN, 1.0);
        button.trigger (ButtonEvent.UP, 0.0);

        assertEquals (1, commandCalls.get ());
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.END), phases (coreEvents));
        assertEquals (0, router.activeGestureCount ());
    }


    @Test
    void unclaimedGestureRetainsLegacyPressedLongAndReleaseSemantics ()
    {
        final FakeButton button = new FakeButton ();
        final List<ButtonEvent> commandEvents = new ArrayList<> ();
        final List<ButtonEvent> handlerEvents = new ArrayList<> ();
        final List<PhysicalInputEvent<String>> coreEvents = new ArrayList<> ();
        final PhysicalInputRouter<String> router = router (new AtomicReference<> (InputRoute.NONE), coreEvents);
        button.bind ( (event, velocity) -> commandEvents.add (event));
        button.addEventHandler (ButtonEvent.DOWN, handlerEvents::add);
        button.addEventHandler (ButtonEvent.UP, handlerEvents::add);
        installArbitrator (button, router);

        button.trigger (ButtonEvent.DOWN, 0.5);
        assertTrue (button.isPressed ());
        assertFalse (button.isLongPressed ());
        assertEquals (63, button.getPressedVelocity ());

        button.fireLongPress ();
        assertTrue (button.isPressed ());
        assertTrue (button.isLongPressed ());

        button.trigger (ButtonEvent.UP, 0.0);

        assertEquals (List.of (ButtonEvent.DOWN, ButtonEvent.LONG, ButtonEvent.UP), commandEvents);
        assertEquals (List.of (ButtonEvent.DOWN, ButtonEvent.UP), handlerEvents);
        assertTrue (coreEvents.isEmpty ());
        assertFalse (button.isPressed ());
        assertFalse (button.isLongPressed ());
    }


    @Test
    void exclusiveGestureSuppressesAllLegacyStateCommandsAndHandlersButKeepsPhysicalLongRelease ()
    {
        final FakeButton button = new FakeButton ();
        final List<ButtonEvent> commandEvents = new ArrayList<> ();
        final List<ButtonEvent> handlerEvents = new ArrayList<> ();
        final List<PhysicalInputEvent<String>> coreEvents = new ArrayList<> ();
        final PhysicalInputRouter<String> router = router (new AtomicReference<> (InputRoute.EXCLUSIVE), coreEvents);
        button.bind ( (event, velocity) -> commandEvents.add (event));
        button.addEventHandler (ButtonEvent.DOWN, handlerEvents::add);
        button.addEventHandler (ButtonEvent.UP, handlerEvents::add);
        installArbitrator (button, router);

        button.trigger (ButtonEvent.DOWN, 1.0);
        assertFalse (button.isPressed ());
        assertFalse (button.isLongPressed ());
        assertEquals (0, button.getPressedVelocity ());

        button.fireLongPress ();
        assertFalse (button.isPressed ());
        assertFalse (button.isLongPressed ());

        button.trigger (ButtonEvent.UP, 0.0);

        assertTrue (commandEvents.isEmpty ());
        assertTrue (handlerEvents.isEmpty ());
        assertEquals (List.of (InputPhase.BEGIN, InputPhase.LONG, InputPhase.END), phases (coreEvents));
        assertEquals (0, router.activeGestureCount ());
        assertFalse (button.isPressed ());
    }


    @Test
    void buttonArbitratorCanOnlyBeInstalledOnceOnABoundButton ()
    {
        final FakeButton button = new FakeButton ();
        assertThrows (IllegalStateException.class, () -> button.installEventArbitrator ( (event, velocity, legacy) -> legacy.run ()));

        button.bind ( (event, velocity) -> {
            // No-op.
        });
        assertThrows (IllegalArgumentException.class, () -> button.installEventArbitrator (null));
        button.installEventArbitrator ( (event, velocity, legacy) -> legacy.run ());
        assertThrows (IllegalStateException.class, () -> button.installEventArbitrator ( (event, velocity, legacy) -> legacy.run ()));
    }


    @Test
    void replacesTouchBehaviorWithoutBindingHardwareAgain ()
    {
        final FakeContinuousControl control = new FakeContinuousControl ();
        final AtomicInteger originalCalls = new AtomicInteger ();
        final AtomicInteger replacementCalls = new AtomicInteger ();
        control.bindTouch ((event, velocity) -> originalCalls.incrementAndGet (), null, null, 0, 0);

        control.replaceTouchCommand ((event, velocity) -> replacementCalls.incrementAndGet ());
        control.triggerTouch (true);

        assertEquals (1, control.hardwareTouchBindingCount);
        assertEquals (0, originalCalls.get ());
        assertEquals (1, replacementCalls.get ());
        assertThrows (IllegalArgumentException.class, () -> control.replaceTouchCommand (null));
        assertThrows (IllegalStateException.class, () -> new FakeContinuousControl ().replaceTouchCommand ((event, velocity) -> {
            // Not reached.
        }));
    }


    private static IHost host ()
    {
        return (IHost) Proxy.newProxyInstance (
            IHost.class.getClassLoader (),
            new Class<?> []
            {
                IHost.class
            },
            (proxy, method, arguments) -> defaultValue (method.getReturnType ()));
    }


    private static PhysicalInputRouter<String> router (final AtomicReference<InputRoute> route, final List<PhysicalInputEvent<String>> events)
    {
        final PhysicalControlRegistry<String> registry = PhysicalControlRegistry.<String>builder (1)
            .register (BUTTON, InputKind.BUTTON)
            .build ();
        return new PhysicalInputRouter<> (registry, (ignoredControl, ignoredKind) -> route.get (), events::add);
    }


    private static void installArbitrator (final FakeButton button, final PhysicalInputRouter<String> router)
    {
        button.installEventArbitrator ( (event, velocity, legacy) -> router.route (BUTTON, InputKind.BUTTON, phase (event), velocity, legacy));
    }


    private static InputPhase phase (final ButtonEvent event)
    {
        return switch (event)
        {
            case DOWN -> InputPhase.BEGIN;
            case LONG -> InputPhase.LONG;
            case UP -> InputPhase.END;
        };
    }


    private static List<InputPhase> phases (final List<PhysicalInputEvent<String>> events)
    {
        return events.stream ().map (PhysicalInputEvent::phase).toList ();
    }


    private static Object defaultValue (final Class<?> type)
    {
        if (!type.isPrimitive () || type == void.class)
            return null;
        if (type == boolean.class)
            return Boolean.FALSE;
        if (type == char.class)
            return Character.valueOf ('\0');
        if (type == byte.class)
            return Byte.valueOf ((byte) 0);
        if (type == short.class)
            return Short.valueOf ((short) 0);
        if (type == int.class)
            return Integer.valueOf (0);
        if (type == long.class)
            return Long.valueOf (0);
        if (type == float.class)
            return Float.valueOf (0);
        return Double.valueOf (0);
    }


    private static final class FakeButton extends AbstractHwButton
    {
        private final ScheduledHost scheduler;
        private int hardwareBindingCount;


        private FakeButton ()
        {
            this (new ScheduledHost ());
        }


        private FakeButton (final ScheduledHost scheduler)
        {
            super (scheduler.host (), "Button");
            this.scheduler = scheduler;
            this.scheduler.clear ();
        }


        @Override
        public void bind (final TriggerCommand command)
        {
            this.command = command;
            this.hardwareBindingCount++;
        }


        @Override
        public void bind (final IMidiInput input, final BindType type, final int channel, final int control)
        {
            // Not needed by this command-only fake.
        }


        @Override
        public void bind (final IMidiInput input, final BindType type, final int channel, final int control, final int value)
        {
            // Not needed by this command-only fake.
        }


        @Override
        public void unbind ()
        {
            // Not needed by this command-only fake.
        }


        @Override
        public void unbindPress ()
        {
            // Not needed by this command-only fake.
        }


        @Override
        public void rebind ()
        {
            // Not needed by this command-only fake.
        }


        @Override
        public void setBounds (final double x, final double y, final double width, final double height)
        {
            // Not needed by this command-only fake.
        }


        private void fireLongPress ()
        {
            this.scheduler.runNext ();
        }
    }


    private static final class ScheduledHost
    {
        private final List<Runnable> tasks = new ArrayList<> ();


        private IHost host ()
        {
            return (IHost) Proxy.newProxyInstance (
                IHost.class.getClassLoader (),
                new Class<?> []
                {
                    IHost.class
                },
                (proxy, method, arguments) -> {
                    if (method.getName ().equals ("scheduleTask"))
                    {
                        this.tasks.add ((Runnable) arguments[0]);
                        return null;
                    }
                    return defaultValue (method.getReturnType ());
                });
        }


        private void clear ()
        {
            this.tasks.clear ();
        }


        private void runNext ()
        {
            if (this.tasks.isEmpty ())
                throw new AssertionError ("No scheduled button task");
            this.tasks.removeFirst ().run ();
        }
    }


    private static final class FakeContinuousControl extends AbstractHwContinuousControl
    {
        private int hardwareTouchBindingCount;


        private FakeContinuousControl ()
        {
            super (host (), "Continuous");
        }


        @Override
        public void bindTouch (final TriggerCommand command, final IMidiInput input, final BindType type, final int channel, final int control)
        {
            this.touchCommand = command;
            this.hardwareTouchBindingCount++;
        }


        @Override
        public void bind (final IParameter parameter)
        {
            // Not needed by this command-only fake.
        }


        @Override
        public void bind (final IMidiInput input, final BindType type, final int channel, final int control)
        {
            // Not needed by this command-only fake.
        }


        @Override
        public void unbind ()
        {
            // Not needed by this command-only fake.
        }


        @Override
        public void rebind ()
        {
            // Not needed by this command-only fake.
        }


        @Override
        public void handleValue (final double value)
        {
            // Not needed by this command-only fake.
        }


        @Override
        public void setIndexInGroup (final int index)
        {
            // Not needed by this command-only fake.
        }


        @Override
        public void setBounds (final double x, final double y, final double width, final double height)
        {
            // Not needed by this command-only fake.
        }
    }
}
