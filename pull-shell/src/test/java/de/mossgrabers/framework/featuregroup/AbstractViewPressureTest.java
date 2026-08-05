// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.featuregroup;

import de.mossgrabers.framework.configuration.AbstractConfiguration;
import de.mossgrabers.framework.configuration.Configuration;
import de.mossgrabers.framework.controller.IControlSurface;
import de.mossgrabers.framework.controller.grid.IPadGrid;
import de.mossgrabers.framework.controller.valuechanger.IValueChanger;
import de.mossgrabers.framework.daw.IModel;
import de.mossgrabers.framework.daw.midi.MidiConstants;
import de.mossgrabers.framework.scale.Scales;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;


class AbstractViewPressureTest
{
    @Test
    void routesMappedPadPressureWithoutRegistration ()
    {
        final TestRig rig = new TestRig (AbstractConfiguration.AFTERTOUCH_CONVERT_POLY);
        rig.view.map (36, 60);

        rig.view.onGridPressure (36, 91);

        assertEquals (1, rig.messages.size ());
        assertArrayEquals (new int []
        {
            MidiConstants.CMD_POLY_AFTERTOUCH,
            60,
            91
        }, rig.messages.get (0));
    }


    @Test
    void ignoresPressureOutsideTheMusicalNoteMap ()
    {
        final TestRig rig = new TestRig (AbstractConfiguration.AFTERTOUCH_CONVERT_CHANNEL);

        rig.view.onGridPressure (36, 91);
        rig.view.onGridPressure (-1, 91);

        assertEquals (0, rig.messages.size ());
    }


    private static final class TestRig
    {
        private final List<int []> messages = new ArrayList<> ();
        private final TestView     view;


        @SuppressWarnings("unchecked")
        private TestRig (final int conversion)
        {
            final AtomicInteger conversionMode = new AtomicInteger (conversion);
            final Configuration configuration = proxy (Configuration.class, (proxy, method, arguments) -> {
                if ("getConvertAftertouch".equals (method.getName ()))
                    return Integer.valueOf (conversionMode.get ());
                return defaultValue (method.getReturnType ());
            });
            final IPadGrid grid = proxy (IPadGrid.class, (proxy, method, arguments) -> {
                if ("translateToGrid".equals (method.getName ()))
                    return arguments[0];
                return defaultValue (method.getReturnType ());
            });
            final IControlSurface<Configuration> surface = (IControlSurface<Configuration>) Proxy.newProxyInstance (
                IControlSurface.class.getClassLoader (),
                new Class<?> []
                {
                    IControlSurface.class
                },
                (proxy, method, arguments) -> {
                    switch (method.getName ())
                    {
                        case "getConfiguration":
                            return configuration;
                        case "getPadGrid":
                            return grid;
                        case "sendMidiEvent":
                            this.messages.add (new int []
                            {
                                ((Integer) arguments[0]).intValue (),
                                ((Integer) arguments[1]).intValue (),
                                ((Integer) arguments[2]).intValue ()
                            });
                            return null;
                        default:
                            return defaultValue (method.getReturnType ());
                    }
                });
            final IValueChanger valueChanger = proxy (IValueChanger.class, (proxy, method, arguments) -> defaultValue (method.getReturnType ()));
            final Scales scales = new Scales (valueChanger, 36, 100, 8, 8);
            final IModel model = proxy (IModel.class, (proxy, method, arguments) -> {
                if ("getScales".equals (method.getName ()))
                    return scales;
                return defaultValue (method.getReturnType ());
            });
            this.view = new TestView (surface, model);
        }
    }


    private static final class TestView extends AbstractView<IControlSurface<Configuration>, Configuration>
    {
        private TestView (final IControlSurface<Configuration> surface, final IModel model)
        {
            super ("Pressure test", surface, model);
        }


        private void map (final int physicalNote, final int midiNote)
        {
            final int [] mapping = Scales.getEmptyMatrix ();
            mapping[physicalNote] = midiNote;
            this.keyManager.setNoteMatrix (mapping);
        }


        @Override
        public void drawGrid ()
        {
            // Not needed by this test.
        }


        @Override
        public void onGridNote (final int note, final int velocity)
        {
            // Not needed by this test.
        }
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
        if (!type.isPrimitive ())
            return null;
        if (type == boolean.class)
            return Boolean.FALSE;
        if (type == char.class)
            return Character.valueOf ('\0');
        if (type == byte.class)
            return Byte.valueOf ((byte) 0);
        if (type == short.class)
            return Short.valueOf ((short) 0);
        if (type == int.class)
            return Integer.valueOf (0);
        if (type == long.class)
            return Long.valueOf (0);
        if (type == float.class)
            return Float.valueOf (0);
        return Double.valueOf (0);
    }
}
