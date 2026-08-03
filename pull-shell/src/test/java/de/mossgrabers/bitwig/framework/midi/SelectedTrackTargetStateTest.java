// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.midi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorDeviceFollowMode;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.DeviceLayer;
import com.bitwig.extension.controller.api.DeviceLayerBank;
import com.bitwig.extension.controller.api.DeviceMatcher;
import com.bitwig.extension.controller.api.DeviceSlot;
import com.bitwig.extension.controller.api.PinnableCursorDevice;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.StringValue;

import de.mossgrabers.bitwig.framework.daw.ModelImpl;

import org.junit.jupiter.api.Test;


/**
 * Tests capability read-back from the private selected-track target.
 */
class SelectedTrackTargetStateTest
{
    @Test
    void derivesPrimaryDrumApplicabilityFromTheDirectRoutingTarget ()
    {
        final AtomicReference<String> targetID = new AtomicReference<> ("track-b");
        final AtomicBoolean targetExists = new AtomicBoolean (true);
        final AtomicBoolean targetCanHoldNotes = new AtomicBoolean (true);
        final AtomicBoolean [] candidateExists = booleans (true, false, false, false);
        final AtomicBoolean [] candidateHasPads = booleans (false, true, false, false);
        final AtomicInteger interests = new AtomicInteger ();
        final AtomicInteger matchedBanks = new AtomicInteger ();
        final AtomicInteger createdBanks = new AtomicInteger ();
        final AtomicReference<Object []> cursorArguments = new AtomicReference<> ();

        final DeviceMatcher matcher = relaxedProxy (DeviceMatcher.class);
        final DeviceBank directBank = matchedBank (device (candidateExists[0], candidateHasPads[0], interests), matcher, matchedBanks);
        final DeviceBank layerBank = matchedBank (device (candidateExists[2], candidateHasPads[2], interests), matcher, matchedBanks);
        final DeviceBank slotBank = matchedBank (device (candidateExists[3], candidateHasPads[3], interests), matcher, matchedBanks);
        final DeviceLayer firstLayer = proxy (DeviceLayer.class, (proxy, method, arguments) -> {
            if ("createDeviceBank".equals (method.getName ()))
            {
                assertEquals (1, arguments[0]);
                createdBanks.incrementAndGet ();
                return layerBank;
            }
            return relaxedValue (method.getReturnType ());
        });
        final DeviceLayerBank layers = proxy (DeviceLayerBank.class, (proxy, method, arguments) -> {
            if ("getItemAt".equals (method.getName ()))
                return firstLayer;
            return relaxedValue (method.getReturnType ());
        });
        final DeviceSlot cursorSlot = proxy (DeviceSlot.class, (proxy, method, arguments) -> {
            if ("createDeviceBank".equals (method.getName ()))
            {
                assertEquals (1, arguments[0]);
                createdBanks.incrementAndGet ();
                return slotBank;
            }
            return relaxedValue (method.getReturnType ());
        });
        final BooleanValue primaryInstrumentExists = booleanValue (BooleanValue.class, candidateExists[1], interests);
        final BooleanValue primaryInstrumentHasPads = booleanValue (BooleanValue.class, candidateHasPads[1], interests);
        final PinnableCursorDevice primaryInstrument = proxy (PinnableCursorDevice.class, (proxy, method, arguments) -> {
            if ("exists".equals (method.getName ()))
                return primaryInstrumentExists;
            if ("hasDrumPads".equals (method.getName ()))
                return primaryInstrumentHasPads;
            if ("createLayerBank".equals (method.getName ()))
            {
                assertEquals (1, arguments[0]);
                return layers;
            }
            if ("getCursorSlot".equals (method.getName ()))
                return cursorSlot;
            return relaxedValue (method.getReturnType ());
        });

        final StringValue targetIDValue = stringValue (targetID, interests);
        final BooleanValue targetExistsValue = booleanValue (BooleanValue.class, targetExists, interests);
        final SettableBooleanValue targetCanHoldNotesValue = booleanValue (SettableBooleanValue.class, targetCanHoldNotes, interests);
        final CursorTrack target = proxy (CursorTrack.class, (proxy, method, arguments) -> {
            if ("channelId".equals (method.getName ()))
                return targetIDValue;
            if ("exists".equals (method.getName ()))
                return targetExistsValue;
            if ("canHoldNoteData".equals (method.getName ()))
                return targetCanHoldNotesValue;
            if ("createDeviceBank".equals (method.getName ()))
            {
                assertEquals (1, arguments[0]);
                createdBanks.incrementAndGet ();
                return directBank;
            }
            if ("createCursorDevice".equals (method.getName ()) && arguments.length == 4)
            {
                cursorArguments.set (arguments);
                return primaryInstrument;
            }
            return relaxedValue (method.getReturnType ());
        });
        final ControllerHost host = proxy (ControllerHost.class, (proxy, method, arguments) -> {
            if ("createBitwigDeviceMatcher".equals (method.getName ()))
            {
                assertEquals (ModelImpl.INSTRUMENT_DRUM_MACHINE, arguments[0]);
                return matcher;
            }
            return relaxedValue (method.getReturnType ());
        });

        final SelectedTrackTargetState state = new SelectedTrackTargetState (host, target);

        assertArrayEquals (new Object []
        {
            SelectedTrackTargetState.DRUM_DEVICE_CURSOR_ID,
            SelectedTrackTargetState.DRUM_DEVICE_CURSOR_NAME,
            Integer.valueOf (0),
            CursorDeviceFollowMode.FIRST_INSTRUMENT
        }, cursorArguments.get ());
        assertEquals (3, createdBanks.get ());
        assertEquals (3, matchedBanks.get ());
        assertEquals (11, interests.get ());
        assertEquals ("track-b", state.getChannelID ());
        assertTrue (state.doesExist ());
        assertTrue (state.canHoldNotes ());

        // Capability must come from one complete candidate, never by combining two candidates.
        assertFalse (state.hasDrumDevice ());
        candidateHasPads[0].set (true);
        assertTrue (state.hasDrumDevice ());
        candidateHasPads[0].set (false);
        candidateExists[1].set (true);
        assertTrue (state.hasDrumDevice ());
        candidateExists[1].set (false);
        candidateExists[2].set (true);
        candidateHasPads[2].set (true);
        assertTrue (state.hasDrumDevice ());
        candidateHasPads[2].set (false);
        candidateExists[2].set (false);
        candidateExists[3].set (true);
        candidateHasPads[3].set (true);
        assertTrue (state.hasDrumDevice ());
        candidateHasPads[3].set (false);
        assertFalse (state.hasDrumDevice ());

        targetExists.set (false);
        assertFalse (state.doesExist ());
        targetCanHoldNotes.set (false);
        assertFalse (state.canHoldNotes ());
        targetID.set ("track-c");
        assertEquals ("track-c", state.getChannelID ());
    }


