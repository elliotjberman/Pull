// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller.hardware;

import de.mossgrabers.framework.command.core.ContinuousCommand;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the stable callback installed below changing command and parameter targets.
 */
class ContinuousControlArbitrationTest
{
    private static final String ENCODER = "push.encoder.1";


    @Test
    void laterParameterBindingKeepsOneStableHardwareCallbackAndRemainsObserved ()
    {
        final FakeRelativeControl control = new FakeRelativeControl ();
        final AtomicInteger commandCalls = new AtomicInteger ();
        final AtomicInteger parameterDelta = new AtomicInteger ();
        final List<PhysicalInputEvent<String>> coreEvents = new ArrayList<> ();
        final PhysicalInputRouter<String> router = router (InputKind.RELATIVE, new AtomicReference<> (InputRoute.OBSERVE), coreEvents);

        control.bind (value -> commandCalls.incrementAndGet ());
        assertEquals (1, control.activeHardwareBindings);
        assertEquals (1, control.hardwareTargetChanges);

        control.installValueArbitrator ( (value, legacy) -> router.route (ENCODER, InputKind.RELATIVE, InputPhase.CHANGE, decodeTwosComplement (value), legacy));
        assertEquals (1, control.activeHardwareBindings);
        assertEquals (2, control.hardwareTargetChanges);

        control.bind (parameter (parameterDelta));
        control.emit (65);
        router.flush (ENCODER, InputKind.RELATIVE);

        assertEquals (1, control.activeHardwareBindings);
        assertEquals (2, control.hardwareTargetChanges);
        assertEquals (0, commandCalls.get ());
        assertEquals (-63, parameterDelta.get ());
        assertEquals (List.of (-63L), coreEvents.stream ().map (PhysicalInputEvent::value).toList ());

        control.bind (value -> commandCalls.incrementAndGet ());
        control.emit (1);
        router.flush (ENCODER, InputKind.RELATIVE);

        assertEquals (1, commandCalls.get ());
        assertEquals (1, control.activeHardwareBindings);
        assertEquals (2, control.hardwareTargetChanges);
    }


    @Test
    void exclusiveValueRouteSuppressesBothCommandAndParameterMutations ()
    {
        final FakeRelativeControl control = new FakeRelativeControl ();
        final AtomicInteger commandCalls = new AtomicInteger ();
        final AtomicInteger parameterDelta = new AtomicInteger ();
        final List<PhysicalInputEvent<String>> coreEvents = new ArrayList<> ();
        final PhysicalInputRouter<String> router = router (InputKind.RELATIVE, new AtomicReference<> (InputRoute.EXCLUSIVE), coreEvents);

        control.bind (value -> commandCalls.incrementAndGet ());
        control.installValueArbitrator ( (value, legacy) -> router.route (ENCODER, InputKind.RELATIVE, InputPhase.CHANGE, decodeTwosComplement (value), legacy));
        control.emit (1);
        control.bind (parameter (parameterDelta));
        control.emit (2);
        router.flush ();

        assertEquals (0, commandCalls.get ());
        assertEquals (0, parameterDelta.get ());
        assertEquals (List.of (3L), coreEvents.stream ().map (PhysicalInputEvent::value).toList ());
        assertEquals (1, control.activeHardwareBindings);
        assertEquals (2, control.hardwareTargetChanges);
    }


