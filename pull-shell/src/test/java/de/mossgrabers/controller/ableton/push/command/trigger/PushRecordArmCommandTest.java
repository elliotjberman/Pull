// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.command.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.mossgrabers.framework.configuration.Configuration;
import de.mossgrabers.framework.controller.ButtonID;
import de.mossgrabers.framework.controller.IControlSurface;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.data.bank.ISlotBank;
import de.mossgrabers.framework.daw.data.bank.ITrackBank;
import de.mossgrabers.framework.utils.ButtonEvent;

import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;


/**
 * Tests Push's selected-track record-arm behavior.
 */
class PushRecordArmCommandTest
{
    @Test
    void plainRecordPressArmsSelectedTrackWithoutTogglingArrangerRecord ()
    {
        final AtomicInteger armToggles = new AtomicInteger ();
        final AtomicInteger arrangerRecordToggles = new AtomicInteger ();
        final AtomicInteger launcherOverdubToggles = new AtomicInteger ();
        final TestRig rig = createRig (armToggles, arrangerRecordToggles, launcherOverdubToggles);

        rig.command ().execute (ButtonEvent.DOWN, 127);
        rig.command ().execute (ButtonEvent.UP, 0);

        assertEquals (1, armToggles.get ());
        assertEquals (0, arrangerRecordToggles.get ());
        assertEquals (0, launcherOverdubToggles.get ());
    }


    @Test
    void shiftRecordKeepsLauncherOverdubWithoutTogglingArrangerRecord ()
    {
        final AtomicInteger armToggles = new AtomicInteger ();
        final AtomicInteger arrangerRecordToggles = new AtomicInteger ();
        final AtomicInteger launcherOverdubToggles = new AtomicInteger ();
        final TestRig rig = createRig (armToggles, arrangerRecordToggles, launcherOverdubToggles);
        rig.shiftPressed ().set (true);

        rig.command ().execute (ButtonEvent.DOWN, 127);
        rig.command ().execute (ButtonEvent.UP, 0);

        assertEquals (0, armToggles.get ());
        assertEquals (0, arrangerRecordToggles.get ());
        assertEquals (1, launcherOverdubToggles.get ());
    }


    @SuppressWarnings("unchecked")
    private static TestRig createRig (final AtomicInteger armToggles, final AtomicInteger arrangerRecordToggles, final AtomicInteger launcherOverdubToggles)
    {
        final ISlotBank slotBank = proxy (ISlotBank.class, (proxy, method, arguments) -> {
            if ("getSelectedItem".equals (method.getName ()))
                return Optional.empty ();
            return defaultValue (method.getReturnType ());
        });
        final ITrack track = proxy (ITrack.class, (proxy, method, arguments) -> {
            if ("toggleRecArm".equals (method.getName ()))
                armToggles.incrementAndGet ();
            if ("getSlotBank".equals (method.getName ()))
                return slotBank;
            return defaultValue (method.getReturnType ());
        });
        final ITrackBank trackBank = proxy (ITrackBank.class, (proxy, method, arguments) -> {
            if ("getSelectedItem".equals (method.getName ()))
                return Optional.of (track);
            return defaultValue (method.getReturnType ());
        });
        final ITransport transport = proxy (ITransport.class, (proxy, method, arguments) -> {
            if ("toggleRecording".equals (method.getName ()))
                arrangerRecordToggles.incrementAndGet ();
            if ("toggleLauncherOverdub".equals (method.getName ()))
                launcherOverdubToggles.incrementAndGet ();
            return defaultValue (method.getReturnType ());
        });
        final IModel model = proxy (IModel.class, (proxy, method, arguments) -> {
            if ("getTransport".equals (method.getName ()))
                return transport;
            if ("getCurrentTrackBank".equals (method.getName ()))
                return trackBank;
            return defaultValue (method.getReturnType ());
        });
        final AtomicBoolean shiftPressed = new AtomicBoolean ();
        final Configuration configuration = proxy (Configuration.class, (proxy, method, arguments) -> defaultValue (method.getReturnType ()));
        final IControlSurface<Configuration> surface = (IControlSurface<Configuration>) Proxy.newProxyInstance (IControlSurface.class.getClassLoader (), new Class<?> []
        {
            IControlSurface.class
        }, (proxy, method, arguments) -> {
            if ("getConfiguration".equals (method.getName ()))
                return configuration;
            if ("isPressed".equals (method.getName ()) && arguments != null && arguments.length == 1)
                return Boolean.valueOf (ButtonID.SHIFT.equals (arguments[0]) && shiftPressed.get ());
            return defaultValue (method.getReturnType ());
        });

        return new TestRig (new PushRecordArmCommand<> (model, surface), shiftPressed);
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


    private record TestRig (PushRecordArmCommand<IControlSurface<Configuration>, Configuration> command, AtomicBoolean shiftPressed)
    {
        // Data only.
    }
}
