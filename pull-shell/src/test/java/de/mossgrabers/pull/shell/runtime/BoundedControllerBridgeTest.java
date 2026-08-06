// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwLight;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.IDrumDevice;
import de.mossgrabers.framework.daw.data.IDrumPad;
import de.mossgrabers.framework.daw.data.ISlot;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.IDrumPadBank;
import de.mossgrabers.framework.daw.data.bank.ISlotBank;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.daw.midi.SelectedTrackMonitorMode;
import de.mossgrabers.framework.daw.midi.SelectedTrackNoteTargetSnapshot;
import de.mossgrabers.pull.core.api.BridgeSubscription;
import de.mossgrabers.pull.core.api.ControllerBridgeSnapshot;
import de.mossgrabers.pull.core.api.DesiredBridgeSubscriptions;
import de.mossgrabers.pull.core.api.DesiredParameterBanks;
import de.mossgrabers.pull.core.api.DesiredParameterInteraction;
import de.mossgrabers.pull.core.api.DrumContextSnapshot;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.effect.SelectDrumPadEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackAction;
import de.mossgrabers.pull.core.api.effect.SelectedTrackActionEffect;
import de.mossgrabers.pull.core.api.effect.SelectedTrackBoolean;
import de.mossgrabers.pull.core.api.effect.SendNoteInputMidiEffect;
import de.mossgrabers.pull.core.api.effect.SetSelectedTrackBooleanEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.SetTransportStateEffect;
import de.mossgrabers.pull.core.api.effect.TransportState;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Safety-boundary tests for the permanent bounded controller bridge.
 */
class BoundedControllerBridgeTest
{
    @Test
    void publishesOnlyRequestedDomainsAndClearsThemWhenUnsubscribed ()
    {
        final BridgeFixture fixture = new BridgeFixture ();

        assertFalse (fixture.bridge.refresh (1, DesiredBridgeSubscriptions.empty (), DesiredParameterBanks.empty ()));
        assertEquals (ControllerBridgeSnapshot.empty (), fixture.bridge.snapshot ());
        assertEquals (0, fixture.selected.snapshotCount);
        assertEquals (0, fixture.transport.snapshotReadCount);

        assertTrue (fixture.bridge.refresh (2, subscriptions (BridgeSubscription.TRANSPORT, BridgeSubscription.SELECTED_TRACK), DesiredParameterBanks.empty ()));
        assertTrue (fixture.bridge.snapshot ().transport ().available ());
        assertTrue (fixture.bridge.snapshot ().selectedTrack ().exists ());
        assertEquals (1, fixture.selected.snapshotCount);
        assertTrue (fixture.transport.snapshotReadCount > 0);

        final int transportReads = fixture.transport.snapshotReadCount;
        assertTrue (fixture.bridge.refresh (3, DesiredBridgeSubscriptions.empty (), DesiredParameterBanks.empty ()));
        assertEquals (ControllerBridgeSnapshot.empty (), fixture.bridge.snapshot ());
        assertEquals (1, fixture.selected.snapshotCount);
        assertEquals (transportReads, fixture.transport.snapshotReadCount);
    }


    @Test
    void appliesRecordingAndArrangerOverdubAsAbsoluteStates ()
    {
        final BridgeFixture fixture = new BridgeFixture ();

        fixture.bridge.apply (fixture.bridge.prepare (new SetTransportStateEffect (TransportState.RECORDING, true)));
        fixture.bridge.apply (fixture.bridge.prepare (new SetTransportStateEffect (TransportState.ARRANGER_OVERDUB, true)));
        fixture.bridge.apply (fixture.bridge.prepare (new SetTransportStateEffect (TransportState.RECORDING, false)));
        fixture.bridge.apply (fixture.bridge.prepare (new SetTransportStateEffect (TransportState.ARRANGER_OVERDUB, false)));

        assertEquals (List.of (
            "setRecording:true",
            "setArrangerOverdub:true",
            "setRecording:false",
            "setArrangerOverdub:false"), fixture.transport.writes);
        assertFalse (fixture.transport.recording);
        assertFalse (fixture.transport.arrangerOverdub);
    }


