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
import de.mossgrabers.framework.controller.OutputID;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.color.ColorManager;
import de.mossgrabers.framework.controller.hardware.IHwLight;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;


/** Exercises refresh through the real surface, factory, and light. */
class ControlSurfaceLightRefreshTest
{
    private Supplier<InternalHardwareLightState> supplier;
    private Consumer<InternalHardwareLightState> updater;
    private InternalHardwareLightState lastSent;
    private boolean invalidated;
    private final List<Runnable> scheduled = new ArrayList<> ();


    @Test
    void refreshResendsCurrentStateAndPreservesLaterTurnOff ()
    {
        final AbstractControlSurface<Configuration> surface = this.createSurface ();
        final AtomicReference<ColorEx> desired = new AtomicReference<> (ColorEx.RED);
        final List<ColorEx> output = new ArrayList<> ();
        final IHwLight light = surface.createLight (OutputID.LED1, desired::get, output::add);
        final Runnable update = surface.getSurfaceFactory ()::flush;

        update.run ();
        update.run ();
        assertEquals (List.of (ColorEx.RED), output, "Unchanged output is normally suppressed");

        surface.forceFlush ();
        assertEquals (List.of (ColorEx.RED), output, "Refresh waits for the next hardware update");
        update.run ();
        update.run ();
        assertEquals (List.of (ColorEx.RED, ColorEx.RED), output);

        surface.forceFlush ();
        desired.set (ColorEx.BLUE);
        update.run ();
        assertEquals (List.of (ColorEx.RED, ColorEx.RED, ColorEx.BLUE), output);

        surface.forceFlush ();
        light.turnOff ();
        update.run ();
        assertEquals (List.of (ColorEx.RED, ColorEx.RED, ColorEx.BLUE, ColorEx.BLACK), output);
        List.copyOf (this.scheduled).forEach (Runnable::run);
        update.run ();
        assertEquals (List.of (ColorEx.RED, ColorEx.RED, ColorEx.BLUE, ColorEx.BLACK), output,
            "Queued refresh work must not undo a later turn-off");
    }


    @SuppressWarnings("unchecked")
    private AbstractControlSurface<Configuration> createSurface ()
    {
        final ObjectHardwareProperty<InternalHardwareLightState> property = proxy (ObjectHardwareProperty.class, (ignored, method, args) -> {
            if (method.getName ().equals ("setValueSupplier"))
                this.supplier = (Supplier<InternalHardwareLightState>) args[0];
            else if (method.getName ().equals ("onUpdateHardware"))
                this.updater = (Consumer<InternalHardwareLightState>) args[0];
            return null;
        });
        final MultiStateHardwareLight light = proxy (MultiStateHardwareLight.class, (ignored, method, args) ->
            method.getName ().equals ("state") ? property : null);
        final HardwareSurface hardware = proxy (HardwareSurface.class, (ignored, method, args) -> {
            switch (method.getName ())
            {
                case "createMultiStateHardwareLight":
                    return light;
                case "invalidateHardwareOutputState":
                    this.invalidated = true;
                    break;
                case "updateHardware":
                    this.updateHardware ();
                    break;
                default:
                    break;
            }
            return null;
        });
        final ControllerHost host = proxy (ControllerHost.class, (ignored, method, args) -> {
            if (method.getName ().equals ("createHardwareSurface"))
                return hardware;
            if (method.getName ().equals ("scheduleTask"))
                this.scheduled.add ((Runnable) args[0]);
            return null;
        });
        final AbstractControlSurface<Configuration> surface = new AbstractControlSurface<> (0, new HostImpl (host), null, new ColorManager (), null, null, null, null, 100, 100) {};
        // Discard unrelated button-timeout calibration scheduled by factory construction.
        this.scheduled.clear ();
        return surface;
    }


    /** Bitwig samples suppliers and transmits only on a later hardware update. */
    private void updateHardware ()
    {
        final InternalHardwareLightState value = this.supplier.get ();
        if (this.invalidated || !Objects.equals (this.lastSent, value))
            this.updater.accept (value);
        this.lastSent = value;
        this.invalidated = false;
    }


    private static <T> T proxy (final Class<T> type, final InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] {type}, handler));
    }
}
