// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.hardware;

import com.bitwig.extension.controller.api.BooleanHardwareProperty;
import com.bitwig.extension.controller.api.AbsoluteHardwareControl;
import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.DoubleValue;
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
import com.bitwig.extension.callback.BooleanValueChangedCallback;
import com.bitwig.extension.callback.DoubleValueChangedCallback;


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


    @Test
    void observesOnlyAuthoritativeMappedAbsoluteTargetState ()
    {
        final AtomicReference<BooleanValueChangedCallback> hasTargetObserver = new AtomicReference<> ();
        final AtomicReference<DoubleValueChangedCallback> targetObserver = new AtomicReference<> ();
        final BooleanValue hasTargetValue = proxy (BooleanValue.class, (ignored, method, arguments) -> {
            if (method.getName ().equals ("addValueObserver"))
                hasTargetObserver.set ((BooleanValueChangedCallback) arguments[0]);
            return null;
        });
        final DoubleValue targetValue = proxy (DoubleValue.class, (ignored, method, arguments) -> {
            if (method.getName ().equals ("addValueObserver"))
                targetObserver.set ((DoubleValueChangedCallback) arguments[0]);
            return null;
        });
        final AbsoluteHardwareControl control = proxy (AbsoluteHardwareControl.class, (ignored, method, arguments) -> switch (method.getName ())
        {
            case "hasTargetValue" -> hasTargetValue;
            case "targetValue" -> targetValue;
            default -> null;
        });
        final List<Boolean> observed = new ArrayList<> ();

        HwSurfaceFactoryImpl.installMappedAbsoluteFeedback (control, observed::add);
        hasTargetObserver.get ().valueChanged (true);
        assertEquals (List.of (), observed, "target presence alone is not a coherent authoritative sample");
        targetObserver.get ().valueChanged (0.8);
        targetObserver.get ().valueChanged (0.2);
        hasTargetObserver.get ().valueChanged (false);

        assertEquals (List.of (true, false, false), observed);
    }


    @SuppressWarnings("unchecked")
    private static <T> T proxy (final Class<T> type, final java.lang.reflect.InvocationHandler handler)
    {
        return (T) Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] {type}, handler);
    }
}
