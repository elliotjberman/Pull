// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.runtime;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.controller.ableton.push.parameterprovider.PushVolumeParameter;
import de.mossgrabers.framework.controller.ContinuousID;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwLight;
import de.mossgrabers.framework.controller.hardware.IHwRelativeKnob;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.IProject;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.data.ICursorDevice;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.IMasterTrack;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.IParameterBank;
import de.mossgrabers.framework.daw.data.bank.IParameterPageBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.parameter.IParameter;
import de.mossgrabers.pull.core.api.DesiredParameterInteraction;
import de.mossgrabers.pull.core.api.DesiredParameterBanks;
import de.mossgrabers.pull.core.api.ParameterBankId;
import de.mossgrabers.pull.core.api.ParameterSlot;
import de.mossgrabers.pull.core.api.ParameterTargetRef;
import de.mossgrabers.pull.core.api.ParameterTargetSnapshot;
import de.mossgrabers.pull.core.api.effect.AdjustParameterValueEffect;
import de.mossgrabers.pull.core.api.effect.ResetParameterEffect;
import de.mossgrabers.pull.core.api.effect.SetParameterValueEffect;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * Exact-actuator boundary tests for rebindable parameter proxy slots.
 */
class ParameterTargetHostTest
{
    @Test
    void rejectsLeaseWhenBitwigRebindsTheSameWrapperToAnotherDevice ()
    {
        final MutableParameter parameter = new MutableParameter (64);
        final MutableRemoteDevice device = new MutableRemoteDevice (parameter.proxy ());
        final MutableContinuous continuous = new MutableContinuous ();
        final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        final PushControlSurface surface = createSurface (continuous, valueChanger);
        final IHwRelativeKnob knob = surface.createRelativeKnob (ContinuousID.KNOB1, "Knob 1");
        knob.bind (device.parameter);

        final ParameterTargetHost host = new ParameterTargetHost (surface, model (device, valueChanger), silentLog ());
        final DesiredParameterBanks banks = new DesiredParameterBanks (Set.of (ParameterBankId.ACTIVE));
        host.refresh (banks);
        final ParameterTargetRef original = host.snapshot ().slots ().get (ParameterSlot.active (0)).target ();
        final DesiredParameterInteraction interaction = new DesiredParameterInteraction (1, false, Map.of (original, 64.0), Set.of (), Set.of (), 0);
        final Map<ParameterTargetRef, ParameterTargetHost.RetainedTarget> leases = host.prepareLeases (interaction, banks);
        host.applyLeases (leases, banks);
        final ParameterTargetHost.PreparedSet restore = host.prepare (new SetParameterValueEffect (original, 64), leases);

        device.id = "device-b";
        host.refresh (banks);
        final ParameterTargetRef rebound = host.snapshot ().slots ().get (ParameterSlot.active (0)).target ();

        assertNotEquals (original, rebound);
        assertThrows (IllegalStateException.class, () -> host.apply (restore));
        assertEquals (0, parameter.writeCount);
    }


    @Test
    void projectBankIsIndependentOfHardwareBindingAndCandidatePreparationIsTransactional ()
    {
        final MutableParameter projectParameter = new MutableParameter (64);
        final MutableParameter deviceParameter = new MutableParameter (32);
        final MutableRemoteDevice device = new MutableRemoteDevice (deviceParameter.proxy ());
        final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        final AtomicInteger projectPage = new AtomicInteger ();
        final ParameterTargetHost host = new ParameterTargetHost (
            createSurface (new MutableContinuous (), valueChanger),
            model (device, valueChanger, projectParameter.proxy (), projectPage),
            silentLog ());
        final DesiredParameterBanks projectBank = new DesiredParameterBanks (Set.of (ParameterBankId.PROJECT_REMOTE));
        host.refresh (projectBank);

        final ParameterTargetSnapshot snapshot = host.snapshot ().slots ().get (ParameterSlot.projectRemote (0));
        assertEquals ("Cutoff", snapshot.name ());
        assertEquals ("64 units", snapshot.displayedValue ());
        assertEquals (128, snapshot.numberOfSteps ());

        host.prepareLeases (
            DesiredParameterInteraction.empty (),
            new DesiredParameterBanks (Set.of (ParameterBankId.SELECTED_DEVICE_REMOTE)));

        final ParameterTargetHost.PreparedAdjust adjust = host.prepare (new AdjustParameterValueEffect (snapshot.target (), 3));
        host.apply (adjust);
        final ParameterTargetHost.PreparedReset reset = host.prepare (new ResetParameterEffect (snapshot.target ()));
        host.apply (reset);

        assertEquals (67, projectParameter.value);
        assertEquals (1, projectParameter.incrementCount);
        assertEquals (1, projectParameter.resetCount);
        assertEquals (ParameterSlot.projectRemote (0), host.snapshot ().slots ().keySet ().iterator ().next ());

        final ParameterTargetHost.PreparedAdjust stale = host.prepare (new AdjustParameterValueEffect (snapshot.target (), 1));
        projectPage.incrementAndGet ();
        host.refresh (projectBank);
        assertNotEquals (snapshot.target (), host.snapshot ().slots ().get (ParameterSlot.projectRemote (0)).target ());
        assertThrows (IllegalStateException.class, () -> host.apply (stale));
        assertEquals (1, projectParameter.incrementCount);
    }


