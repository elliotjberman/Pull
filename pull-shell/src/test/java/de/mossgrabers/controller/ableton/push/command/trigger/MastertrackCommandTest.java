// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.command.trigger;

import de.mossgrabers.controller.ableton.push.PushConfiguration;
import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.controller.ableton.push.controller.PushControlSurface;
import de.mossgrabers.framework.controller.display.IGraphicDisplay;
import de.mossgrabers.framework.controller.hardware.IHwButton;
import de.mossgrabers.framework.controller.hardware.IHwLight;
import de.mossgrabers.framework.controller.hardware.IHwSurfaceFactory;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.data.ICursorTrack;
import de.mossgrabers.framework.daw.data.IMasterTrack;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.daw.midi.IMidiInput;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.framework.daw.midi.ISelectedTrackNoteTarget;
import de.mossgrabers.framework.featuregroup.IMode;
import de.mossgrabers.framework.mode.Modes;
import de.mossgrabers.framework.utils.ButtonEvent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;


class MastertrackCommandTest
{
    @Test
    void coreWorkspaceMasterIsAPageOverlayAndDoesNotChangeTheSelectedTrack ()
    {
        final IValueChanger valueChanger = new TwosComplementValueChanger (128, 1);
        final PushControlSurface surface = createSurface (valueChanger);
        surface.getModeManager ().register (Modes.TRACK, relaxedProxy (IMode.class));
        surface.getModeManager ().register (Modes.MASTER, relaxedProxy (IMode.class));
        surface.getModeManager ().setDefaultID (Modes.TRACK);
        surface.getModeManager ().setActive (Modes.TRACK);
        final List<String> selections = new ArrayList<> ();
        final ICursorTrack cursorTrack = proxy (ICursorTrack.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "doesExist" -> Boolean.TRUE;
            case "getIndex" -> Integer.valueOf (3);
            default -> relaxedValue (method.getReturnType ());
        });
        final IMasterTrack masterTrack = proxy (IMasterTrack.class, (proxy, method, arguments) -> {
            if ("select".equals (method.getName ()))
                selections.add ("master");
            return relaxedValue (method.getReturnType ());
        });
        final IModel model = proxy (IModel.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getCursorTrack" -> cursorTrack;
            case "getMasterTrack" -> masterTrack;
            default -> relaxedValue (method.getReturnType ());
        });
        final MastertrackCommand command = new MastertrackCommand (model, surface, () -> true);

        press (command);

        assertEquals (Modes.MASTER, surface.getModeManager ().getActiveID ());
        assertEquals (List.of (), selections);

        press (command);

        assertEquals (Modes.TRACK, surface.getModeManager ().getActiveID ());
        assertEquals (List.of (), selections);
    }


    private static void press (final MastertrackCommand command)
    {
        command.execute (ButtonEvent.DOWN, 127);
        command.execute (ButtonEvent.UP, 0);
    }


    private static PushControlSurface createSurface (final IValueChanger valueChanger)
    {
        final IHwButton button = relaxedProxy (IHwButton.class);
        final IHwLight light = relaxedProxy (IHwLight.class);
        final IHwSurfaceFactory factory = proxy (IHwSurfaceFactory.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "createButton" -> button;
            case "createLight" -> light;
            default -> relaxedValue (method.getReturnType ());
        });
        final IHost host = proxy (IHost.class, (proxy, method, arguments) -> "createSurfaceFactory".equals (method.getName ()) ? factory : relaxedValue (method.getReturnType ()));
        final PushControlSurface surface = new PushControlSurface (
            host,
            new PushColorManager (),
            new PushConfiguration (host, valueChanger, List.of ()),
            relaxedProxy (IMidiOutput.class),
            relaxedProxy (IMidiInput.class),
            relaxedProxy (ISelectedTrackNoteTarget.class),
            relaxedProxy (ITrack.class),
            () -> false,
            null);
        surface.addGraphicsDisplay (relaxedProxy (IGraphicDisplay.class));
        return surface;
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
