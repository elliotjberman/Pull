// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.ui.component.BipolarSlider;
import de.mossgrabers.pull.core.ui.component.FaderMarker;
import de.mossgrabers.pull.core.ui.component.VerticalMeter;
import de.mossgrabers.pull.core.ui.page.GlobalMixerPagePresentation;
import de.mossgrabers.pull.core.ui.page.GlobalMixerPageRenderer;
import de.mossgrabers.pull.core.ui.page.MixerControlStyle;
import de.mossgrabers.pull.core.ui.page.PageVisuals;
import de.mossgrabers.pull.core.ui.page.PlaybackRippleRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Offline cases for shared bank controls and the remote-project playback overlay. */
public final class UiLibraryCompletionFixtures
{
    public static final String RIPPLE_STORY_ID = "playback-ripple";
    public static final RgbColor BLUE = new RgbColor (62, 160, 255);
    private static final RgbColor BLACK = new RgbColor (0, 0, 0);
    private static final RgbColor DARK = new RgbColor (32, 32, 32);
    private static final RgbColor WHITE = new RgbColor (255, 255, 255);

    public record Example (String id, String title, String description, PageVisuals visuals) { }
    public record Component (String id, String title, String description, List<Variant> variants) { }
    public record Variant (String id, String title, ControllerDisplayScene display) { }
    public record Animation (String id, String title, int durationMillis, List<List<ControllerDisplayScene>> runs) { }

    private UiLibraryCompletionFixtures () { }

    public static List<Example> views ()
    {
        return List.of (
            new Example ("global-mixer-extremes", "Global mixer · observed extremes", "Shared choices, values, faders, meters, pan and rings; VU observations differ from parameter values. The final column is unavailable.", GlobalMixerPageRenderer.render (globalMixer ())),
            new Example (RIPPLE_STORY_ID, "Remote playback ripple", "Preview the purple playing ripple or white stopped ripple. Each button replays the production animation and clears the display when it finishes.", new PageVisuals (Map.of (), PlaybackRippleRenderer.display (1, WHITE, 0))));
    }

    /** Match ProjectPlaybackCoordinator's 250ms wave and playing/stopped colors; all frames use its renderer. */
    public static List<Animation> rippleAnimations ()
    {
        return List.of (ripple ("playing", "Play start ripple", new RgbColor (160, 48, 255)), ripple ("stopped", "Play stop ripple", WHITE));
    }

    private static Animation ripple (final String id, final String title, final RgbColor color)
    {
        final List<List<ControllerDisplayScene>> runs = new ArrayList<> (8);
        for (int seed = 0; seed < 8; seed++)
        {
            final List<ControllerDisplayScene> frames = new ArrayList<> (16);
            for (int frame = 0; frame <= 15; frame++) frames.add (PlaybackRippleRenderer.display (frame / 15.0, color, seed * 7919L));
            runs.add (List.copyOf (frames));
        }
        return new Animation (id, title, 250, List.copyOf (runs));
    }

    public static GlobalMixerPagePresentation globalMixer ()
    {
        final List<GlobalMixerPagePresentation.MenuItem> menu = new ArrayList<> ();
        for (int column = 0; column < 8; column++)
            menu.add (new GlobalMixerPagePresentation.MenuItem (column == 7 ? "" : column == 0 ? "Volume with long menu title" : "Control " + column, column == 0, column == 6));
        final List<GlobalMixerPagePresentation.Control> controls = List.of (
            control (0, GlobalMixerPagePresentation.Widget.VOLUME, "-123.456789 dB", 0, true, 1, 0.25),
            control (1, GlobalMixerPagePresentation.Widget.VOLUME, "+6.0 dB", 1, false, 0.1, 0.7),
            control (2, GlobalMixerPagePresentation.Widget.PAN, "100 L", 0, true, 0, 0),
            control (3, GlobalMixerPagePresentation.Widget.PAN, "100 R", 1, true, 0, 0),
            control (4, GlobalMixerPagePresentation.Widget.SEND_VOLUME, "-∞ dB", 0, true, 0, 0),
            control (5, GlobalMixerPagePresentation.Widget.RING, "A very long host value", 1, true, 0, 0),
            control (6, GlobalMixerPagePresentation.Widget.RING, "99.999999 kHz", 0.5, false, 0, 0));
        return new GlobalMixerPagePresentation (menu, controls);
    }

    private static GlobalMixerPagePresentation.Control control (final int column, final GlobalMixerPagePresentation.Widget widget,
        final String text, final double value, final boolean active, final double left, final double right)
    {
        return new GlobalMixerPagePresentation.Control (column, widget, text, value, active, BLUE, left, right);
    }

    public static List<Component> components ()
    {
        final List<Variant> meters = new ArrayList<> ();
        final List<Variant> faders = new ArrayList<> ();
        final List<Variant> sliders = new ArrayList<> ();
        for (final double value: new double[] {0, 0.5, 1})
        {
            final String id = value == 0 ? "minimum" : value == 1 ? "maximum" : "midpoint";
            final String title = (value == 0 ? "Minimum" : value == 1 ? "Maximum" : "Midpoint") + " · " + (int) (100 * value) + "%";
            final List<DisplayCommand> meter = background (32, 88);
            VerticalMeter.append (meter, 4, 4, value, DARK, List.of (
                new VerticalMeter.Band (0, 0.75, BLUE),
                new VerticalMeter.Band (0.75, 0.9, new RgbColor (255, 144, 0)),
                new VerticalMeter.Band (0.9, 1, new RgbColor (255, 0, 0))), MixerControlStyle.LEVEL_METER);
            meters.add (new Variant (id, title, new ControllerDisplayScene (32, 88, meter)));
            final List<DisplayCommand> fader = background (20, 88);
            FaderMarker.append (fader, 12, 4, value, BLUE, MixerControlStyle.VOLUME_FADER);
            faders.add (new Variant (id, title, new ControllerDisplayScene (20, 88, fader)));
            final List<DisplayCommand> pan = background (90, 24);
            BipolarSlider.append (pan, 4, 12, value, BLUE, DARK, MixerControlStyle.PAN_SLIDER);
            sliders.add (new Variant (id, title, new ControllerDisplayScene (90, 24, pan)));
        }
        return List.of (
            new Component ("component-vertical-meter", "Vertical meter", "A supplied level color with fixed orange and red warning bands.", List.copyOf (meters)),
            new Component ("component-fader-marker", "Fader marker", "Observed parameter marker and filled stem.", List.copyOf (faders)),
            new Component ("component-bipolar-slider", "Bipolar slider", "Center reference, filled excursion and observed position.", List.copyOf (sliders)));
    }

    private static List<DisplayCommand> background (final int width, final int height)
    {
        final List<DisplayCommand> commands = new ArrayList<> ();
        commands.add (new DisplayCommand.Rectangle (0, 0, width, height, BLACK));
        return commands;
    }
}