    @Test
    void touchArbitrationOwnsTheCompleteLegacyLifecycle ()
    {
        final ScheduledHost host = new ScheduledHost ();
        final FakeRelativeControl control = new FakeRelativeControl (host);
        final AtomicReference<InputRoute> route = new AtomicReference<> (InputRoute.OBSERVE);
        final List<ButtonEvent> legacyEvents = new ArrayList<> ();
        final List<PhysicalInputEvent<String>> coreEvents = new ArrayList<> ();
        final PhysicalInputRouter<String> router = router (InputKind.TOUCH, route, coreEvents);

        control.bindTouch ( (event, velocity) -> legacyEvents.add (event), null, null, 0, 0);
        control.installTouchEventArbitrator ( (event, velocity, legacy) -> router.route (ENCODER, InputKind.TOUCH, phase (event), velocity, legacy));

        control.triggerTouch (true);
        assertTrue (control.isTouched ());
        host.runNext ();
        assertTrue (control.isLongTouched ());
        control.triggerTouch (false);
        assertFalse (control.isTouched ());
        assertEquals (List.of (ButtonEvent.DOWN, ButtonEvent.LONG, ButtonEvent.UP), legacyEvents);

        route.set (InputRoute.EXCLUSIVE);
        control.triggerTouch (true);
        assertFalse (control.isTouched ());
        host.runNext ();
        assertFalse (control.isLongTouched ());
        control.triggerTouch (false);
        assertFalse (control.isTouched ());

        assertEquals (List.of (ButtonEvent.DOWN, ButtonEvent.LONG, ButtonEvent.UP), legacyEvents);
        assertEquals (
            List.of (InputPhase.BEGIN, InputPhase.LONG, InputPhase.END, InputPhase.BEGIN, InputPhase.LONG, InputPhase.END),
            coreEvents.stream ().map (PhysicalInputEvent::phase).toList ());
        assertEquals (1, control.hardwareTouchBindingCount);
        assertEquals (0, router.activeGestureCount ());
    }


    @Test
    void staleTouchTimerCannotLongPressANewerGestureEarly ()
    {
        final ScheduledHost host = new ScheduledHost ();
        final FakeRelativeControl control = new FakeRelativeControl (host);
        final List<ButtonEvent> legacyEvents = new ArrayList<> ();

        control.bindTouch ( (event, velocity) -> legacyEvents.add (event), null, null, 0, 0);
        control.triggerTouch (true);
        control.triggerTouch (false);
        control.triggerTouch (true);

        host.runNext ();
        assertEquals (List.of (ButtonEvent.DOWN, ButtonEvent.UP, ButtonEvent.DOWN), legacyEvents);
        assertTrue (control.isTouched ());

        host.runNext ();
        assertEquals (List.of (ButtonEvent.DOWN, ButtonEvent.UP, ButtonEvent.DOWN, ButtonEvent.LONG), legacyEvents);
        assertTrue (control.isLongTouched ());
    }


    @Test
    void arbitratorsAreValidatedAndInstalledOnlyOnce ()
    {
        final FakeRelativeControl control = new FakeRelativeControl ();
        assertThrows (IllegalArgumentException.class, () -> control.installValueArbitrator (null));
        control.installValueArbitrator ( (value, legacy) -> legacy.run ());
        assertThrows (IllegalStateException.class, () -> control.installValueArbitrator ( (value, legacy) -> legacy.run ()));

        assertThrows (IllegalStateException.class, () -> control.installTouchEventArbitrator ( (event, velocity, legacy) -> legacy.run ()));
        control.bindTouch ( (event, velocity) -> {
            // No-op.
        }, null, null, 0, 0);
        assertThrows (IllegalArgumentException.class, () -> control.installTouchEventArbitrator (null));
        control.installTouchEventArbitrator ( (event, velocity, legacy) -> legacy.run ());
        assertThrows (IllegalStateException.class, () -> control.installTouchEventArbitrator ( (event, velocity, legacy) -> legacy.run ()));
    }


    @Test
    void everyParameterRebindAdvancesTheTargetGeneration ()
    {
        final FakeRelativeControl control = new FakeRelativeControl ();
        final IParameter parameter = parameter (new AtomicInteger ());

        control.bind (value -> {
            // No-op command.
        });
        assertEquals (1, control.getBindingGeneration ());
        control.bind (parameter);
        assertEquals (2, control.getBindingGeneration ());
        assertSame (parameter, control.getBoundParameter ());

        // Cursor remote-control pages reuse the same proxy objects. Rebinding the same object must
        // still invalidate a retained actuator generation.
        control.bind (parameter);
        assertEquals (3, control.getBindingGeneration ());
    }


