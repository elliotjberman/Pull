// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.parameter;

import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.hardware.IHwContinuousControl;
import de.mossgrabers.framework.utils.ButtonEvent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Push trigger, motion ordering, and context-barrier tests.
 */
class PushParameterMutationServiceTest
{
    @Test
    void shiftReleaseFlushesMotionBeforeRequestingRestoreAndReleasingShift ()
    {
        final List<String> order = new ArrayList<> ();
        final FakeTarget target = new FakeTarget ("macro", 10, order);
        final PushParameterMutationService service = service (Map.of (ContinuousID.KNOB1, target));

        service.routeShift (ButtonEvent.DOWN, () -> order.add ("unexpected-flush"), () -> order.add ("shift-down"));
        service.mutate (ContinuousID.KNOB1, control (), () -> target.requestTemporary (20));
        target.advanceHost ();
        service.routeShift (ButtonEvent.UP, () -> order.add ("flush"), () -> order.add ("shift-up"));

        assertEquals (List.of ("shift-down", "temporary", "flush", "shift-up"), order);
        settleAndRequestRestore (service);
        assertEquals (List.of ("shift-down", "temporary", "flush", "shift-up", "restore"), order);

        target.advanceHost ();
        service.tick ();
        service.tick ();

        assertEquals (List.of ("shift-down", "temporary", "flush", "shift-up", "restore"), order);
    }


    @Test
    void contextChangingButtonWaitsForRestoreReadback ()
    {
        final List<String> order = new ArrayList<> ();
        final FakeTarget target = new FakeTarget ("selected-device-page", 33, order);
        final PushParameterMutationService service = service (Map.of (ContinuousID.KNOB1, target));

        service.routeShift (ButtonEvent.DOWN, () -> {
            // No pending motion.
        }, () -> {
            // No-op stable Shift press.
        });
        service.mutate (ContinuousID.KNOB1, control (), () -> target.requestTemporary (80));
        target.advanceHost ();
        service.routeButton (ButtonID.PAGE_RIGHT, () -> order.add ("flush"), () -> order.add ("navigate"));
        service.routeCoreButton (ButtonID.PAGE_RIGHT, () -> order.add ("core"));

        assertEquals (List.of ("temporary", "flush"), order);
        settleAndRequestRestore (service);
        assertEquals (List.of ("temporary", "flush", "restore"), order);
        target.advanceHost ();
        service.tick ();
        service.tick ();
        assertEquals (List.of ("temporary", "flush", "restore", "navigate", "core"), order);
    }


    @Test
    void projectDeviceTempoAndMasterControlsShareOneMutationSeam ()
    {
        final List<String> order = new ArrayList<> ();
        final Map<ContinuousID, FakeTarget> targets = Map.of (
            ContinuousID.KNOB1, new FakeTarget ("project-macro", 1, order),
            ContinuousID.KNOB2, new FakeTarget ("device-remote", 2, order),
            ContinuousID.TEMPO, new FakeTarget ("tempo", 120, order),
            ContinuousID.MASTER_KNOB, new FakeTarget ("master-volume", 70, order));
        final PushParameterMutationService service = service (targets);

        service.routeShift (ButtonEvent.DOWN, () -> {
            // No pending motion.
        }, () -> {
            // No-op stable Shift press.
        });
        int value = 20;
        for (final Map.Entry<ContinuousID, FakeTarget> entry: targets.entrySet ())
        {
            final FakeTarget target = entry.getValue ();
            final int requestedValue = value++;
            service.mutate (entry.getKey (), control (), () -> target.requestTemporary (requestedValue));
            target.advanceHost ();
        }
        service.routeShift (ButtonEvent.UP, () -> {
            // No pending motion.
        }, () -> order.add ("shift-up"));

        settleAndRequestRestore (service);
        for (final FakeTarget target: targets.values ())
            target.advanceHost ();
        service.tick ();
        service.tick ();

        assertTrue (order.containsAll (List.of (
            "restore project-macro",
            "restore device-remote",
            "restore tempo",
            "restore master-volume",
            "shift-up")));
    }


