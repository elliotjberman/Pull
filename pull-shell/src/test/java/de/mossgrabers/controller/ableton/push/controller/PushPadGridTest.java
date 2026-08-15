// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.controller.ableton.push.controller;

import de.mossgrabers.framework.controller.color.ColorEx;
import de.mossgrabers.framework.controller.grid.PadColor;
import de.mossgrabers.framework.daw.midi.IMidiOutput;
import de.mossgrabers.pull.core.api.output.ControllerPadGridOverlay;
import de.mossgrabers.pull.core.api.output.PadGridPosition;
import de.mossgrabers.pull.core.api.output.RgbColor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;


/** Tests the permanent generic overlay plane beneath reloadable pad animations. */
class PushPadGridTest
{
    @Test
    void fadeAndRenderedPadUseTheSameRgbResolution ()
    {
        final List<MidiNote> sent = new ArrayList<> ();
        final IMidiOutput output = proxy (IMidiOutput.class, (ignored, method, arguments) -> {
            if ("sendNoteEx".equals (method.getName ()))
                sent.add (new MidiNote (((Integer) arguments[0]).intValue (), ((Integer) arguments[1]).intValue (), ((Integer) arguments[2]).intValue ()));
            return null;
        });
        final PushPadGrid grid = new PushPadGrid (new PushColorManager (), output);
        final PadColor paleYellow = PadColor.rgb (ColorEx.fromRGB (254, 254, 170));

        grid.light (36, paleYellow);
        grid.requestFade (36, paleYellow);
        grid.sendState (36);

        assertEquals (2, sent.getLast ().channel ());
        assertEquals (43, sent.getLast ().velocity ());
    }


    @Test
    void sparseOverlayFreezesTheVisibleFrameAndRestoresTheLatestStableFrame ()
    {
        final List<MidiNote> sent = new ArrayList<> ();
        final IMidiOutput output = proxy (IMidiOutput.class, (ignored, method, arguments) -> {
            if ("sendNoteEx".equals (method.getName ()))
                sent.add (new MidiNote (((Integer) arguments[0]).intValue (), ((Integer) arguments[1]).intValue (), ((Integer) arguments[2]).intValue ()));
            return null;
        });
        final PushColorManager colors = new PushColorManager ();
        final PushPadGrid grid = new PushPadGrid (colors, output);
        final AtomicReference<ControllerPadGridOverlay> overlay = new AtomicReference<> (ControllerPadGridOverlay.inactive ());
        grid.setOverlaySupplier (overlay::get);

        grid.light (36, 10);
        grid.light (37, 11);
        final RgbColor purple = new RgbColor (160, 48, 255);
        final RgbColor off = new RgbColor (0, 0, 0);
        final int purpleIndex = colors.getColorIndex (ColorEx.fromRGB (purple.red (), purple.green (), purple.blue ()));
        overlay.set (new ControllerPadGridOverlay (true, Map.of (new PadGridPosition (0, 0), purple, new PadGridPosition (1, 0), off)));

        assertEquals (purpleIndex, grid.getLightInfo (36).getColor ());
        assertEquals (0, grid.getLightInfo (37).getColor ());

        grid.light (36, 20);
        grid.light (37, 21);
        assertEquals (purpleIndex, grid.getLightInfo (36).getColor ());
        assertEquals (0, grid.getLightInfo (37).getColor ());
        grid.sendState (36);
        assertEquals (new MidiNote (0, 36, purpleIndex), sent.getLast ());

        overlay.set (ControllerPadGridOverlay.inactive ());
        assertEquals (20, grid.getLightInfo (36).getColor ());
        assertEquals (21, grid.getLightInfo (37).getColor ());
        grid.sendState (36);
        assertEquals (new MidiNote (0, 36, 20), sent.getLast ());
    }


    @Test
    void cachesRgbResolutionForRepeatedOverlaySamples ()
    {
        final IMidiOutput output = proxy (IMidiOutput.class, (ignored, method, arguments) -> null);
        final TrackingPushColorManager colors = new TrackingPushColorManager ();
        final PushPadGrid grid = new PushPadGrid (colors, output);
        final RgbColor purple = new RgbColor (160, 48, 255);
        grid.setOverlaySupplier (() -> new ControllerPadGridOverlay (true, Map.of (new PadGridPosition (0, 0), purple)));

        grid.getLightInfo (36);
        grid.getLightInfo (36);
        grid.sendState (36);

        assertEquals (1, colors.rgbResolutions);
    }


    private static <T> T proxy (final Class<T> type, final java.lang.reflect.InvocationHandler handler)
    {
        return type.cast (Proxy.newProxyInstance (type.getClassLoader (), new Class<?> []
        {
            type
        }, handler));
    }


    private record MidiNote (int channel, int note, int velocity)
    {
    }


    private static final class TrackingPushColorManager extends PushColorManager
    {
        private int rgbResolutions;


        /** {@inheritDoc} */
        @Override
        public int getColorIndex (final ColorEx color)
        {
            this.rgbResolutions++;
            return super.getColorIndex (color);
        }
    }
}
