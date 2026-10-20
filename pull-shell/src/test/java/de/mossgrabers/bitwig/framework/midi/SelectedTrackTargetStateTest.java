// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.midi;

import static de.mossgrabers.pull.shell.testing.TestProxies.proxy;
import static de.mossgrabers.pull.shell.testing.TestProxies.relaxedProxy;
import static de.mossgrabers.pull.shell.testing.TestProxies.relaxedValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.bitwig.extension.controller.api.BooleanValue;
import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.IntegerValue;
import com.bitwig.extension.controller.api.NoteInput;
import com.bitwig.extension.controller.api.Parameter;
import com.bitwig.extension.controller.api.PlayingNote;
import com.bitwig.extension.controller.api.PlayingNoteArrayValue;
import com.bitwig.extension.controller.api.SettableBooleanValue;
import com.bitwig.extension.controller.api.SettableColorValue;
import com.bitwig.extension.controller.api.SettableEnumValue;
import com.bitwig.extension.controller.api.SettableRangedValue;
import com.bitwig.extension.controller.api.SettableStringValue;
import com.bitwig.extension.controller.api.SoloValue;
import com.bitwig.extension.controller.api.StringValue;
import com.bitwig.extension.callback.ObjectValueChangedCallback;

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
        final AtomicInteger immediateStopCalls = new AtomicInteger ();
        final AtomicInteger returnCalls = new AtomicInteger ();

        final PlayingNoteArrayValue playingNotes = proxy (PlayingNoteArrayValue.class, (proxy, method, arguments) -> {
            if ("get".equals (method.getName ()))
                return notes.get ();
            if ("addValueObserver".equals (method.getName ()))
            {
                @SuppressWarnings("unchecked")
                final ObjectValueChangedCallback<PlayingNote []> observer = (ObjectValueChangedCallback<PlayingNote []>) arguments[0];
                notesObserver.set (observer);
                observer.valueChanged (notes.get ());
            }
            return relaxedValue (method.getReturnType ());
        });
        final SettableRangedValue volumeValue = rangedValue (volume);
        final SettableRangedValue panValue = rangedValue (pan);
        final var emptyTree = new de.mossgrabers.bitwig.framework.daw.DrumTreeHostFake ();
        final CursorTrack target = proxy (CursorTrack.class, (proxy, method, arguments) -> {
            return switch (method.getName ())
            {
                case "channelId" -> stringValue (targetID);
                case "exists" -> booleanValue (BooleanValue.class, targetExists);
                case "name" -> settableStringValue (name);
                case "color" -> colorValue (0.1F, 0.2F, 0.3F);
                case "trackType" -> stringValue (type);
                case "position" -> integerValue (position);
                case "canHoldNoteData" -> booleanValue (SettableBooleanValue.class, canHoldNotes);
                case "canHoldAudioData" -> booleanValue (SettableBooleanValue.class, canHoldAudio);
                case "isGroup" -> booleanValue (BooleanValue.class, group);
                case "isGroupExpanded" -> booleanValue (SettableBooleanValue.class, expanded);
                case "isActivated" -> booleanValue (SettableBooleanValue.class, activated);
                case "arm" -> booleanValue (SettableBooleanValue.class, armed);
                case "monitorMode" -> enumValue (monitorMode);
                case "mute" -> booleanValue (SettableBooleanValue.class, muted);
                case "solo" -> booleanValue (SoloValue.class, soloed);
                case "isMutedBySolo" -> booleanValue (BooleanValue.class, mutedBySolo);
                case "isStopped" -> booleanValue (BooleanValue.class, stopped);
                case "volume" -> parameter (volumeValue);
                case "pan" -> parameter (panValue);
                case "playingNotes" -> playingNotes;
                case "createDeviceBank", "createCursorDevice" -> method.invoke (emptyTree.track, arguments);
                case "stop" -> {
                    stopCalls.incrementAndGet ();
                    yield null;
                }
                case "stopAlt" -> {
                    immediateStopCalls.incrementAndGet ();
                    yield null;
                }
                case "returnToArrangement" -> {
                    returnCalls.incrementAndGet ();
                    yield null;
                }
                default -> relaxedValue (method.getReturnType ());
            };
        });
        final ControllerHost host = relaxedProxy (ControllerHost.class);
        final SelectedTrackTargetState state = new SelectedTrackTargetState (new de.mossgrabers.bitwig.framework.daw.HostImpl (host), target, new NoteInputImpl (relaxedProxy (NoteInput.class)));

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
        state.stopImmediately ();
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
        assertEquals (1, immediateStopCalls.get ());
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
    void derivesDrumApplicabilityFromThePrivateSelectedTrackTree ()
    {
        final var host = new de.mossgrabers.bitwig.framework.daw.DrumTreeHostFake ();
        host.root.add (de.mossgrabers.bitwig.framework.daw.DrumTreeHostFake.container ("Layer",
            de.mossgrabers.bitwig.framework.daw.DrumTreeHostFake.effect ("Empty first branch"),
            de.mossgrabers.bitwig.framework.daw.DrumTreeHostFake.drum ("Second branch")));
        final SelectedTrackTargetState state = new SelectedTrackTargetState (
            new de.mossgrabers.bitwig.framework.daw.HostImpl (relaxedProxy (ControllerHost.class)), host.track,
            new NoteInputImpl (relaxedProxy (NoteInput.class)));
        assertFalse (state.hasDrumDevice ());
        for (int i = 0; i < 30; i++) { host.advance (); state.hasDrumDevice (); }
        assertTrue (state.hasDrumDevice ());
        host.trackId = "track-b";
        host.root.clear ();
        host.publish ();
        assertFalse (state.hasDrumDevice ());
        assertEquals (0, host.editorSelections);
    }


    private static StringValue stringValue (final AtomicReference<String> value)
    {
        return proxy (StringValue.class, (proxy, method, arguments) -> {
            if ("get".equals (method.getName ()))
                return value.get ();
            return relaxedValue (method.getReturnType ());
        });
    }


    private static SettableStringValue settableStringValue (final AtomicReference<String> value)
    {
        return proxy (SettableStringValue.class, (proxy, method, arguments) -> {
            if ("get".equals (method.getName ()))
                return value.get ();
            if ("set".equals (method.getName ()))
            {
                value.set ((String) arguments[0]);
                return null;
            }
            return relaxedValue (method.getReturnType ());
        });
    }


    private static IntegerValue integerValue (final AtomicInteger value)
    {
        return proxy (IntegerValue.class, (proxy, method, arguments) -> {
            if ("get".equals (method.getName ()) || "getAsInt".equals (method.getName ()))
                return Integer.valueOf (value.get ());
            return relaxedValue (method.getReturnType ());
        });
    }


    private static SettableColorValue colorValue (final float red, final float green, final float blue)
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
            return relaxedValue (method.getReturnType ());
        });
    }


    private static SettableEnumValue enumValue (final AtomicReference<String> value)
    {
        return proxy (SettableEnumValue.class, (proxy, method, arguments) -> {
            if ("get".equals (method.getName ()))
                return value.get ();
            if ("set".equals (method.getName ()))
            {
                value.set ((String) arguments[0]);
                return null;
            }
            return relaxedValue (method.getReturnType ());
        });
    }


    private static SettableRangedValue rangedValue (final AtomicReference<Double> value)
    {
        return proxy (SettableRangedValue.class, (proxy, method, arguments) -> {
            if ("get".equals (method.getName ()) || "getAsDouble".equals (method.getName ()))
                return value.get ();
            if ("set".equals (method.getName ()) && arguments.length == 1)
            {
                value.set (Double.valueOf (((Number) arguments[0]).doubleValue ()));
                return null;
            }
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


    private static <T> T booleanValue (final Class<T> type, final AtomicBoolean value)
    {
        return proxy (type, (proxy, method, arguments) -> {
            if ("get".equals (method.getName ()) || "getAsBoolean".equals (method.getName ()))
                return Boolean.valueOf (value.get ());
            if ("set".equals (method.getName ()))
            {
                value.set (((Boolean) arguments[0]).booleanValue ());
                return null;
            }
            return relaxedValue (method.getReturnType ());
        });
    }


}
