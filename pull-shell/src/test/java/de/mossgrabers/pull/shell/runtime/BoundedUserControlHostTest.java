// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.Parameter;
import com.bitwig.extension.controller.api.UserControlBank;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Bitwig API topology and asynchronous read-back tests for the fixed user-control bank. */
class BoundedUserControlHostTest
{
    @Test
    void createsFourNamedInterestedControlsAndDoesNotOptimisticallyEchoWrites ()
    {
        final FakeBitwigUserControls bitwig = new FakeBitwigUserControls (0, 0.25, 0.5, 1);
        final BoundedUserControlHost host = new BoundedUserControlHost (bitwig.host ());

        assertEquals (4, bitwig.requestedCapacity);
        assertEquals (List.of ("Drum Control 1", "Drum Control 2", "Drum Control 3", "Drum Control 4"), bitwig.labels);
        assertTrue (bitwig.interested.stream ().allMatch (Boolean::booleanValue));
        assertEquals (List.of (0.0, 0.25, 0.5, 1.0), host.snapshot ().values ());

        host.set (0, 1);
        assertEquals (1, bitwig.requestedWrites[0]);
        assertEquals (0, host.snapshot ().values ().getFirst ());
        bitwig.values[0] = 1;
        assertEquals (1, host.snapshot ().values ().getFirst ());
    }


    private static final class FakeBitwigUserControls implements InvocationHandler
    {
        private final double [] values;
        private final double [] requestedWrites = new double [4];
        private final List<String> labels = new ArrayList<> (Arrays.asList ("", "", "", ""));
        private final List<Boolean> interested = new ArrayList<> (Arrays.asList (Boolean.FALSE, Boolean.FALSE, Boolean.FALSE, Boolean.FALSE));
        private final List<Parameter> parameters = new ArrayList<> (4);
        private int requestedCapacity;


        private FakeBitwigUserControls (final double... values)
        {
            this.values = values.clone ();
            for (int slot = 0; slot < 4; slot++)
            {
                final int capturedSlot = slot;
                this.parameters.add (proxy (Parameter.class, (proxy, method, arguments) -> this.parameterCall (capturedSlot, proxy, method, arguments)));
            }
        }


        private ControllerHost host ()
        {
            return proxy (ControllerHost.class, this);
        }


        @Override
        public Object invoke (final Object proxy, final Method method, final Object [] arguments)
        {
            if (method.getName ().equals ("createUserControls"))
            {
                this.requestedCapacity = ((Integer) arguments[0]).intValue ();
                return proxy (UserControlBank.class, (bank, bankMethod, bankArguments) -> this.parameters.get (((Integer) bankArguments[0]).intValue ()));
            }
            return defaultValue (method.getReturnType ());
        }


        private Object parameterCall (final int slot, final Object proxy, final Method method, final Object [] arguments)
        {
            return switch (method.getName ())
            {
                case "setLabel" -> {
                    this.labels.set (slot, (String) arguments[0]);
                    yield null;
                }
                case "value" -> proxy;
                case "markInterested" -> {
                    this.interested.set (slot, Boolean.TRUE);
                    yield null;
                }
                case "get", "getAsDouble", "getRaw" -> Double.valueOf (this.values[slot]);
                case "set" -> {
                    this.requestedWrites[slot] = ((Number) arguments[0]).doubleValue ();
                    yield null;
                }
                default -> defaultValue (method.getReturnType ());
            };
        }


        @SuppressWarnings("unchecked")
        private static <T> T proxy (final Class<T> type, final InvocationHandler handler)
        {
            return (T) Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] {type}, handler);
        }


        private static Object defaultValue (final Class<?> type)
        {
            if (!type.isPrimitive ())
                return null;
            if (type == boolean.class)
                return Boolean.FALSE;
            if (type == double.class)
                return Double.valueOf (0);
            if (type == int.class)
                return Integer.valueOf (0);
            if (type == long.class)
                return Long.valueOf (0);
            return null;
        }
    }
}
