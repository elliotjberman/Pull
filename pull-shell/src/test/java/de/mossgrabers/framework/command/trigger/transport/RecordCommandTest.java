// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.command.trigger.transport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import de.mossgrabers.framework.configuration.Configuration;
import de.mossgrabers.framework.controller.IControlSurface;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.utils.ButtonEvent;

import org.junit.jupiter.api.Test;


/**
 * Tests arranger-record command semantics.
 */
class RecordCommandTest
{
    @Test
    void normalRecordPressesToggleArrangerRecording ()
    {
        final AtomicInteger toggles = new AtomicInteger ();
        final AtomicInteger starts = new AtomicInteger ();
        final ITransport transport = proxy (ITransport.class, (proxy, method, arguments) -> {
            if ("toggleRecording".equals (method.getName ()))
                toggles.incrementAndGet ();
            if ("startRecording".equals (method.getName ()))
                starts.incrementAndGet ();
            return defaultValue (method.getReturnType ());
        });
        final IModel model = proxy (IModel.class, (proxy, method, arguments) -> {
            if ("getTransport".equals (method.getName ()))
                return transport;
            return defaultValue (method.getReturnType ());
        });
        final Configuration configuration = proxy (Configuration.class, (proxy, method, arguments) -> defaultValue (method.getReturnType ()));
        final IControlSurface<Configuration> surface = controlSurface (configuration);
        final RecordCommand<IControlSurface<Configuration>, Configuration> command = new RecordCommand<> (model, surface);

        command.execute (ButtonEvent.UP, 0);
        command.execute (ButtonEvent.UP, 0);

        assertEquals (2, toggles.get ());
        assertEquals (0, starts.get ());
    }


    @SuppressWarnings("unchecked")
    private static IControlSurface<Configuration> controlSurface (final Configuration configuration)
    {
        return (IControlSurface<Configuration>) Proxy.newProxyInstance (IControlSurface.class.getClassLoader (), new Class<?> []
        {
            IControlSurface.class
        }, (proxy, method, arguments) -> {
            if ("getConfiguration".equals (method.getName ()))
                return configuration;
            return defaultValue (method.getReturnType ());
        });
    }


    private static <T> T proxy (final Class<T> type, final java.lang.reflect.InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?> []
        {
            type
        }, handler));
    }


    private static Object defaultValue (final Class<?> type)
    {
        if (!type.isPrimitive () || void.class.equals (type))
            return null;
        if (boolean.class.equals (type))
            return Boolean.FALSE;
        if (char.class.equals (type))
            return Character.valueOf ('\0');
        if (byte.class.equals (type))
            return Byte.valueOf ((byte) 0);
        if (short.class.equals (type))
            return Short.valueOf ((short) 0);
        if (int.class.equals (type))
            return Integer.valueOf (0);
        if (long.class.equals (type))
            return Long.valueOf (0L);
        if (float.class.equals (type))
            return Float.valueOf (0.0F);
        return Double.valueOf (0.0);
    }
}
