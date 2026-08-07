// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.view.sequencer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Proxy;

import de.mossgrabers.controller.ableton.push.controller.PushColorManager;
import de.mossgrabers.framework.configuration.Configuration;
import de.mossgrabers.framework.controller.IControlSurface;
import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.daw.DAWColor;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.ITransport;
import de.mossgrabers.framework.daw.clip.INoteClip;

import org.junit.jupiter.api.Test;


/**
 * Tests the audio-clip range feedback.
 */
class ClipLengthViewTest
{
    @Test
    void showsUnselectedPadsInWhiteAndBlinksThePlayingPadSlowly ()
    {
        final Fixture fixture = createFixture (DAWColor.DAW_COLOR_BLUE.getColor (), 20);

        fixture.view ().drawGrid ();

        assertEquals (DAWColor.DAW_COLOR_BLUE.name (), fixture.pad (0).color ());
        assertNull (fixture.pad (0).blinkColor ());
        assertEquals (DAWColor.DAW_COLOR_BLUE.name (), fixture.pad (1).color ());
        assertEquals (AbstractSequencerView.COLOR_ACTIVE_PAGE, fixture.pad (1).blinkColor ());
        assertFalse (fixture.pad (1).fast ());
        assertEquals (AbstractSequencerView.COLOR_PAGE, fixture.pad (2).color ());
        assertEquals (PushColorManager.PUSH2_COLOR2_WHITE, fixture.colorManager ().getColorIndex (AbstractSequencerView.COLOR_PAGE));
    }


    @Test
    void leavesUnselectedPadsOffForTheWhiteClipColor ()
    {
        final Fixture fixture = createFixture (DAWColor.DAW_COLOR_LIGHT_GRAY.getColor (), -1);

        fixture.view ().drawGrid ();

        assertEquals (DAWColor.DAW_COLOR_LIGHT_GRAY.name (), fixture.pad (0).color ());
        assertEquals (ClipLengthView.COLOR_OUTSIDE, fixture.pad (2).color ());
        assertEquals (PushColorManager.PUSH2_COLOR_BLACK, fixture.colorManager ().getColorIndex (ClipLengthView.COLOR_OUTSIDE));
        assertNull (fixture.pad (0).blinkColor ());
        assertNull (fixture.pad (2).blinkColor ());
    }


    private static Fixture createFixture (final ColorEx clipColor, final int currentStep)
    {
        final PadLight [] padLights = new PadLight [64];
        final IPadGrid padGrid = proxy (IPadGrid.class, (proxy, method, arguments) -> {
            if ("getCols".equals (method.getName ()))
                return Integer.valueOf (8);
            if ("getRows".equals (method.getName ()))
                return Integer.valueOf (8);
            if ("lightEx".equals (method.getName ()) && arguments[2] instanceof final String color)
            {
                final int pad = ((Integer) arguments[1]).intValue () * 8 + ((Integer) arguments[0]).intValue ();
                final String blinkColor = arguments.length == 5 ? (String) arguments[3] : null;
                final boolean fast = arguments.length == 5 && ((Boolean) arguments[4]).booleanValue ();
                padLights[pad] = new PadLight (color, blinkColor, fast);
            }
            return defaultValue (method.getReturnType ());
        });
        final INoteClip clip = proxy (INoteClip.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getColor" -> clipColor;
            case "getCurrentStep" -> Integer.valueOf (currentStep);
            case "getLoopLength" -> Double.valueOf (8.0);
            case "getLoopStart" -> Double.valueOf (0.0);
            case "getStepLength" -> Double.valueOf (0.25);
            default -> defaultValue (method.getReturnType ());
        });
        final ITransport transport = proxy (ITransport.class, (proxy, method, arguments) -> "getQuartersPerMeasure".equals (method.getName ()) ? Integer.valueOf (4) : defaultValue (method.getReturnType ()));
        final PushColorManager colorManager = new PushColorManager ();
        final IModel model = proxy (IModel.class, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getColorManager" -> colorManager;
            case "getNoteClip" -> clip;
            case "getTransport" -> transport;
            default -> defaultValue (method.getReturnType ());
        });
        final Configuration configuration = proxy (Configuration.class, (proxy, method, arguments) -> defaultValue (method.getReturnType ()));
        final IControlSurface<Configuration> surface = controlSurface (configuration, padGrid);
        final ClipLengthView<IControlSurface<Configuration>, Configuration> view = new ClipLengthView<> (surface, model, true);
        return new Fixture (view, colorManager, padLights);
    }


    @SuppressWarnings("unchecked")
    private static IControlSurface<Configuration> controlSurface (final Configuration configuration, final IPadGrid padGrid)
    {
        return (IControlSurface<Configuration>) Proxy.newProxyInstance (IControlSurface.class.getClassLoader (), new Class<?> []
        {
            IControlSurface.class
        }, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getConfiguration" -> configuration;
            case "getPadGrid" -> padGrid;
            default -> defaultValue (method.getReturnType ());
        });
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


    private record Fixture (ClipLengthView<IControlSurface<Configuration>, Configuration> view, PushColorManager colorManager, PadLight [] padLights)
    {
        PadLight pad (final int index)
        {
            return this.padLights[index];
        }
    }


    private record PadLight (String color, String blinkColor, boolean fast)
    {
    }
}
