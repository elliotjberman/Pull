// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.featuregroup;

import java.lang.reflect.Proxy;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.grid.PadColor;
import de.mossgrabers.framework.daw.data.ITrack;
import de.mossgrabers.framework.scale.Scales;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;


/** Tests that note layouts preserve track RGB values until the pad output boundary. */
class AbstractViewColorTest
{
    @Test
    void octaveRootKeepsTheExactTrackColor ()
    {
        final ColorEx paleYellow = ColorEx.fromRGB (254, 254, 170);
        final ITrack track = proxyTrack (paleYellow);

        assertEquals (PadColor.rgb (paleYellow), AbstractView.replaceOctaveColorWithTrackColor (track, Scales.SCALE_COLOR_OCTAVE));
        assertEquals (PadColor.registered (Scales.SCALE_COLOR_NOTE), AbstractView.replaceOctaveColorWithTrackColor (track, Scales.SCALE_COLOR_NOTE));
    }


    private static ITrack proxyTrack (final ColorEx color)
    {
        return ITrack.class.cast (Proxy.newProxyInstance (ITrack.class.getClassLoader (), new Class<?> []
        {
            ITrack.class
        }, (ignored, method, arguments) -> "getColor".equals (method.getName ()) ? color : null));
    }
}