    private static DeviceBank matchedBank (final Device candidate, final DeviceMatcher matcher, final AtomicInteger matchedBanks)
    {
        return proxy (DeviceBank.class, (proxy, method, arguments) -> {
            if ("setDeviceMatcher".equals (method.getName ()))
            {
                assertSame (matcher, arguments[0]);
                matchedBanks.incrementAndGet ();
                return null;
            }
            if ("getItemAt".equals (method.getName ()))
                return candidate;
            return relaxedValue (method.getReturnType ());
        });
    }


    private static Device device (final AtomicBoolean exists, final AtomicBoolean hasPads, final AtomicInteger interests)
    {
        final BooleanValue existsValue = booleanValue (BooleanValue.class, exists, interests);
        final BooleanValue hasPadsValue = booleanValue (BooleanValue.class, hasPads, interests);
        return proxy (Device.class, (proxy, method, arguments) -> {
            if ("exists".equals (method.getName ()))
                return existsValue;
            if ("hasDrumPads".equals (method.getName ()))
                return hasPadsValue;
            return relaxedValue (method.getReturnType ());
        });
    }


    private static AtomicBoolean [] booleans (final boolean... values)
    {
        final AtomicBoolean [] result = new AtomicBoolean [values.length];
        for (int index = 0; index < values.length; index++)
            result[index] = new AtomicBoolean (values[index]);
        return result;
    }


    private static StringValue stringValue (final AtomicReference<String> value, final AtomicInteger interests)
    {
        return proxy (StringValue.class, (proxy, method, arguments) -> {
            if ("get".equals (method.getName ()))
                return value.get ();
            if ("markInterested".equals (method.getName ()))
                interests.incrementAndGet ();
            return relaxedValue (method.getReturnType ());
        });
    }


    private static <T> T booleanValue (final Class<T> type, final AtomicBoolean value, final AtomicInteger interests)
    {
        return proxy (type, (proxy, method, arguments) -> {
            if ("get".equals (method.getName ()) || "getAsBoolean".equals (method.getName ()))
                return Boolean.valueOf (value.get ());
            if ("markInterested".equals (method.getName ()))
                interests.incrementAndGet ();
            return relaxedValue (method.getReturnType ());
        });
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
