// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.bitwig.framework.daw;

import static de.mossgrabers.pull.shell.testing.TestProxies.proxy;
import static de.mossgrabers.pull.shell.testing.TestProxies.relaxedProxy;
import static de.mossgrabers.pull.shell.testing.TestProxies.relaxedValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import com.bitwig.extension.controller.api.ControllerHost;
import com.bitwig.extension.controller.api.CursorTrack;
import com.bitwig.extension.controller.api.Device;
import com.bitwig.extension.controller.api.DeviceBank;
import com.bitwig.extension.controller.api.DeviceLayer;
import com.bitwig.extension.controller.api.DeviceLayerBank;
import com.bitwig.extension.controller.api.DeviceMatcher;
import com.bitwig.extension.controller.api.DeviceSlot;
import com.bitwig.extension.controller.api.PinnableCursorDevice;

import de.mossgrabers.bitwig.framework.daw.PrimaryDrumDeviceCanopy.Candidate;
import de.mossgrabers.bitwig.framework.daw.PrimaryDrumDeviceCanopy.Path;

import org.junit.jupiter.api.Test;


/**
 * Tests fixed topology constraints of the primary drum-device canopy.
 */
class PrimaryDrumDeviceCanopyTest
{
    @Test
    void doesNotAdvertiseTrackSendsOnNestedProxyPaths ()
    {
        final Device directDrum = relaxedProxy (Device.class);
        final Device layerDrum = relaxedProxy (Device.class);
        final Device slotDrum = relaxedProxy (Device.class);
        final DeviceBank directBank = deviceBank (directDrum);
        final DeviceBank layerBank = deviceBank (layerDrum);
        final DeviceBank slotBank = deviceBank (slotDrum);
        final DeviceLayer firstLayer = proxy (DeviceLayer.class, (proxy, method, arguments) -> "createDeviceBank".equals (method.getName ()) ? layerBank : relaxedValue (method.getReturnType ()));
        final DeviceLayerBank layers = proxy (DeviceLayerBank.class, (proxy, method, arguments) -> "getItemAt".equals (method.getName ()) ? firstLayer : relaxedValue (method.getReturnType ()));
        final DeviceSlot slot = proxy (DeviceSlot.class, (proxy, method, arguments) -> "createDeviceBank".equals (method.getName ()) ? slotBank : relaxedValue (method.getReturnType ()));
        final PinnableCursorDevice primaryInstrument = proxy (PinnableCursorDevice.class, (proxy, method, arguments) -> {
            if ("createLayerBank".equals (method.getName ()))
                return layers;
            if ("getCursorSlot".equals (method.getName ()))
                return slot;
            return relaxedValue (method.getReturnType ());
        });
        final CursorTrack track = proxy (CursorTrack.class, (proxy, method, arguments) -> {
            if ("createDeviceBank".equals (method.getName ()))
                return directBank;
            if ("createCursorDevice".equals (method.getName ()))
                return primaryInstrument;
            return relaxedValue (method.getReturnType ());
        });
        final ControllerHost host = proxy (ControllerHost.class, (proxy, method, arguments) -> "createBitwigDeviceMatcher".equals (method.getName ()) ? relaxedProxy (DeviceMatcher.class) : relaxedValue (method.getReturnType ()));

        final List<Candidate> candidates = PrimaryDrumDeviceCanopy.create (host, track, "cursor-id", "Cursor", 8);

        assertEquals (List.of (Path.TRACK_CHAIN, Path.FIRST_INSTRUMENT, Path.FIRST_LAYER, Path.CURSOR_SLOT), candidates.stream ().map (Candidate::path).toList ());
        assertEquals (List.of (Integer.valueOf (8), Integer.valueOf (0), Integer.valueOf (0), Integer.valueOf (0)), candidates.stream ().map (Candidate::numLayerSends).toList ());
        assertSame (directDrum, candidates.get (0).device ());
        assertSame (primaryInstrument, candidates.get (1).device ());
        assertSame (layerDrum, candidates.get (2).device ());
        assertSame (slotDrum, candidates.get (3).device ());
    }


    private static DeviceBank deviceBank (final Device device)
    {
        return proxy (DeviceBank.class, (proxy, method, arguments) -> "getItemAt".equals (method.getName ()) ? device : relaxedValue (method.getReturnType ()));
    }


}