    @Test
    void commitsExactParameterLeasesIntoTheImmediateHotReloadSnapshot ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        final DesiredParameterBanks parameterBanks = new DesiredParameterBanks (Set.of (ParameterBankId.GLOBAL));
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.PARAMETERS), parameterBanks);
        final ParameterTargetRef tempo = fixture.bridge.snapshot ().parameters ().slots ().get (ParameterSlot.TEMPO).target ();
        final DesiredParameterInteraction interaction = new DesiredParameterInteraction (1, false, Map.of (tempo, 120.0), Set.of (), Set.of (), 0);
        final Map<ParameterTargetRef, ControllerBridge.ParameterLease> prepared = fixture.bridge.prepareParameterLeases (interaction, parameterBanks);
        final ControllerBridge.PreparedAction restore = fixture.bridge.prepare (new SetParameterValueEffect (tempo, 98), prepared);

        assertTrue (fixture.bridge.applyParameterLeases (prepared, parameterBanks));
        assertEquals (Map.of (tempo, 120.0), fixture.bridge.snapshot ().parameters ().retainedBaselines ());
        fixture.bridge.apply (restore);
        assertEquals (98, fixture.transport.tempo);
    }


    @Test
    void rejectsPreparedSelectedTrackActionAfterTargetHandoff ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.SELECTED_TRACK), DesiredParameterBanks.empty ());
        final ControllerBridge.PreparedAction prepared = fixture.bridge.prepare (
            new SetSelectedTrackBooleanEffect (1, "track-a", SelectedTrackBoolean.RECORD_ARMED, true));

        fixture.selected.switchTo (2, "track-b");
        fixture.bridge.apply (prepared);

        assertEquals (0, fixture.selected.armedWriteCount);
    }


    @Test
    void createsANewClipThroughTheDisplayIndependentSelectedTrackAction ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.SELECTED_TRACK), DesiredParameterBanks.empty ());

        fixture.bridge.apply (fixture.bridge.prepare (
            new SelectedTrackActionEffect (1, "track-a", SelectedTrackAction.CREATE_NEW_CLIP)));

        assertEquals (1, fixture.newClipCount);
    }


    @Test
    void rechecksDrumDeviceBankAndPadIdentityAtApplyTime ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.DRUM_PADS), DesiredParameterBanks.empty ());
        final DrumContextSnapshot drum = fixture.bridge.snapshot ().drum ();
        final ControllerBridge.PreparedAction prepared = fixture.bridge.prepare (
            new SelectDrumPadEffect (drum.generation (), drum.targetChannelId (), 0));

        fixture.drum.deviceID = "device-b";
        fixture.bridge.apply (prepared);
        fixture.drum.deviceID = "device-a";
        fixture.drum.baseMidiNote = 48;
        fixture.bridge.apply (prepared);
        fixture.drum.baseMidiNote = 36;
        fixture.drum.padChannelID = "pad-b";
        fixture.bridge.apply (prepared);
        assertEquals (0, fixture.drum.selectionCount);

        fixture.drum.padChannelID = "pad-a";
        fixture.bridge.apply (prepared);
        assertEquals (1, fixture.drum.selectionCount);
    }


    @Test
    void neutralizesEveryStatefulMidiFamilyOnCoreHandoff ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.SELECTED_TRACK), DesiredParameterBanks.empty ());
        fixture.bridge.activateCoreGeneration (1);

        applyMidi (fixture, 0xB3, 74, 99);
        applyMidi (fixture, 0xA3, 60, 75);
        applyMidi (fixture, 0xD4, 80, 17);
        applyMidi (fixture, 0xE2, 5, 100);
        fixture.bridge.activateCoreGeneration (2);

        assertEquals (8, fixture.noteInputMidiMessages.size ());
        assertEquals (Set.of (
            new MidiMessage (0xB3, 74, 0),
            new MidiMessage (0xA3, 60, 0),
            new MidiMessage (0xD4, 0, 0),
            new MidiMessage (0xE2, 0, 64)),
            new HashSet<> (fixture.noteInputMidiMessages.subList (4, 8)));
    }


    @Test
    void neutralizesStatefulMidiWhenTheSelectedTargetChanges ()
    {
        final BridgeFixture fixture = new BridgeFixture ();
        fixture.bridge.refresh (1, subscriptions (BridgeSubscription.SELECTED_TRACK), DesiredParameterBanks.empty ());
        applyMidi (fixture, 0xB1, 1, 127);

        fixture.selected.switchTo (2, "track-b");
        fixture.bridge.refresh (2, DesiredBridgeSubscriptions.empty (), DesiredParameterBanks.empty ());

        assertEquals (List.of (
            new MidiMessage (0xB1, 1, 127),
            new MidiMessage (0xB1, 1, 0)), fixture.noteInputMidiMessages);
    }


    private static void applyMidi (final BridgeFixture fixture, final int status, final int data1, final int data2)
    {
        fixture.bridge.apply (fixture.bridge.prepare (
            new SendNoteInputMidiEffect (status, data1, data2)));
    }


    private static DesiredBridgeSubscriptions subscriptions (final BridgeSubscription... subscriptions)
    {
        return new DesiredBridgeSubscriptions (Set.of (subscriptions));
    }


    private static final class BridgeFixture
    {
        private final MutableSelectedTarget selected = new MutableSelectedTarget ();
        private final MutableTransport transport = new MutableTransport ();
        private final MutableDrum drum = new MutableDrum (this.selected);
        private final List<MidiMessage> noteInputMidiMessages = new ArrayList<> ();
        private final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        private final BoundedControllerBridge bridge;
        private int newClipCount;


        private BridgeFixture ()
        {
            final ITransport transportProxy = this.transport.proxy ();
            final ICursorTrack cursorTrack = this.drum.cursorTrack ();
            final IDrumDevice drumDevice = this.drum.device ();
            final IModel model = proxy (IModel.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "getTransport" -> transportProxy;
                case "getCursorTrack" -> cursorTrack;
                case "getDrumDevice" -> drumDevice;
                case "getValueChanger" -> this.valueChanger;
                case "createNoteClip" -> {
                    this.newClipCount++;
                    yield null;
                }
                default -> relaxedValue (method.getReturnType ());
            });
            final PushControlSurface surface = createSurface (this.selected, cursorTrack, this.valueChanger);
            this.bridge = new BoundedControllerBridge (
                model,
                this.selected,
                (status, data1, data2) -> this.noteInputMidiMessages.add (new MidiMessage (status, data1, data2)),
                surface,
                this.valueChanger,
                new RuntimeLog ()
                {
                    @Override
                    public void info (final String message)
                    {
                        // No test diagnostics.
                    }


                    @Override
                    public void warn (final String message)
                    {
                        // No test diagnostics.
                    }
                });
        }
    }


    private static final class MutableTransport
    {
        private final List<String> writes = new ArrayList<> ();
        private boolean recording;
        private boolean arrangerOverdub;
        private double tempo = 120;
        private int snapshotReadCount;


        private ITransport proxy ()
        {
            return BoundedControllerBridgeTest.proxy (ITransport.class, (proxy, method, arguments) -> {
                switch (method.getName ())
                {
                    case "isPlaying":
                    case "isLauncherOverdub":
                    case "isLoop":
                    case "isMetronomeOn":
                    case "isFillModeActive":
                        this.snapshotReadCount++;
                        return Boolean.FALSE;
                    case "isRecording":
                        this.snapshotReadCount++;
                        return Boolean.valueOf (this.recording);
                    case "isArrangerOverdub":
                        this.snapshotReadCount++;
                        return Boolean.valueOf (this.arrangerOverdub);
                    case "getTempo":
                        this.snapshotReadCount++;
                        return Double.valueOf (this.tempo);
                    case "getPosition":
                        this.snapshotReadCount++;
                        return Double.valueOf (16.0);
                    case "getNumerator":
                        this.snapshotReadCount++;
                        return Integer.valueOf (4);
                    case "getDenominator":
                        this.snapshotReadCount++;
                        return Integer.valueOf (4);
                    case "getMinimumTempo":
                        return Double.valueOf (20.0);
                    case "getMaximumTempo":
                        return Double.valueOf (666.0);
                    case "setRecording":
                        this.recording = ((Boolean) arguments[0]).booleanValue ();
                        this.writes.add ("setRecording:" + this.recording);
                        return null;
                    case "setArrangerOverdub":
                        this.arrangerOverdub = ((Boolean) arguments[0]).booleanValue ();
                        this.writes.add ("setArrangerOverdub:" + this.arrangerOverdub);
                        return null;
                    case "setTempo":
                        this.tempo = ((Number) arguments[0]).doubleValue ();
                        return null;
                    case "toggleRecording":
                    case "toggleOverdub":
                        this.writes.add (method.getName ());
                        return null;
                    default:
                        return relaxedValue (method.getReturnType ());
                }
            });
        }
    }


    private static final class MutableSelectedTarget implements ISelectedTrackNoteTarget
    {
        private long generation = 1;
        private String channelID = "track-a";
        private boolean armed;
        private int snapshotCount;
        private int armedWriteCount;


        @Override
        public SelectedTrackNoteTargetSnapshot snapshot ()
        {
            this.snapshotCount++;
            return new SelectedTrackNoteTargetSnapshot (
                this.generation,
                this.channelID,
                true,
                "Drums",
                0.8,
                0.2,
                0.1,
                "Instrument",
                2,
                true,
                false,
                false,
                false,
                true,
                this.armed,
                SelectedTrackMonitorMode.AUTO,
                false,
                false,
                false,
                false,
                true,
                0.75,
                0.5);
        }


        private void switchTo (final long newGeneration, final String newChannelID)
        {
            this.generation = newGeneration;
            this.channelID = newChannelID;
        }


        @Override
        public long getGeneration ()
        {
            return this.generation;
        }


        @Override
        public String getChannelID ()
        {
            return this.channelID;
        }


        @Override
        public boolean doesExist ()
        {
            return true;
        }


        @Override
        public boolean canHoldNotes ()
        {
            return true;
        }


        @Override
        public boolean hasDrumDevice ()
        {
            return true;
        }


        @Override
        public int getPlayingVelocity (final int note)
        {
            return 0;
        }


        @Override
        public void setActivated (final boolean activated)
        {
            // Not relevant to these safety tests.
        }


        @Override
        public void setGroupExpanded (final boolean expanded)
        {
            // Not relevant to these safety tests.
        }


        @Override
        public void setArmed (final boolean newArmed)
        {
            this.armedWriteCount++;
            this.armed = newArmed;
        }


        @Override
        public void setMonitorMode (final SelectedTrackMonitorMode mode)
        {
            // Not relevant to these safety tests.
        }


        @Override
        public void setMuted (final boolean muted)
        {
            // Not relevant to these safety tests.
        }


        @Override
        public void setSoloed (final boolean soloed)
        {
            // Not relevant to these safety tests.
        }


        @Override
        public void setVolume (final double normalizedVolume)
        {
            // Not relevant to these safety tests.
        }


        @Override
        public void setPan (final double normalizedPan)
        {
            // Not relevant to these safety tests.
        }


        @Override
        public void stop ()
        {
            // Not relevant to these safety tests.
        }


        @Override
        public void returnToArrangement ()
        {
            // Not relevant to these safety tests.
        }
    }


    private static final class MutableDrum
    {
        private final MutableSelectedTarget selected;
        private String deviceID = "device-a";
        private String padChannelID = "pad-a";
        private int baseMidiNote = 36;
        private int selectionCount;


        private MutableDrum (final MutableSelectedTarget selected)
        {
            this.selected = selected;
        }


        private ICursorTrack cursorTrack ()
        {
            final ISlot slot = relaxedProxy (ISlot.class);
            final ISlotBank slotBank = proxy (ISlotBank.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "getSelectedItem" -> java.util.Optional.empty ();
                case "getEmptySlot" -> java.util.Optional.of (slot);
                default -> relaxedValue (method.getReturnType ());
            });
            return proxy (ICursorTrack.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "doesExist" -> Boolean.TRUE;
                case "getChannelID" -> this.selected.channelID;
                case "getSlotBank" -> slotBank;
                default -> relaxedValue (method.getReturnType ());
            });
        }


        private IDrumDevice device ()
        {
            final IDrumPad pad = proxy (IDrumPad.class, (proxy, method, arguments) -> {
                switch (method.getName ())
                {
                    case "doesExist":
                    case "isActivated":
                    case "hasDevices":
                        return Boolean.TRUE;
                    case "isSelected":
                    case "isMute":
                    case "isSolo":
                        return Boolean.FALSE;
                    case "getChannelID":
                        return this.padChannelID;
                    case "getName":
                        return "Kick";
                    case "getColor":
                        return ColorEx.RED;
                    case "getVolume":
                    case "getPan":
                        return Integer.valueOf (64);
                    case "select":
                        this.selectionCount++;
                        return null;
                    default:
                        return relaxedValue (method.getReturnType ());
                }
            });
            final IDrumPadBank bank = proxy (IDrumPadBank.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "getPageSize" -> Integer.valueOf (1);
                case "getScrollPosition" -> Integer.valueOf (this.baseMidiNote);
                case "getItem" -> pad;
                default -> relaxedValue (method.getReturnType ());
            });
            return proxy (IDrumDevice.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "doesExist", "hasDrumPads" -> Boolean.TRUE;
                case "getID" -> this.deviceID;
                case "getDrumPadBank" -> bank;
                default -> relaxedValue (method.getReturnType ());
            });
        }
    }


    private static PushControlSurface createSurface (final ISelectedTrackNoteTarget selectedTarget, final ITrack drumModelTrack, final IValueChanger valueChanger)
    {
        final IHwButton button = relaxedProxy (IHwButton.class);
        final IHwLight light = relaxedProxy (IHwLight.class);
        final IHwSurfaceFactory surfaceFactory = proxy (IHwSurfaceFactory.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "createButton" -> button;
            case "createLight" -> light;
            default -> relaxedValue (method.getReturnType ());
        });
        final IHost host = proxy (IHost.class, (proxy, method, arguments) -> "createSurfaceFactory".equals (method.getName ()) ? surfaceFactory : relaxedValue (method.getReturnType ()));
        final IMidiInput input = relaxedProxy (IMidiInput.class);
        final IMidiOutput output = relaxedProxy (IMidiOutput.class);
        final PushConfiguration configuration = new PushConfiguration (host, valueChanger, List.of ());
        return new PushControlSurface (host, new PushColorManager (), configuration, output, input, selectedTarget, drumModelTrack, () -> true, null);
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


    private record MidiMessage (int status, int data1, int data2)
    {
    }
}
