// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.configuration;

import de.mossgrabers.framework.controller.valuechanger.TwosComplementValueChanger;
import de.mossgrabers.framework.daw.IHost;
import de.mossgrabers.framework.daw.data.IDeviceMetadata;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;


/** Characterization tests for the fixed Add Track device shortcuts. */
class AbstractConfigurationShortcutTest
{
    @Test
    void shortMetadataListsRepeatTheirLastEntryAcrossSevenSlots ()
    {
        final IDeviceMetadata instrument1 = new DeviceMetadata ("Instrument 1");
        final IDeviceMetadata instrument2 = new DeviceMetadata ("Instrument 2");
        final IDeviceMetadata effect = new DeviceMetadata ("Effect");
        final TestConfiguration configuration = new TestConfiguration (host (List.of (instrument1, instrument2), List.of (effect)));

        configuration.initializeShortcuts ();

        assertSame (instrument1, configuration.getInstrumentShortcut (0).orElseThrow ());
        assertSame (instrument2, configuration.getInstrumentShortcut (6).orElseThrow ());
        assertSame (effect, configuration.getAudioEffectShortcut (6).orElseThrow ());
        assertSame (effect, configuration.getDeviceShortcut (6).orElseThrow ());
        assertTrue (configuration.getDeviceShortcut (7).isEmpty ());
    }


    @Test
    void emptyMetadataListsLeaveEveryShortcutEmpty ()
    {
        final TestConfiguration configuration = new TestConfiguration (host (List.of (), List.of ()));

        configuration.initializeShortcuts ();

        assertTrue (configuration.getInstrumentShortcut (0).isEmpty ());
        assertTrue (configuration.getAudioEffectShortcut (0).isEmpty ());
        assertTrue (configuration.getDeviceShortcut (0).isEmpty ());
    }


    private static IHost host (final List<IDeviceMetadata> instruments, final List<IDeviceMetadata> effects)
    {
        return (IHost) Proxy.newProxyInstance (IHost.class.getClassLoader (), new Class<?> []
        {
            IHost.class
        }, (proxy, method, arguments) -> switch (method.getName ())
        {
            case "getInstrumentMetadata" -> instruments;
            case "getAudioEffectMetadata" -> effects;
            default -> defaultValue (method.getReturnType ());
        });
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


    private static final class TestConfiguration extends AbstractConfiguration
    {
        private TestConfiguration (final IHost host)
        {
            super (host, new TwosComplementValueChanger (128, 1), List.of ());
        }


        private void initializeShortcuts ()
        {
            this.initializeDeviceShortcuts ();
        }


        @Override
        public void init (final ISettingsUI globalSettings, final ISettingsUI documentSettings)
        {
            // Not needed for this focused initialization test.
        }
    }


    private record DeviceMetadata (String name) implements IDeviceMetadata
    {
        @Override
        public String fullName ()
        {
            return this.name;
        }
    }
}