    @Test
    void rejectsLeaseCommitWhenTheProjectPageRebindsAfterPreparation ()
    {
        final MutableParameter projectParameter = new MutableParameter (64);
        final MutableRemoteDevice device = new MutableRemoteDevice (new MutableParameter (32).proxy ());
        final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        final AtomicInteger projectPage = new AtomicInteger ();
        final ParameterTargetHost host = new ParameterTargetHost (
            createSurface (new MutableContinuous (), valueChanger),
            model (device, valueChanger, projectParameter.proxy (), projectPage),
            silentLog ());
        final DesiredParameterBanks banks = new DesiredParameterBanks (Set.of (ParameterBankId.PROJECT_REMOTE));
        host.refresh (banks);
        final ParameterTargetRef original = host.snapshot ().slots ().get (ParameterSlot.projectRemote (0)).target ();
        final DesiredParameterInteraction interaction = new DesiredParameterInteraction (1, false, Map.of (original, 64.0), Set.of (), Set.of (), 0);
        final Map<ParameterTargetRef, ParameterTargetHost.RetainedTarget> prepared = host.prepareLeases (interaction, banks);

        projectPage.incrementAndGet ();

        assertThrows (IllegalStateException.class, () -> host.applyLeases (prepared, banks));
        assertTrue (host.snapshot ().retainedBaselines ().isEmpty ());
        assertEquals (0, projectParameter.writeCount);
    }


