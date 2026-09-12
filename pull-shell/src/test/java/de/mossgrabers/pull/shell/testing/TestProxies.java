// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.shell.testing;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

/** Shared inert proxies; tests supply their own observations and mutation handling. */
public final class TestProxies
{
    private TestProxies () { }

    public static <T> T proxy (final Class<T> type, final InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?> [] { type }, handler));
    }

    public static <T> T relaxedProxy (final Class<T> type)
    {
        return proxy (type, (proxy, method, arguments) -> relaxedValue (method.getReturnType ()));
    }

    public static Object relaxedValue (final Class<?> type)
    {
        if (type.isInterface ())
            return relaxedProxy (type);
        return defaultValue (type);
    }

    /** Inert recursive values with empty strings; unlike relaxedValue, nested strings are non-null. */
    public static Object empty (final Class<?> type)
    {
        if (type == String.class) return "";
        return type.isInterface () ? proxy (type, (p, method, args) -> empty (method.getReturnType ())) : defaultValue (type);
    }

    public static Object defaultValue (final Class<?> type)
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
