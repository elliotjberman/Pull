// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.hardware;

import com.bitwig.extension.api.Color;
import com.bitwig.extension.controller.api.InternalHardwareLightState;
import com.bitwig.extension.controller.api.MultiStateHardwareLight;
import com.bitwig.extension.controller.api.ObjectHardwareProperty;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.daw.IHost;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;


/** Direct API-21 coverage for Bitwig manual-mapping light feedback installation. */
class HwLightImplTest
{
    @Test
    void observesMappedColorsWithoutBypassingTheNormalLightSupplier ()
    {
        final AtomicReference<Function<Color, InternalHardwareLightState>> colorToState = new AtomicReference<> ();
        final ObjectHardwareProperty<InternalHardwareLightState> property = proxy (ObjectHardwareProperty.class, (ignored, method, arguments) -> null);
        final MultiStateHardwareLight light = proxy (MultiStateHardwareLight.class, (ignored, method, arguments) -> {
            if (method.getName ().equals ("state"))
                return property;
            if (method.getName ().equals ("setColorToStateFunction"))
            {
                @SuppressWarnings("unchecked")
                final Function<Color, InternalHardwareLightState> function = (Function<Color, InternalHardwareLightState>) arguments[0];
                colorToState.set (function);
            }
            return null;
        });
        final InternalHardwareLightState supplied = new RawColorLightState (ColorEx.BLACK);
        final HwLightImpl subject = new HwLightImpl (proxy (IHost.class, (ignored, method, arguments) -> null), light, () -> supplied, ignored -> { });
        final AtomicReference<Optional<ColorEx>> observed = new AtomicReference<> ();

        subject.installMappedColorObserver (observed::set);
        assertSame (supplied, colorToState.get ().apply (Color.fromRGBA (0.25, 0.5, 1, 1)));
        assertEquals (Optional.of (new ColorEx (0.25, 0.5, 1)), observed.get ());

        assertSame (supplied, colorToState.get ().apply (Color.nullColor ()));
        assertEquals (Optional.empty (), observed.get ());
        assertThrows (IllegalStateException.class, () -> subject.installMappedColorObserver (ignored -> { }));
    }


    @SuppressWarnings("unchecked")
    private static <T> T proxy (final Class<T> type, final java.lang.reflect.InvocationHandler handler)
    {
        return (T) Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] {type}, handler);
    }
}
