// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.hardware;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.HardwareSurface;
import com.bitwig.extension.controller.api.InternalHardwareLightState;
import com.bitwig.extension.controller.api.MultiStateHardwareLight;
import com.bitwig.extension.controller.api.ObjectHardwareProperty;

import de.mossgrabers.bitwig.framework.daw.HostImpl;
import de.mossgrabers.framework.configuration.Configuration;
import de.mossgrabers.framework.controller.AbstractControlSurface;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.OutputID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.hardware.IHwLight;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;


/** Exercises surface refresh through the real factory, lights, and continuous-output cache. */
class ControlSurfaceLightRefreshTest
{
    @Test
    void resendsUnchangedButtonAndStandaloneLightsOnTheNextHardwareUpdate ()
    {
        final Fixture fixture = new Fixture ();
        final List<Integer> buttonOutput = new ArrayList<> ();
        final List<ColorEx> standaloneOutput = new ArrayList<> ();
        fixture.surface.createLight (null, () -> 7, buttonOutput::add, ignored -> ColorEx.RED,
            fixture.surface.createButton (ButtonID.PLAY, "Play"));
        fixture.surface.createLight (OutputID.LED1, () -> ColorEx.BLUE, standaloneOutput::add);

        fixture.surface.updateHardware ();
        fixture.surface.updateHardware ();
        assertEquals (List.of (7), buttonOutput);
        assertEquals (List.of (ColorEx.BLUE), standaloneOutput);

        fixture.surface.forceFlush ();

        assertEquals (List.of (7), buttonOutput, "Invalidation must not transmit a synthetic off state");
        assertEquals (List.of (ColorEx.BLUE), standaloneOutput);

        fixture.surface.updateHardware ();
        fixture.surface.updateHardware ();
        assertEquals (List.of (7, 7), buttonOutput);
        assertEquals (List.of (ColorEx.BLUE, ColorEx.BLUE), standaloneOutput);
    }


    @Test
    void samplesTheLatestSupplierStateAfterRefreshWasRequested ()
    {
        final Fixture fixture = new Fixture ();
        final AtomicInteger desired = new AtomicInteger (7);
        final List<Integer> output = new ArrayList<> ();
        fixture.surface.createLight (OutputID.LED1, desired::get, output::add, ignored -> ColorEx.RED, null);
        fixture.surface.updateHardware ();

        fixture.surface.forceFlush ();
        desired.set (19);
        assertEquals (List.of (7), output);

        fixture.surface.updateHardware ();
        assertEquals (List.of (7, 19), output);
    }


    @Test
    void turningOffAfterARefreshCannotBeUndoneByDelayedWork ()
    {
        final Fixture fixture = new Fixture ();
        final List<Integer> output = new ArrayList<> ();
        final IHwLight light = fixture.surface.createLight (OutputID.LED1, () -> 7, output::add, ignored -> ColorEx.RED, null);
        fixture.surface.updateHardware ();

        fixture.surface.forceFlush ();
        light.turnOff ();
        fixture.surface.updateHardware ();
        assertEquals (List.of (7, 0), output);

        // Any work queued by refresh must not restore a supplier after the later turn-off.
        List.copyOf (fixture.scheduled).forEach (Runnable::run);
        fixture.surface.updateHardware ();
        assertEquals (List.of (7, 0), output);
    }


    @Test
    void stillResendsUnchangedContinuousOutput ()
    {
        final Fixture fixture = new Fixture ();
        final List<Integer> output = new ArrayList<> ();
        fixture.surface.createAbsoluteKnob (ContinuousID.KNOB1, "Knob").addOutput (() -> 42, output::add);
        fixture.surface.updateHardware ();
        fixture.surface.updateHardware ();
        assertEquals (List.of (42), output);

        fixture.surface.forceFlush ();
        assertEquals (List.of (42), output);

        fixture.surface.updateHardware ();
        fixture.surface.updateHardware ();
        assertEquals (List.of (42, 42), output);
    }


    private static final class TestSurface extends AbstractControlSurface<Configuration>
    {
        private TestSurface (final HostImpl host)
        {
            super (0, host, null, new ColorManager (), null, null, null, null, 100, 100);
        }


        private void updateHardware ()
        {
            this.flushHardware ();
        }
    }


    /** Bitwig output invalidation is a request; only a later update samples and transmits. */
    private static final class Fixture
    {
        private final List<LightProperty> lights = new ArrayList<> ();
        private final List<Runnable> scheduled = new ArrayList<> ();
        private final TestSurface surface;
        private boolean invalidated;


        private Fixture ()
        {
            final HardwareSurface hardware = proxy (HardwareSurface.class, (ignored, method, arguments) -> {
                switch (method.getName ())
                {
                    case "createMultiStateHardwareLight":
                        final LightProperty light = new LightProperty ();
                        this.lights.add (light);
                        return proxy (MultiStateHardwareLight.class, (ignoredLight, lightMethod, lightArguments) ->
                            lightMethod.getName ().equals ("state") ? light.property : defaultValue (lightMethod.getReturnType ()));
                    case "invalidateHardwareOutputState":
                        this.invalidated = true;
                        return null;
                    case "updateHardware":
                        this.lights.forEach (lightProperty -> lightProperty.update (this.invalidated));
                        this.invalidated = false;
                        return null;
                    default:
                        return defaultValue (method.getReturnType ());
                }
            });
            final ControllerHost host = proxy (ControllerHost.class, (ignored, method, arguments) -> {
                if (method.getName ().equals ("createHardwareSurface"))
                    return hardware;
                if (method.getName ().equals ("scheduleTask"))
                    this.scheduled.add ((Runnable) arguments[0]);
                return defaultValue (method.getReturnType ());
            });
            this.surface = new TestSurface (new HostImpl (host));
            // The factory starts unrelated button-timeout calibration during construction.
            this.scheduled.clear ();
        }
    }


    private static final class LightProperty
    {
        private Supplier<InternalHardwareLightState> supplier;
        private Consumer<InternalHardwareLightState> updater;
        private InternalHardwareLightState lastSent;
        private boolean sent;
        private final ObjectHardwareProperty<InternalHardwareLightState> property;


        @SuppressWarnings("unchecked")
        private LightProperty ()
        {
            this.property = proxy (ObjectHardwareProperty.class, (ignored, method, arguments) -> {
                if (method.getName ().equals ("setValueSupplier"))
                    this.supplier = (Supplier<InternalHardwareLightState>) arguments[0];
                else if (method.getName ().equals ("onUpdateHardware"))
                    this.updater = (Consumer<InternalHardwareLightState>) arguments[0];
                return defaultValue (method.getReturnType ());
            });
        }


        private void update (final boolean invalidated)
        {
            final InternalHardwareLightState value = this.supplier.get ();
            if (invalidated || !this.sent || !Objects.equals (this.lastSent, value))
            {
                this.updater.accept (value);
                this.lastSent = value;
                this.sent = true;
            }
        }
    }


    private static Object defaultValue (final Class<?> type)
    {
        if (type == void.class)
            return null;
        if (type.isPrimitive ())
            return Array.get (Array.newInstance (type, 1), 0);
        if (type.isInterface ())
            return proxy (type, (ignored, method, arguments) -> defaultValue (method.getReturnType ()));
        return null;
    }


    @SuppressWarnings("unchecked")
    private static <T> T proxy (final Class<T> type, final InvocationHandler handler)
    {
        return (T) Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] {type}, handler);
    }
}
