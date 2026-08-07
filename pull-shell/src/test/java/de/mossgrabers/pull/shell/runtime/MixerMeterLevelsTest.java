// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.data.IChannel;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertEquals;


class MixerMeterLevelsTest
{
    @Test
    void capturesAndNormalizesLiveChannelMetersWithoutAConfigurationGate ()
    {
        final IChannel channel = (IChannel) Proxy.newProxyInstance (
            IChannel.class.getClassLoader (),
            new Class<?> [] { IChannel.class },
            (proxy, method, arguments) -> switch (method.getName ())
            {
                case "getVuLeft" -> Integer.valueOf (768);
                case "getVuRight" -> Integer.valueOf (256);
                default -> defaultValue (method.getReturnType ());
            });
        final IValueChanger valueChanger = (IValueChanger) Proxy.newProxyInstance (
            IValueChanger.class.getClassLoader (),
            new Class<?> [] { IValueChanger.class },
            (proxy, method, arguments) -> "toNormalizedValue".equals (method.getName ()) ? Double.valueOf (((Number) arguments[0]).doubleValue () / 1024.0) : defaultValue (method.getReturnType ()));

        final MixerMeterLevels levels = MixerMeterLevels.capture (channel);

        assertEquals (768, levels.left ());
        assertEquals (256, levels.right ());
        assertEquals (0.75, levels.normalizedLeft (valueChanger));
        assertEquals (0.25, levels.normalizedRight (valueChanger));
    }


    private static Object defaultValue (final Class<?> type)
    {
        if (!type.isPrimitive ())
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
}
