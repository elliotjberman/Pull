// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.midi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.NoteInput;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.Track;

import org.junit.jupiter.api.Test;


/**
 * Tests direct routing of a controller note input.
 */
class NoteInputImplTest
{
    @Test
    void selectedTrackTargetUsesAPrivateSelectionFollowingCursor ()
    {
        final CursorTrack selectedTrack = proxy (CursorTrack.class, (proxy, method, arguments) -> defaultValue (method.getReturnType ()));
        final AtomicReference<Object []> cursorArguments = new AtomicReference<> ();
        final ControllerHost host = proxy (ControllerHost.class, (proxy, method, arguments) -> {
            if ("createCursorTrack".equals (method.getName ()) && arguments.length == 5)
            {
                cursorArguments.set (arguments);
                return selectedTrack;
            }
            return defaultValue (method.getReturnType ());
        });

        assertSame (selectedTrack, MidiInputImpl.createSelectedTrackNoteTarget (host));
        assertArrayEquals (new Object []
        {
            "PULL_PADS_SELECTED_TRACK",
            "Pull Pads Selected Track",
            Integer.valueOf (0),
            Integer.valueOf (0),
            Boolean.TRUE
        }, cursorArguments.get ());
    }


    @Test
    void directRoutingExcludesTheInputFromAllInputsBeforeAttachingIt ()
    {
        final List<String> calls = new ArrayList<> ();
        final AtomicReference<NoteInput> attachedInput = new AtomicReference<> ();
        final SettableBooleanValue includedInAllInputs = proxy (SettableBooleanValue.class, (proxy, method, arguments) -> {
            if ("set".equals (method.getName ()))
                calls.add ("include:" + arguments[0]);
            return defaultValue (method.getReturnType ());
        });
        final NoteInput noteInput = proxy (NoteInput.class, (proxy, method, arguments) -> {
            if ("includeInAllInputs".equals (method.getName ()))
                return includedInAllInputs;
            return defaultValue (method.getReturnType ());
        });
        final Track track = proxy (Track.class, (proxy, method, arguments) -> {
            if ("addNoteSource".equals (method.getName ()))
            {
                calls.add ("attach");
                attachedInput.set ((NoteInput) arguments[0]);
            }
            return defaultValue (method.getReturnType ());
        });

        NoteInputImpl.routeDirectly (noteInput, track);

        assertEquals (List.of ("include:false", "attach"), calls);
        assertSame (noteInput, attachedInput.get ());
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
