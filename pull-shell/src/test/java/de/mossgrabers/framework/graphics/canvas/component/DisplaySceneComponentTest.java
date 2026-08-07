// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.graphics.canvas.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.mossgrabers.framework.graphics.DefaultBounds;
import de.mossgrabers.framework.graphics.DefaultGraphicsDimensions;
import de.mossgrabers.framework.graphics.DefaultGraphicsInfo;
import de.mossgrabers.framework.graphics.IGraphicsContext;
import de.mossgrabers.framework.graphics.display.ModelInfo;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.DisplayTextFit;
import de.mossgrabers.pull.core.api.MixerControlKind;
import de.mossgrabers.pull.core.api.output.MixerControlDisplay;
import de.mossgrabers.pull.core.api.output.MixerControlsDisplay;
import de.mossgrabers.pull.core.api.output.RgbColor;


/** Tests for the stable reloadable-scene interpreter. */
class DisplaySceneComponentTest
{
    @Test
    void interpretsLiteralTextAndScaledPrimitivesWithoutViewSemantics ()
    {
        final RgbColor white = new RgbColor (255, 255, 255);
        final ControllerDisplayScene scene = new ControllerDisplayScene (960, 160, List.of (
            new DisplayCommand.Rectangle (0, 0, 960, 160, new RgbColor (0, 0, 0)),
            new DisplayCommand.RoundedRectangle (10, 10, 66, 32, 16, white),
            new DisplayCommand.Circle (20, 20, 5, white),
            new DisplayCommand.DottedArc (50, 50, 10, 0, 90, 2, 1, white),
            new DisplayCommand.TextAt ("Pan", 128, 34, white, 12.5),
            new DisplayCommand.TextBox ("second_test", 608, 35, 104, 25, DisplayTextAlignment.LEFT, white, 19, 12, DisplayTextFit.CLIP)));
        final List<Call> calls = new ArrayList<> ();
        final IGraphicsContext context = recordingContext (calls);

        new DisplaySceneComponent (scene).draw (new DefaultGraphicsInfo (context, null, new DefaultGraphicsDimensions (960, 160, 1024), new DefaultBounds (0, 0, 960, 160)));

        assertTrue (calls.contains (new Call ("drawTextAt", "Pan")));
        assertTrue (calls.contains (new Call ("drawTextInBounds", "second_test")));
        assertEquals (4, calls.stream ().filter (call -> "fillCircle".equals (call.method ())).count ());
        assertEquals (new DisplaySceneComponent (scene), new DisplaySceneComponent (scene));
    }


    @Test
    void confinesMixerCellsBelowMenusAndAboveTheFooter ()
    {
        final RgbColor color = new RgbColor (1, 2, 3);
        final ControllerDisplayScene cellScene = new ControllerDisplayScene (MixerControlDisplay.WIDTH, MixerControlDisplay.HEIGHT, List.of (new DisplayCommand.Rectangle (0, 0, MixerControlDisplay.WIDTH, MixerControlDisplay.HEIGHT, color)));
        final MixerControlsDisplay display = new MixerControlsDisplay (List.of (new MixerControlDisplay (1, MixerControlKind.PAN, cellScene)));
        final List<RectangleCall> rectangles = new ArrayList<> ();
        final List<RectangleCall> clips = new ArrayList<> ();
        final IGraphicsContext context = (IGraphicsContext) Proxy.newProxyInstance (
            IGraphicsContext.class.getClassLoader (),
            new Class<?> []
            {
                IGraphicsContext.class
            },
            (proxy, method, arguments) -> {
                if ("fillRectangle".equals (method.getName ()))
                    rectangles.add (new RectangleCall (((Number) arguments[0]).doubleValue (), ((Number) arguments[1]).doubleValue (), ((Number) arguments[2]).doubleValue (), ((Number) arguments[3]).doubleValue ()));
                if ("pushClip".equals (method.getName ()))
                    clips.add (new RectangleCall (((Number) arguments[0]).doubleValue (), ((Number) arguments[1]).doubleValue (), ((Number) arguments[2]).doubleValue (), ((Number) arguments[3]).doubleValue ()));
                return relaxedValue (method.getReturnType ());
            });

        new MixerControlsComponent (display).draw (new DefaultGraphicsInfo (context, null, new DefaultGraphicsDimensions (960, 160, 1024), new DefaultBounds (0, 0, 960, 160)));

        assertEquals (List.of (new RectangleCall (120, 17, 120, 126)), rectangles);
        assertEquals (rectangles, clips);
        assertTrue (rectangles.stream ().noneMatch (rectangle -> rectangle.y () < 17 || rectangle.y () + rectangle.height () > 143));
    }


    @Test
    void emptyMixerDisplayDrawsNothing ()
    {
        final List<Call> calls = new ArrayList<> ();
        new MixerControlsComponent (MixerControlsDisplay.empty ()).draw (new DefaultGraphicsInfo (recordingContext (calls), null, new DefaultGraphicsDimensions (960, 160, 1024), new DefaultBounds (0, 0, 960, 160)));
        assertTrue (calls.isEmpty ());
    }


    @Test
    void displayModelInvalidatesItsRenderCacheWhenOnlyTheOverlayChanges ()
    {
        final DisplaySceneComponent base = new DisplaySceneComponent (new ControllerDisplayScene (960, 160, List.of (new DisplayCommand.Rectangle (0, 0, 960, 160, new RgbColor (0, 0, 0)))));
        final DisplaySceneComponent white = new DisplaySceneComponent (new ControllerDisplayScene (960, 160, List.of (new DisplayCommand.Rectangle (0, 0, 10, 160, new RgbColor (255, 255, 255)))));
        final DisplaySceneComponent purple = new DisplaySceneComponent (new ControllerDisplayScene (960, 160, List.of (new DisplayCommand.Rectangle (0, 0, 10, 160, new RgbColor (160, 48, 255)))));

        assertNotEquals (new ModelInfo (null, List.of (base), List.of (white)), new ModelInfo (null, List.of (base), List.of (purple)));
    }


    private static IGraphicsContext recordingContext (final List<Call> calls)
    {
        return (IGraphicsContext) Proxy.newProxyInstance (
            IGraphicsContext.class.getClassLoader (),
            new Class<?> []
            {
                IGraphicsContext.class
            },
            (proxy, method, arguments) -> {
                final String text = arguments != null && arguments.length > 0 && arguments[0] instanceof final String value ? value : "";
                calls.add (new Call (method.getName (), text));
                if ("calculateFontSize".equals (method.getName ()))
                    return Double.valueOf (19);
                if (method.getReturnType () == boolean.class)
                    return Boolean.FALSE;
                if (method.getReturnType () == double.class)
                    return Double.valueOf (0);
                if (method.getReturnType () == int.class)
                    return Integer.valueOf (0);
                return null;
            });
    }


    private record Call (String method, String text)
    {
        // Intentionally empty.
    }


    private static Object relaxedValue (final Class<?> type)
    {
        if (type == boolean.class)
            return Boolean.FALSE;
        if (type == double.class)
            return Double.valueOf (0);
        if (type == int.class)
            return Integer.valueOf (0);
        return null;
    }


    private record RectangleCall (double x, double y, double width, double height)
    {
        // Intentionally empty.
    }
}