    @Test
    void revisitingAMacroAroundAnotherMacroRestoresTheOriginalBaseline ()
    {
        final List<String> order = new ArrayList<> ();
        final FakeTarget first = new FakeTarget ("first-macro", 10, order);
        final FakeTarget second = new FakeTarget ("second-macro", 20, order);
        final PushParameterMutationService service = service (Map.of (
            ContinuousID.KNOB1, first,
            ContinuousID.KNOB2, second));

        service.routeShift (ButtonEvent.DOWN, () -> {
            // No pending motion.
        }, () -> {
            // No-op stable Shift press.
        });
        service.mutate (ContinuousID.KNOB1, control (), () -> first.requestTemporary (50));
        first.advanceHost ();
        service.mutate (ContinuousID.KNOB2, control (), () -> second.requestTemporary (80));
        second.advanceHost ();
        service.mutate (ContinuousID.KNOB1, control (), () -> first.requestTemporary (90));
        service.routeShift (ButtonEvent.UP, () -> {
            // No pending motion.
        }, () -> {
            // No-op stable Shift release.
        });

        service.tick ();
        first.advanceHost ();
        service.tick ();
        assertEquals (90, first.requested);

        service.tick ();
        service.tick ();
        assertEquals (10, first.requested);
        assertEquals (20, second.requested);
    }


    @Test
    void ordinaryButtonAndPersistentMutationAreNotDelayed ()
    {
        final AtomicInteger calls = new AtomicInteger ();
        final PushParameterMutationService service = service (Map.of ());

        service.routeButton (ButtonID.PLAY, calls::incrementAndGet, calls::incrementAndGet);
        service.routeCoreButton (ButtonID.PLAY, calls::incrementAndGet);
        service.mutate (ContinuousID.PLAY_POSITION, control (), calls::incrementAndGet);

        assertEquals (3, calls.get ());
    }


    private static PushParameterMutationService service (final Map<ContinuousID, FakeTarget> targets)
    {
        return new PushParameterMutationService ( (controlID, ignoredControl) -> targets.get (controlID));
    }


    private static void settleAndRequestRestore (final PushParameterMutationService service)
    {
        service.tick ();
        service.tick ();
    }


    private static IHwContinuousControl control ()
    {
        return (IHwContinuousControl) Proxy.newProxyInstance (
            IHwContinuousControl.class.getClassLoader (),
            new Class<?> []
            {
                IHwContinuousControl.class
            },
            (proxy, method, arguments) -> defaultValue (method.getReturnType ()));
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


    private static final class FakeTarget implements ParameterMutationTarget
    {
        private final ParameterTargetRef reference;
        private final List<String> order;
        private final double baseline;
        private double authoritative;
        private double requested;


        private FakeTarget (final String identity, final double baseline, final List<String> order)
        {
            this.reference = new ParameterTargetRef ("test", identity, 1);
            this.order = order;
            this.baseline = baseline;
            this.authoritative = baseline;
            this.requested = baseline;
        }


        @Override
        public ParameterTargetRef reference ()
        {
            return this.reference;
        }


        @Override
        public double readAuthoritativeValue ()
        {
            return this.authoritative;
        }


        @Override
        public void restore (final double value)
        {
            this.order.add (this.reference.identity ().equals ("macro") || this.reference.identity ().equals ("selected-device-page") ? "restore" : "restore " + this.reference.identity ());
            this.requested = value;
        }


        @Override
        public boolean isCurrent ()
        {
            return true;
        }


        @Override
        public boolean isAt (final double expected)
        {
            return this.authoritative == expected;
        }


        private void requestTemporary (final double value)
        {
            this.order.add ("temporary");
            this.requested = value;
        }


        private void advanceHost ()
        {
            this.authoritative = this.requested;
        }
    }
}
