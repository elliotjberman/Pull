// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.hardware;

import com.bitwig.extension.controller.api.AbsoluteHardwareControl;
import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.DoubleValue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.mossgrabers.pull.core.api.ControllerMappingTarget;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.bitwig.extension.callback.BooleanValueChangedCallback;
import com.bitwig.extension.callback.DoubleValueChangedCallback;


/** Direct API-25 coverage for authoritative Bitwig manual-mapping target facts. */
class HwSurfaceFactoryImplTest
{
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesTargetFactsAfterBothInitialCallbacks (final boolean presenceFirst)
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
        final List<ControllerMappingTarget> observed = new ArrayList<> ();

        HwSurfaceFactoryImpl.installMappedAbsoluteFeedback (control, (hasTarget, value) -> observed.add (new ControllerMappingTarget (hasTarget, value)));
        if (presenceFirst)
            hasTargetObserver.get ().valueChanged (true);
        else
            targetObserver.get ().valueChanged (0.8);
        assertEquals (List.of (), observed, "one property callback does not establish initial readiness");
        if (presenceFirst)
            targetObserver.get ().valueChanged (0.8);
        else
            hasTargetObserver.get ().valueChanged (true);

        targetObserver.get ().valueChanged (0.2);
        targetObserver.get ().valueChanged (0.4);
        targetObserver.get ().valueChanged (0.5);
        targetObserver.get ().valueChanged (0.8);
        hasTargetObserver.get ().valueChanged (false);

        assertEquals (List.of (
            new ControllerMappingTarget (true, 0.8),
            new ControllerMappingTarget (true, 0.2),
            new ControllerMappingTarget (true, 0.4),
            new ControllerMappingTarget (true, 0.5),
            new ControllerMappingTarget (true, 0.8),
            new ControllerMappingTarget (false, 0.8)), observed);

    }


    @SuppressWarnings("unchecked")
    private static <T> T proxy (final Class<T> type, final java.lang.reflect.InvocationHandler handler)
    {
        return (T) Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] {type}, handler);
    }
}