    private static PhysicalInputRouter<String> router (final InputKind kind, final AtomicReference<InputRoute> route, final List<PhysicalInputEvent<String>> events)
    {
        final PhysicalControlRegistry<String> registry = PhysicalControlRegistry.<String>builder (1)
            .register (ENCODER, kind)
            .build ();
        return new PhysicalInputRouter<> (registry, (ignoredControl, ignoredKind) -> route.get (), events::add);
    }


    private static IParameter parameter (final AtomicInteger delta)
    {
        return (IParameter) Proxy.newProxyInstance (
            IParameter.class.getClassLoader (),
            new Class<?> []
            {
                IParameter.class
            },
            (proxy, method, arguments) -> {
                if (method.getName ().equals ("changeValue") && arguments.length == 1)
                    delta.addAndGet (((Integer) arguments[0]).intValue ());
                return defaultValue (method.getReturnType ());
            });
    }


    private static int decodeTwosComplement (final int value)
    {
        return value > 63 ? value - 128 : value;
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


    private static final class ScheduledHost
    {
        private final List<Runnable> tasks = new ArrayList<> ();


        private IHost proxy ()
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


        private void runNext ()
        {
            if (this.tasks.isEmpty ())
                throw new AssertionError ("No scheduled touch task");
            this.tasks.removeFirst ().run ();
        }
    }


    private static final class FakeRelativeControl extends AbstractHwContinuousControl
    {
        private IParameter parameter;
        private int activeHardwareBindings;
        private int hardwareTargetChanges;
        private int hardwareTouchBindingCount;


        private FakeRelativeControl ()
        {
            this (new ScheduledHost ());
        }


        private FakeRelativeControl (final ScheduledHost host)
        {
            super (host.proxy (), "Relative");
        }


        @Override
        public void bind (final ContinuousCommand command)
        {
            this.parameter = null;
            super.bind (command);
            if (!this.hasValueArbitrator ())
                this.replaceHardwareTarget ();
        }


        @Override
        public void bind (final IParameter parameter)
        {
            this.markBindingChanged ();
            this.parameter = parameter;
            if (!this.hasValueArbitrator ())
                this.replaceHardwareTarget ();
        }


        @Override
        public IParameter getBoundParameter ()
        {
            return this.parameter;
        }


        @Override
        public void bindTouch (final TriggerCommand command, final IMidiInput input, final BindType type, final int channel, final int control)
        {
            this.touchCommand = command;
            this.hardwareTouchBindingCount++;
        }


        @Override
        public void bind (final IMidiInput input, final BindType type, final int channel, final int control)
        {
            // Not needed by this callback-only fake.
        }


        @Override
        public void unbind ()
        {
            // Not needed by this callback-only fake.
        }


        @Override
        public void rebind ()
        {
            // Not needed by this callback-only fake.
        }


        @Override
        public void handleValue (final double value)
        {
            this.emit ((int) value);
        }


        @Override
        public void setIndexInGroup (final int index)
        {
            // Not needed by this callback-only fake.
        }


        @Override
        public void setBounds (final double x, final double y, final double width, final double height)
        {
            // Not needed by this callback-only fake.
        }


        @Override
        protected void onValueArbitratorInstalled ()
        {
            this.replaceHardwareTarget ();
        }


        private void emit (final int encodedValue)
        {
            this.arbitrateValue (encodedValue, () -> {
                if (this.parameter == null)
                    this.command.execute (encodedValue);
                else
                    this.parameter.changeValue (decodeTwosComplement (encodedValue));
            });
        }


        private void replaceHardwareTarget ()
        {
            this.activeHardwareBindings = 1;
            this.hardwareTargetChanges++;
        }
    }
}
