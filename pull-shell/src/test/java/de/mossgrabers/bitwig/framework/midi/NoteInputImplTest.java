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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.DeviceLayer;
import com.bitwig.extension.controller.api.DeviceLayerBank;
import com.bitwig.extension.controller.api.DeviceSlot;
import com.bitwig.extension.controller.api.MidiIn;
import com.bitwig.extension.controller.api.NoteInput;
import com.bitwig.extension.controller.api.PinnableCursorDevice;
import com.bitwig.extension.controller.api.SettableBooleanValue;

import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;

import org.junit.jupiter.api.Test;


/**
 * Tests selected-track controller note-input routing and private target observation.
 */
class NoteInputImplTest
{
    @Test
    void selectedTrackRouteExcludesAllInputsAndReconcilesIdempotently ()
    {
        final AtomicReference<CursorTrack> stateTarget = new AtomicReference<> ();
        final AtomicInteger noteSourceAttachments = new AtomicInteger ();
        final AtomicInteger noteSourceRemovals = new AtomicInteger ();
        final List<Boolean> includedInAllInputs = new ArrayList<> ();
        final Device drumMachine = relaxedProxy (Device.class);
        final DeviceBank drumDevices = proxy (DeviceBank.class, (proxy, method, arguments) -> {
            if ("getItemAt".equals (method.getName ()))
                return drumMachine;
            return relaxedValue (method.getReturnType ());
        });
        final DeviceLayer firstLayer = proxy (DeviceLayer.class, (proxy, method, arguments) -> {
            if ("createDeviceBank".equals (method.getName ()))
                return drumDevices;
            return relaxedValue (method.getReturnType ());
        });
        final DeviceLayerBank layers = proxy (DeviceLayerBank.class, (proxy, method, arguments) -> {
            if ("getItemAt".equals (method.getName ()))
                return firstLayer;
            return relaxedValue (method.getReturnType ());
        });
        final DeviceSlot cursorSlot = proxy (DeviceSlot.class, (proxy, method, arguments) -> {
            if ("createDeviceBank".equals (method.getName ()))
                return drumDevices;
            return relaxedValue (method.getReturnType ());
        });
        final PinnableCursorDevice primaryInstrument = proxy (PinnableCursorDevice.class, (proxy, method, arguments) -> {
            if ("createLayerBank".equals (method.getName ()))
                return layers;
            if ("getCursorSlot".equals (method.getName ()))
                return cursorSlot;
            return relaxedValue (method.getReturnType ());
        });
        final CursorTrack selectedTrack = proxy (CursorTrack.class, (proxy, method, arguments) -> {
            if ("createDeviceBank".equals (method.getName ()))
            {
                stateTarget.set ((CursorTrack) proxy);
                return drumDevices;
            }
            if ("createCursorDevice".equals (method.getName ()))
            {
                stateTarget.set ((CursorTrack) proxy);
                return primaryInstrument;
            }
            if ("addNoteSource".equals (method.getName ()))
            {
                noteSourceAttachments.incrementAndGet ();
                return null;
            }
            if ("removeNoteSource".equals (method.getName ()))
            {
                noteSourceRemovals.incrementAndGet ();
                return null;
            }
            return relaxedValue (method.getReturnType ());
        });
        final SettableBooleanValue includeValue = proxy (SettableBooleanValue.class, (proxy, method, arguments) -> {
            if ("set".equals (method.getName ()))
                includedInAllInputs.add ((Boolean) arguments[0]);
            return relaxedValue (method.getReturnType ());
        });
        final NoteInput noteInput = proxy (NoteInput.class, (proxy, method, arguments) -> {
            if ("includeInAllInputs".equals (method.getName ()))
                return includeValue;
            return relaxedValue (method.getReturnType ());
        });
        final MidiIn midiIn = proxy (MidiIn.class, (proxy, method, arguments) -> {
            if ("createNoteInput".equals (method.getName ()))
                return noteInput;
            return relaxedValue (method.getReturnType ());
        });
        final ControllerHost host = proxy (ControllerHost.class, (proxy, method, arguments) -> {
            if ("getMidiInPort".equals (method.getName ()))
                return midiIn;
            if ("createCursorTrack".equals (method.getName ()))
                return selectedTrack;
            return relaxedValue (method.getReturnType ());
        });

        final MidiInputImpl input = new MidiInputImpl (0, host, "Pull Pads", new String []
        {
            "80????",
            "90????"
        });
        final ISelectedTrackNoteTarget target = input.createSelectedTrackTarget ();
        target.submitNoteInputRoute (true);
        target.submitNoteInputRoute (true);
        target.submitNoteInputRoute (false);
        target.submitNoteInputRoute (false);

        assertSame (selectedTrack, stateTarget.get ());
        assertEquals (1, noteSourceAttachments.get ());
        assertEquals (1, noteSourceRemovals.get ());
        assertEquals (List.of (Boolean.TRUE, Boolean.FALSE), includedInAllInputs);
    }


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

        assertSame (selectedTrack, MidiInputImpl.createSelectedTrackTargetCursor (host));
        assertArrayEquals (new Object []
        {
            "PULL_PADS_SELECTED_TRACK",
            "Pull Pads Selected Track",
            Integer.valueOf (0),
            Integer.valueOf (0),
            Boolean.TRUE
        }, cursorArguments.get ());
    }
    private static <T> T proxy (final Class<T> type, final java.lang.reflect.InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?> []
        {
            type
        }, handler));
    }


    private static <T> T relaxedProxy (final Class<T> type)
    {
        return proxy (type, (proxy, method, arguments) -> relaxedValue (method.getReturnType ()));
    }


    private static Object relaxedValue (final Class<?> type)
    {
        if (type.isInterface ())
            return relaxedProxy (type);
        return defaultValue (type);
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
