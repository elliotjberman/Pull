// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.hardware;

import com.bitwig.extension.controller.api.BooleanHardwareProperty;
import com.bitwig.extension.controller.api.HardwareButton;
import com.bitwig.extension.controller.api.OnOffHardwareLight;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;


/** Direct API-21 coverage for authoritative Bitwig manual-mapping Boolean feedback. */
class HwSurfaceFactoryImplTest
{
    @Test
    void installsNoOutputBooleanBackgroundAndObservesResolvedHardwareUpdates ()
    {
        final AtomicReference<Boolean> fallback = new AtomicReference<> ();
        final AtomicReference<Consumer<Boolean>> hardwareUpdate = new AtomicReference<> ();
        final BooleanHardwareProperty property = proxy (BooleanHardwareProperty.class, (ignored, method, arguments) -> {
            if (method.getName ().equals ("setValue"))
                fallback.set ((Boolean) arguments[0]);
            else if (method.getName ().equals ("onUpdateHardware"))
            {
                @SuppressWarnings("unchecked")
                final Consumer<Boolean> observer = (Consumer<Boolean>) arguments[0];
                hardwareUpdate.set (observer);
            }
            return null;
        });
        final OnOffHardwareLight feedbackLight = proxy (OnOffHardwareLight.class, (ignored, method, arguments) -> method.getName ().equals ("isOn") ? property : null);
        final AtomicReference<OnOffHardwareLight> background = new AtomicReference<> ();
        final HardwareButton button = proxy (HardwareButton.class, (ignored, method, arguments) -> {
            if (method.getName ().equals ("setBackgroundLight"))
                background.set ((OnOffHardwareLight) arguments[0]);
            return null;
        });
        final List<Boolean> observed = new ArrayList<> ();

        HwSurfaceFactoryImpl.installMappedBooleanFeedback (button, feedbackLight, observed::add);

        assertEquals (false, fallback.get ());
        assertSame (feedbackLight, background.get ());
        hardwareUpdate.get ().accept (true);
        hardwareUpdate.get ().accept (false);
        assertEquals (List.of (true, false), observed);
    }


    @SuppressWarnings("unchecked")
    private static <T> T proxy (final Class<T> type, final java.lang.reflect.InvocationHandler handler)
    {
        return (T) Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] {type}, handler);
    }
}
