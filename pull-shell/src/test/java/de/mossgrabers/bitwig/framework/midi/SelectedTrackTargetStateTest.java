// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.midi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.bitwig.extension.controller.api.IntegerValue;
import com.bitwig.extension.controller.api.Parameter;
import com.bitwig.extension.controller.api.PlayingNote;
import com.bitwig.extension.controller.api.PlayingNoteArrayValue;
import com.bitwig.extension.controller.api.PinnableCursorDevice;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.SettableColorValue;
import com.bitwig.extension.controller.api.SettableEnumValue;
import com.bitwig.extension.controller.api.SettableRangedValue;
import com.bitwig.extension.controller.api.SettableStringValue;
import com.bitwig.extension.controller.api.SoloValue;
import com.bitwig.extension.controller.api.StringValue;
import com.bitwig.extension.callback.ObjectValueChangedCallback;

import de.mossgrabers.bitwig.framework.daw.ModelImpl;
import de.mossgrabers.framework.daw.midi.SelectedTrackMonitorMode;
import de.mossgrabers.framework.daw.midi.SelectedTrackNoteTargetSnapshot;

import org.junit.jupiter.api.Test;


/**
 * Tests capability read-back from the private selected-track target.
 */
class SelectedTrackTargetStateTest
{
    @Test
    void exposesBoundedStateAndAbsoluteActions ()
    {
        final AtomicInteger interests = new AtomicInteger ();
        final AtomicReference<String> targetID = new AtomicReference<> ("track-a");
        final AtomicBoolean targetExists = new AtomicBoolean (true);
        final AtomicReference<String> name = new AtomicReference<> ("Drums");
        final AtomicReference<String> type = new AtomicReference<> ("Instrument");
        final AtomicInteger position = new AtomicInteger (7);
        final AtomicBoolean canHoldNotes = new AtomicBoolean (true);
        final AtomicBoolean canHoldAudio = new AtomicBoolean (false);
        final AtomicBoolean group = new AtomicBoolean (true);
        final AtomicBoolean expanded = new AtomicBoolean (true);
        final AtomicBoolean activated = new AtomicBoolean (true);
        final AtomicBoolean armed = new AtomicBoolean (false);
        final AtomicReference<String> monitorMode = new AtomicReference<> ("AUTO");
        final AtomicBoolean muted = new AtomicBoolean (false);
        final AtomicBoolean soloed = new AtomicBoolean (true);
        final AtomicBoolean mutedBySolo = new AtomicBoolean (false);
        final AtomicBoolean stopped = new AtomicBoolean (false);
        final AtomicReference<Double> volume = new AtomicReference<> (Double.valueOf (0.75));
        final AtomicReference<Double> pan = new AtomicReference<> (Double.valueOf (0.25));
        final AtomicReference<PlayingNote []> notes = new AtomicReference<> (new PlayingNote []
        {
            playingNote (36, 96),
            playingNote (36, 112),
            playingNote (60, 64)
        });
        final AtomicReference<ObjectValueChangedCallback<PlayingNote []>> notesObserver = new AtomicReference<> ();
        final AtomicInteger stopCalls = new AtomicInteger ();
        final AtomicInteger returnCalls = new AtomicInteger ();

        final PlayingNoteArrayValue playingNotes = proxy (PlayingNoteArrayValue.class, (proxy, method, arguments) -> {
            if ("get".equals (method.getName ()))
                return notes.get ();
            if ("markInterested".equals (method.getName ()))
                interests.incrementAndGet ();
            if ("addValueObserver".equals (method.getName ()))
            {
                @SuppressWarnings("unchecked")
                final ObjectValueChangedCallback<PlayingNote []> observer = (ObjectValueChangedCallback<PlayingNote []>) arguments[0];
                notesObserver.set (observer);
                observer.valueChanged (notes.get ());
            }
            return relaxedValue (method.getReturnType ());
        });
        final SettableRangedValue volumeValue = rangedValue (volume, interests);
        final SettableRangedValue panValue = rangedValue (pan, interests);
        final DeviceMatcher drumMatcher = relaxedProxy (DeviceMatcher.class);
        final Device emptyDrumCandidate = relaxedProxy (Device.class);
        final DeviceBank directDrumBank = deviceBank (emptyDrumCandidate);
        final DeviceBank layerDrumBank = deviceBank (emptyDrumCandidate);
        final DeviceBank slotDrumBank = deviceBank (emptyDrumCandidate);
        final DeviceLayer firstLayer = proxy (DeviceLayer.class, (proxy, method, arguments) -> "createDeviceBank".equals (method.getName ()) ? layerDrumBank : relaxedValue (method.getReturnType ()));
        final DeviceLayerBank layers = proxy (DeviceLayerBank.class, (proxy, method, arguments) -> "getItemAt".equals (method.getName ()) ? firstLayer : relaxedValue (method.getReturnType ()));
        final DeviceSlot cursorSlot = proxy (DeviceSlot.class, (proxy, method, arguments) -> "createDeviceBank".equals (method.getName ()) ? slotDrumBank : relaxedValue (method.getReturnType ()));
        final PinnableCursorDevice primaryInstrument = proxy (PinnableCursorDevice.class, (proxy, method, arguments) -> {
            return switch (method.getName ())
            {
                case "createLayerBank" -> layers;
                case "getCursorSlot" -> cursorSlot;
                default -> relaxedValue (method.getReturnType ());
            };
        });
        final CursorTrack target = proxy (CursorTrack.class, (proxy, method, arguments) -> {
            return switch (method.getName ())
            {
                case "channelId" -> stringValue (targetID, interests);
                case "exists" -> booleanValue (BooleanValue.class, targetExists, interests);
                case "name" -> settableStringValue (name, interests);
                case "color" -> colorValue (0.1F, 0.2F, 0.3F, interests);
                case "trackType" -> stringValue (type, interests);
                case "position" -> integerValue (position, interests);
                case "canHoldNoteData" -> booleanValue (SettableBooleanValue.class, canHoldNotes, interests);
                case "canHoldAudioData" -> booleanValue (SettableBooleanValue.class, canHoldAudio, interests);
                case "isGroup" -> booleanValue (BooleanValue.class, group, interests);
                case "isGroupExpanded" -> booleanValue (SettableBooleanValue.class, expanded, interests);
                case "isActivated" -> booleanValue (SettableBooleanValue.class, activated, interests);
                case "arm" -> booleanValue (SettableBooleanValue.class, armed, interests);
                case "monitorMode" -> enumValue (monitorMode, interests);
                case "mute" -> booleanValue (SettableBooleanValue.class, muted, interests);
                case "solo" -> booleanValue (SoloValue.class, soloed, interests);
                case "isMutedBySolo" -> booleanValue (BooleanValue.class, mutedBySolo, interests);
                case "isStopped" -> booleanValue (BooleanValue.class, stopped, interests);
                case "volume" -> parameter (volumeValue);
                case "pan" -> parameter (panValue);
                case "playingNotes" -> playingNotes;
                case "createDeviceBank" -> directDrumBank;
                case "createCursorDevice" -> primaryInstrument;
                case "stop" -> {
                    stopCalls.incrementAndGet ();
                    yield null;
                }
                case "returnToArrangement" -> {
                    returnCalls.incrementAndGet ();
                    yield null;
                }
                default -> relaxedValue (method.getReturnType ());
            };
        });
        final ControllerHost host = proxy (ControllerHost.class, (proxy, method, arguments) -> "createBitwigDeviceMatcher".equals (method.getName ()) ? drumMatcher : relaxedValue (method.getReturnType ()));
        final SelectedTrackTargetState state = new SelectedTrackTargetState (host, target);

        final SelectedTrackNoteTargetSnapshot snapshot = state.snapshot ();
        assertEquals (1, snapshot.generation ());
        assertEquals ("track-a", snapshot.trackID ());
        assertTrue (snapshot.exists ());
        assertEquals ("Drums", snapshot.name ());
        assertEquals (0.1, snapshot.colorRed (), 0.0001);
        assertEquals (0.2, snapshot.colorGreen (), 0.0001);
        assertEquals (0.3, snapshot.colorBlue (), 0.0001);
        assertEquals ("Instrument", snapshot.trackType ());
        assertEquals (7, snapshot.position ());
        assertTrue (snapshot.canHoldNotes ());
        assertFalse (snapshot.canHoldAudio ());
        assertTrue (snapshot.group ());
        assertTrue (snapshot.groupExpanded ());
        assertTrue (snapshot.activated ());
        assertFalse (snapshot.armed ());
        assertEquals (SelectedTrackMonitorMode.AUTO, snapshot.monitorMode ());
        assertFalse (snapshot.muted ());
        assertTrue (snapshot.soloed ());
        assertFalse (snapshot.mutedBySolo ());
        assertTrue (snapshot.clipPlaying ());
        assertFalse (snapshot.stopped ());
        assertEquals (0.75, snapshot.volume ());
        assertEquals (0.25, snapshot.pan ());
        assertEquals (112, state.getPlayingVelocity (36));
        assertEquals (64, state.getPlayingVelocity (60));
        assertEquals (0, state.getPlayingVelocity (37));

        state.setGroupExpanded (false);
        state.setActivated (false);
        state.setArmed (true);
        state.setMonitorMode (SelectedTrackMonitorMode.ON);
        state.setMuted (true);
        state.setSoloed (false);
        state.setVolume (0.5);
        state.setPan (0.6);
        state.stop ();
        state.returnToArrangement ();
        assertFalse (expanded.get ());
        assertFalse (activated.get ());
        assertTrue (armed.get ());
        assertEquals ("ON", monitorMode.get ());
        assertTrue (muted.get ());
        assertFalse (soloed.get ());
        assertEquals (0.5, volume.get ().doubleValue ());
        assertEquals (0.6, pan.get ().doubleValue ());
        assertEquals (1, stopCalls.get ());
        assertEquals (1, returnCalls.get ());

        notes.set (new PlayingNote []
        {
            playingNote (48, 100)
        });
        notesObserver.get ().valueChanged (notes.get ());
        assertEquals (0, state.getPlayingVelocity (36));
        assertEquals (100, state.getPlayingVelocity (48));

        targetID.set ("track-b");
        assertEquals (2, state.getGeneration ());
        targetExists.set (false);
        assertEquals (3, state.getGeneration ());
        assertThrows (IllegalArgumentException.class, () -> state.setVolume (-0.1));
        assertThrows (IllegalArgumentException.class, () -> state.setPan (Double.NaN));
        assertThrows (IllegalArgumentException.class, () -> state.getPlayingVelocity (128));
    }


