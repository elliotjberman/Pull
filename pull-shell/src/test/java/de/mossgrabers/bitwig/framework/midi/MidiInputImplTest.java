// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.midi;

import com.bitwig.extension.controller.api.AbsoluteHardwareControl;
import com.bitwig.extension.controller.api.AbsoluteHardwareValueMatcher;
import com.bitwig.extension.controller.api.MidiIn;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;


/** Exact API-21 matcher coverage for alternating absolute note sources. */
class MidiInputImplTest
{
    @Test
    void bindsPositiveNoteOnToLiteralMaximumAndMinimumWhileIgnoringRelease ()
    {
        final List<MatcherRequest> requests = new ArrayList<> ();
        final AbsoluteHardwareValueMatcher matcher = proxy (AbsoluteHardwareValueMatcher.class, (proxy, method, arguments) -> null);
        final MidiIn input = proxy (MidiIn.class, (proxy, method, arguments) -> {
            if (method.getName ().equals ("createAbsoluteValueMatcher"))
            {
                requests.add (new MatcherRequest ((String) arguments[0], (String) arguments[1], (Integer) arguments[2]));
                return matcher;
            }
            return null;
        });
        final AtomicReference<AbsoluteHardwareValueMatcher> installed = new AtomicReference<> ();
        final AbsoluteHardwareControl control = proxy (AbsoluteHardwareControl.class, (proxy, method, arguments) -> {
            if (method.getName ().equals ("setAdjustValueMatcher"))
                installed.set ((AbsoluteHardwareValueMatcher) arguments[0]);
            return null;
        });

        MidiInputImpl.bindNoteValue (input, control, 0, 64, true);
        MidiInputImpl.bindNoteValue (input, control, -1, 65, false);

        assertEquals (List.of (
            new MatcherRequest ("status == 144 && data1 == 64 && data2 > 0", "127", 7),
            new MatcherRequest ("status >= 0x90 && status <= 0x9F && data1 == 65 && data2 > 0", "0", 7)), requests);
        assertSame (matcher, installed.get ());
    }


    private record MatcherRequest (String eventExpression, String valueExpression, int bitCount)
    {}


    private static <T> T proxy (final Class<T> type, final java.lang.reflect.InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] {type}, handler));
    }
}