    @Test
    void masterAndCueTargetsAreFencedToTheObservedProjectTab ()
    {
        final MutableParameter volume = new MutableParameter (64);
        final MutableParameter pan = new MutableParameter (32);
        final MutableParameter cueVolume = new MutableParameter (48);
        final MutableParameter cueMix = new MutableParameter (16);
        final IParameter volumeParameter = volume.proxy ();
        final IParameter panParameter = pan.proxy ();
        final IParameter cueVolumeParameter = cueVolume.proxy ();
        final IParameter cueMixParameter = cueMix.proxy ();
        final AtomicReference<String> projectIdentity = new AtomicReference<> ("project-a");
        final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        final ITransport transport = relaxedProxy (ITransport.class);
        final IProject project = proxy (IProject.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getIdentity" -> projectIdentity.get ();
            case "getCueVolumeParameter" -> cueVolumeParameter;
            case "getCueMixParameter" -> cueMixParameter;
            default -> relaxedValue (method.getReturnType ());
        });
        final IMasterTrack master = proxy (IMasterTrack.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getVolumeParameter" -> volumeParameter;
            case "getPanParameter" -> panParameter;
            default -> relaxedValue (method.getReturnType ());
        });
        final IModel model = proxy (IModel.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getTransport" -> transport;
            case "getProject" -> project;
            case "getMasterTrack" -> master;
            case "getValueChanger" -> valueChanger;
            default -> relaxedValue (method.getReturnType ());
        });
        final ParameterTargetHost host = new ParameterTargetHost (createSurface (new MutableContinuous (), valueChanger), model, silentLog ());
        final DesiredParameterBanks banks = new DesiredParameterBanks (Set.of (ParameterBankId.MASTER));

        host.refresh (banks);
        final ParameterTargetRef original = host.snapshot ().slots ().get (ParameterSlot.MASTER_MIX_VOLUME).target ();
        final ParameterTargetHost.PreparedAdjust stale = host.prepare (new AdjustParameterValueEffect (original, 3));

        projectIdentity.set ("project-b");
        assertThrows (IllegalStateException.class, () -> host.apply (stale));
        host.refresh (banks);
        final ParameterTargetRef rebound = host.snapshot ().slots ().get (ParameterSlot.MASTER_MIX_VOLUME).target ();
        assertNotEquals (original, rebound);

        host.apply (host.prepare (new AdjustParameterValueEffect (rebound, 2)));
        assertEquals (66, volume.value);
        assertEquals (1, volume.incrementCount);
    }


    @Test
    void selectedTrackMixKnobFailsClosedUntilItsBindingMatchesTheDisplayedTrack ()
    {
        final MutableParameter cursorVolume = new MutableParameter (64);
        final MutableParameter selectedVolume = new MutableParameter (64);
        final MutableParameter staleVolume = new MutableParameter (32);
        final IParameter cursorParameter = cursorVolume.proxy ();
        final IParameter selectedParameter = selectedVolume.proxy ();
        final IParameter staleParameter = staleVolume.proxy ();
        final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        final MutableContinuous continuous = new MutableContinuous ();
        final PushControlSurface surface = createSurface (continuous, valueChanger);
        final IHwRelativeKnob knob = surface.createRelativeKnob (ContinuousID.KNOB1, "Knob 1");
        surface.getModeManager ().register (Modes.TRACK, relaxedProxy (IMode.class));
        surface.getModeManager ().setDefaultID (Modes.TRACK);
        surface.getModeManager ().setActive (Modes.TRACK);

        final ICursorTrack selectedTrack = proxy (ICursorTrack.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "doesExist" -> Boolean.TRUE;
            case "getChannelID" -> "selected-track";
            case "getVolumeParameter" -> cursorParameter;
            default -> relaxedValue (method.getReturnType ());
        });
        final ITrack selectedBankTrack = proxy (ITrack.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "doesExist" -> Boolean.TRUE;
            case "getChannelID" -> "selected-track";
            case "getVolumeParameter" -> selectedParameter;
            default -> relaxedValue (method.getReturnType ());
        });
        final ITrack staleTrack = proxy (ITrack.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "doesExist" -> Boolean.TRUE;
            case "getChannelID" -> "stale-track";
            case "getVolumeParameter" -> staleParameter;
            default -> relaxedValue (method.getReturnType ());
        });
        final ITrackBank tracks = proxy (ITrackBank.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getPageSize" -> Integer.valueOf (2);
            case "getItem" -> ((Integer) arguments[0]).intValue () == 0 ? selectedBankTrack : staleTrack;
            case "getSelectedItem" -> Optional.of (selectedBankTrack);
            default -> relaxedValue (method.getReturnType ());
        });
        final IModel model = proxy (IModel.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getTransport" -> relaxedProxy (ITransport.class);
            case "getCursorTrack" -> selectedTrack;
            case "getCurrentTrackBank", "getTrackBank" -> tracks;
            case "getValueChanger" -> valueChanger;
            default -> relaxedValue (method.getReturnType ());
        });
        final ParameterTargetHost host = new ParameterTargetHost (surface, model, silentLog ());
        final DesiredParameterBanks banks = new DesiredParameterBanks (Set.of (ParameterBankId.ACTIVE));

        knob.bind (staleParameter);
        host.refresh (banks);
        assertNull (host.resolveMutation (knob));
        assertTrue (host.requiresResolvedMutation (knob));

        knob.bind (new PushVolumeParameter (selectedParameter, valueChanger));
        host.refresh (banks);
        assertNotNull (host.resolveMutation (knob));
        assertNotNull (host.snapshot ().slots ().get (ParameterSlot.active (0)));
        assertTrue (host.requiresResolvedMutation (knob));
    }


    private static IModel model (final MutableRemoteDevice device, final IValueChanger valueChanger)
    {
        return model (device, valueChanger, null);
    }


    private static IModel model (final MutableRemoteDevice device, final IValueChanger valueChanger, final IParameter projectParameter)
    {
        return model (device, valueChanger, projectParameter, new AtomicInteger ());
    }


    private static IModel model (final MutableRemoteDevice device, final IValueChanger valueChanger, final IParameter projectParameter, final AtomicInteger projectPage)
    {
        final ITransport transport = proxy (ITransport.class, (proxy, method, arguments) -> "getTempo".equals (method.getName ()) ? Double.valueOf (120) : relaxedValue (method.getReturnType ()));
        final IParameterBank emptyParameters = parameterBank (null);
        final IParameterBank projectParameters = parameterBank (projectParameter, projectPage);
        final IProject project = proxy (IProject.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getName" -> "test-project";
            case "getParameterBank" -> projectParameters;
            default -> relaxedValue (method.getReturnType ());
        });
        final ICursorTrack cursorTrack = proxy (ICursorTrack.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "doesExist" -> Boolean.TRUE;
            case "getChannelID" -> "track-a";
            case "getParameterBank" -> emptyParameters;
            default -> relaxedValue (method.getReturnType ());
        });
        return proxy (IModel.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getTransport" -> transport;
            case "getProject" -> project;
            case "getCursorTrack" -> cursorTrack;
            case "getCursorDevice" -> device.proxy ();
            case "getValueChanger" -> valueChanger;
            default -> relaxedValue (method.getReturnType ());
        });
    }


    private static IParameterBank parameterBank (final IParameter parameter)
    {
        return parameterBank (parameter, new AtomicInteger ());
    }


    private static IParameterBank parameterBank (final IParameter parameter, final AtomicInteger page)
    {
        final IParameterPageBank pages = proxy (IParameterPageBank.class, (proxy, method, arguments) -> "getSelectedItemPosition".equals (method.getName ()) ? Integer.valueOf (page.get ()) : relaxedValue (method.getReturnType ()));
        return proxy (IParameterBank.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getPageSize" -> Integer.valueOf (parameter == null ? 0 : 1);
            case "getItem" -> parameter;
            case "getPageBank" -> pages;
            default -> relaxedValue (method.getReturnType ());
        });
    }


    private static PushControlSurface createSurface (final MutableContinuous continuous, final IValueChanger valueChanger)
    {
        final IHwButton button = relaxedProxy (IHwButton.class);
        final IHwLight light = relaxedProxy (IHwLight.class);
        final IHwSurfaceFactory factory = proxy (IHwSurfaceFactory.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "createButton" -> button;
            case "createLight" -> light;
            case "createRelativeKnob" -> continuous.proxy ();
            default -> relaxedValue (method.getReturnType ());
        });
        final IHost host = proxy (IHost.class, (proxy, method, arguments) -> "createSurfaceFactory".equals (method.getName ()) ? factory : relaxedValue (method.getReturnType ()));
        return new PushControlSurface (
            host,
            new PushColorManager (),
            new PushConfiguration (host, valueChanger, List.of ()),
            relaxedProxy (IMidiOutput.class),
            relaxedProxy (IMidiInput.class),
            relaxedProxy (ISelectedTrackNoteTarget.class),
            relaxedProxy (ITrack.class),
            () -> false,
            null);
    }


    private static RuntimeLog silentLog ()
    {
        return new RuntimeLog ()
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
        };
    }


    private static final class MutableRemoteDevice
    {
        private final IParameter parameter;
        private final IParameterBank parameters;
        private String id = "device-a";


        private MutableRemoteDevice (final IParameter parameter)
        {
            this.parameter = parameter;
            this.parameters = parameterBank (parameter);
        }


        private ICursorDevice proxy ()
        {
            return ParameterTargetHostTest.proxy (ICursorDevice.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "doesExist" -> Boolean.TRUE;
                case "getID" -> this.id;
                case "getParameterBank" -> this.parameters;
                default -> relaxedValue (method.getReturnType ());
            });
        }
    }


    private static final class MutableParameter
    {
        private int value;
        private int writeCount;
        private int incrementCount;
        private int resetCount;


        private MutableParameter (final int value)
        {
            this.value = value;
        }


        private IParameter proxy ()
        {
            return ParameterTargetHostTest.proxy (IParameter.class, (proxy, method, arguments) -> switch (method.getName ())
            {
                case "doesExist" -> Boolean.TRUE;
                case "getName" -> "Cutoff";
                case "getPosition", "getIndex" -> Integer.valueOf (0);
                case "getValue" -> Integer.valueOf (this.value);
                case "getModulatedValue" -> Integer.valueOf (this.value + 1);
                case "getDisplayedValue" -> this.value + " units";
                case "getNumberOfSteps" -> Integer.valueOf (128);
                case "inc" -> {
                    this.value += (int) Math.round (((Number) arguments[0]).doubleValue ());
                    this.incrementCount++;
                    yield null;
                }
                case "resetValue" -> {
                    this.resetCount++;
                    yield null;
                }
                case "setValueImmediatly" -> {
                    this.value = ((Number) arguments[0]).intValue ();
                    this.writeCount++;
                    yield null;
                }
                default -> relaxedValue (method.getReturnType ());
            });
        }
    }


    private static final class MutableContinuous
    {
        private IParameter parameter;
        private long generation;


        private IHwRelativeKnob proxy ()
        {
            return ParameterTargetHostTest.proxy (IHwRelativeKnob.class, (proxy, method, arguments) -> {
                if ("bind".equals (method.getName ()) && arguments != null && arguments.length == 1 && (arguments[0] == null || arguments[0] instanceof IParameter))
                {
                    this.parameter = (IParameter) arguments[0];
                    this.generation++;
                    return null;
                }
                return switch (method.getName ())
                {
                    case "getBoundParameter" -> this.parameter;
                    case "getBindingGeneration" -> Long.valueOf (this.generation);
                    default -> relaxedValue (method.getReturnType ());
                };
            });
        }
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
}