    @Test
    void derivesPrimaryDrumApplicabilityFromTheSelectedTarget ()
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


    private static DeviceBank deviceBank (final Device candidate)
    {
        return proxy (DeviceBank.class, (proxy, method, arguments) -> "getItemAt".equals (method.getName ()) ? candidate : relaxedValue (method.getReturnType ()));
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


    private static SettableStringValue settableStringValue (final AtomicReference<String> value, final AtomicInteger interests)
    {
        return proxy (SettableStringValue.class, (proxy, method, arguments) -> {
            if ("get".equals (method.getName ()))
                return value.get ();
            if ("set".equals (method.getName ()))
            {
                value.set ((String) arguments[0]);
                return null;
            }
            if ("markInterested".equals (method.getName ()))
                interests.incrementAndGet ();
            return relaxedValue (method.getReturnType ());
        });
    }


    private static IntegerValue integerValue (final AtomicInteger value, final AtomicInteger interests)
    {
        return proxy (IntegerValue.class, (proxy, method, arguments) -> {
            if ("get".equals (method.getName ()) || "getAsInt".equals (method.getName ()))
                return Integer.valueOf (value.get ());
            if ("markInterested".equals (method.getName ()))
                interests.incrementAndGet ();
            return relaxedValue (method.getReturnType ());
        });
    }


    private static SettableColorValue colorValue (final float red, final float green, final float blue, final AtomicInteger interests)
    {
        return proxy (SettableColorValue.class, (proxy, method, arguments) -> {
            if ("red".equals (method.getName ()))
                return Float.valueOf (red);
            if ("green".equals (method.getName ()))
                return Float.valueOf (green);
            if ("blue".equals (method.getName ()))
                return Float.valueOf (blue);
            if ("alpha".equals (method.getName ()))
                return Float.valueOf (1.0F);
            if ("markInterested".equals (method.getName ()))
                interests.incrementAndGet ();
            return relaxedValue (method.getReturnType ());
        });
    }


    private static SettableEnumValue enumValue (final AtomicReference<String> value, final AtomicInteger interests)
    {
        return proxy (SettableEnumValue.class, (proxy, method, arguments) -> {
            if ("get".equals (method.getName ()))
                return value.get ();
            if ("set".equals (method.getName ()))
            {
                value.set ((String) arguments[0]);
                return null;
            }
            if ("markInterested".equals (method.getName ()))
                interests.incrementAndGet ();
            return relaxedValue (method.getReturnType ());
        });
    }


    private static SettableRangedValue rangedValue (final AtomicReference<Double> value, final AtomicInteger interests)
    {
        return proxy (SettableRangedValue.class, (proxy, method, arguments) -> {
            if ("get".equals (method.getName ()) || "getAsDouble".equals (method.getName ()))
                return value.get ();
            if ("set".equals (method.getName ()) && arguments.length == 1)
            {
                value.set (Double.valueOf (((Number) arguments[0]).doubleValue ()));
                return null;
            }
            if ("markInterested".equals (method.getName ()))
                interests.incrementAndGet ();
            return relaxedValue (method.getReturnType ());
        });
    }


    private static Parameter parameter (final SettableRangedValue value)
    {
        return proxy (Parameter.class, (proxy, method, arguments) -> {
            if ("value".equals (method.getName ()))
                return value;
            return relaxedValue (method.getReturnType ());
        });
    }


    private static PlayingNote playingNote (final int pitch, final int velocity)
    {
        return proxy (PlayingNote.class, (proxy, method, arguments) -> {
            if ("pitch".equals (method.getName ()))
                return Integer.valueOf (pitch);
            if ("velocity".equals (method.getName ()))
                return Integer.valueOf (velocity);
            return relaxedValue (method.getReturnType ());
        });
    }


    private static <T> T booleanValue (final Class<T> type, final AtomicBoolean value, final AtomicInteger interests)
    {
        return proxy (type, (proxy, method, arguments) -> {
            if ("get".equals (method.getName ()) || "getAsBoolean".equals (method.getName ()))
                return Boolean.valueOf (value.get ());
            if ("set".equals (method.getName ()))
            {
                value.set (((Boolean) arguments[0]).booleanValue ());
                return null;
            }
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
