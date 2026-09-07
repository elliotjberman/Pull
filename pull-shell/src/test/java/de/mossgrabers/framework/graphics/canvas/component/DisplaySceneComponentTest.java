// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.graphics.canvas.component;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.junit.jupiter.api.Test;
import com.bitwig.extension.api.graphics.GraphicsOutput;
import de.mossgrabers.bitwig.framework.graphics.GraphicsContextImpl;
import de.mossgrabers.framework.controller.color.ColorEx;

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
            new DisplayCommand.PushClip (0, 0, 960, 143),
            new DisplayCommand.Rectangle (0, 0, 960, 160, new RgbColor (0, 0, 0)),
            new DisplayCommand.RoundedRectangle (10, 10, 66, 32, 16, white),
            new DisplayCommand.Circle (20, 20, 5, white),
            new DisplayCommand.DottedArc (50, 50, 10, 0, 90, 2, 1, white),
            new DisplayCommand.TextAt ("Pan", 128, 34, white, 12.5),
            new DisplayCommand.TextBox ("second_test", 608, 35, 104, 25, DisplayTextAlignment.LEFT, white, 19, 12, DisplayTextFit.CLIP),
            new DisplayCommand.PopClip ()));
        final List<Call> calls = new ArrayList<> ();
        final IGraphicsContext context = recordingContext (calls);

        new DisplaySceneComponent (scene).draw (new DefaultGraphicsInfo (context, null, new DefaultGraphicsDimensions (960, 160, 1024), new DefaultBounds (0, 0, 960, 160)));

        assertTrue (calls.contains (new Call ("drawTextAt", "Pan")));
        assertTrue (calls.contains (new Call ("drawTextInBounds", "second_test")));
        assertEquals (1, calls.stream ().filter (call -> "pushClip".equals (call.method ())).count ());
        assertEquals (1, calls.stream ().filter (call -> "popClip".equals (call.method ())).count ());
        assertEquals (4, calls.stream ().filter (call -> "fillCircle".equals (call.method ())).count ());
        assertEquals (new DisplaySceneComponent (scene), new DisplaySceneComponent (scene));
    }


    @Test
    void transmitsScaledLinesAndTheirClipWithoutLeakingStrokeWidthIntoLaterDrawing ()
    {
        final RgbColor color = new RgbColor (62, 160, 255);
        final ControllerDisplayScene scene = new ControllerDisplayScene (960, 160, List.of (
            new DisplayCommand.PushClip (0, 0, 960, 143),
            new DisplayCommand.Line (20, 30, 60, 70, 4, color),
            new DisplayCommand.PopClip (),
            new DisplayCommand.Line (20, 144, 60, 159, 2, color)));
        final List<NativeStroke> strokes = new ArrayList<> ();
        final Deque<NativeState> saved = new ArrayDeque<> ();
        final NativeState[] state = {new NativeState (7, null)};
        final RectangleCall[] pathRectangle = new RectangleCall[1];
        final double[] path = new double[4];
        final GraphicsOutput output = (GraphicsOutput) Proxy.newProxyInstance (GraphicsOutput.class.getClassLoader (), new Class<?> [] {GraphicsOutput.class}, (proxy, method, arguments) -> {
            switch (method.getName ())
            {
                case "save" -> saved.push (state[0]);
                case "restore" -> state[0] = saved.pop ();
                case "setLineWidth" -> state[0] = new NativeState (((Number) arguments[0]).doubleValue (), state[0].clip ());
                case "rectangle" -> pathRectangle[0] = rectangle (arguments);
                case "clip" -> state[0] = new NativeState (state[0].width (), pathRectangle[0]);
                case "moveTo" -> { path[0] = ((Number) arguments[0]).doubleValue (); path[1] = ((Number) arguments[1]).doubleValue (); }
                case "lineTo" -> { path[2] = ((Number) arguments[0]).doubleValue (); path[3] = ((Number) arguments[1]).doubleValue (); }
                case "stroke" -> strokes.add (new NativeStroke (path[0], path[1], path[2], path[3], state[0].width (), state[0].clip ()));
                default -> { }
            }
            return relaxedValue (method.getReturnType ());
        });
        final GraphicsContextImpl context = new GraphicsContextImpl (GraphicsOutput.AntialiasMode.OFF, output);

        new DisplaySceneComponent (scene).draw (new DefaultGraphicsInfo (context, null, new DefaultGraphicsDimensions (960, 160, 1024), new DefaultBounds (7, 11, 480, 320)));
        context.drawLine (1, 2, 3, 4, ColorEx.WHITE);

        assertEquals (List.of (
            new NativeStroke (17, 71, 37, 151, 2, new RectangleCall (7, 11, 480, 286)),
            new NativeStroke (17, 299, 37, 329, 1, null),
            new NativeStroke (1, 2, 3, 4, 7, null)), strokes);
        assertTrue (saved.isEmpty ());
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
    void stressFitsLongProjectMacroTextThroughTheProductionMixerInterpreter ()
    {
        final RgbColor color = new RgbColor (132, 214, 255);
        final ControllerDisplayScene cellScene = new ControllerDisplayScene (MixerControlDisplay.WIDTH, MixerControlDisplay.HEIGHT, List.of (
            new DisplayCommand.TextBox ("Very Long Project Macro Name", 8, 1, 104, 20, DisplayTextAlignment.LEFT, color, 15, 9, DisplayTextFit.SHRINK_ELLIPSIS),
            new DisplayCommand.TextBox ("-123.456", 8, 21, 64, 30, DisplayTextAlignment.LEFT, color, 30, 12, DisplayTextFit.SHRINK),
            new DisplayCommand.TextAt ("dB", 75, 47, color, 14)));
        final MixerControlsDisplay display = new MixerControlsDisplay (List.of (new MixerControlDisplay (3, MixerControlKind.KNOB, cellScene)));
        final List<TextCall> textCalls = new ArrayList<> ();
        final List<RectangleCall> clips = new ArrayList<> ();
        final IGraphicsContext context = (IGraphicsContext) Proxy.newProxyInstance (
            IGraphicsContext.class.getClassLoader (),
            new Class<?> []
            {
                IGraphicsContext.class
            },
            (proxy, method, arguments) -> {
                if ("pushClip".equals (method.getName ()))
                    clips.add (rectangle (arguments));
                if ("calculateFontSize".equals (method.getName ()))
                    return fittedFontSize ((String) arguments[0], ((Number) arguments[1]).doubleValue () - 1, ((Number) arguments[2]).doubleValue (), ((Number) arguments[3]).doubleValue ());
                if ("drawTextInBounds".equals (method.getName ()))
                    textCalls.add (new TextCall ((String) arguments[0], ((Number) arguments[3]).doubleValue (), ((Number) arguments[7]).doubleValue ()));
                return relaxedValue (method.getReturnType ());
            });

        new MixerControlsComponent (display).draw (new DefaultGraphicsInfo (context, null, new DefaultGraphicsDimensions (960, 160, 1024), new DefaultBounds (0, 0, 960, 160)));

        assertEquals (List.of (new RectangleCall (360, 17, 120, 126)), clips);
        final TextCall label = textCalls.get (0);
        final TextCall value = textCalls.get (1);
        assertTrue (label.text ().endsWith ("..."));
        assertTrue (label.text ().length () < "Very Long Project Macro Name".length ());
        assertTrue (label.fontSize () >= 9 && label.fontSize () < 10);
        assertEquals ("-123.456", value.text ());
        assertEquals (64.0 / ("-123.456".length () * 0.6), value.fontSize (), 0.0001);
        assertTrue (label.measuredWidth () <= label.width ());
        assertTrue (value.measuredWidth () <= value.width ());
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


    private static RectangleCall rectangle (final Object [] arguments)
    {
        return new RectangleCall (((Number) arguments[0]).doubleValue (), ((Number) arguments[1]).doubleValue (), ((Number) arguments[2]).doubleValue (), ((Number) arguments[3]).doubleValue ());
    }


    private static double fittedFontSize (final String text, final double maximumFontSize, final double width, final double minimumFontSize)
    {
        final double fitted = Math.min (maximumFontSize, width / (text.length () * 0.6));
        return fitted < minimumFontSize ? -1 : fitted;
    }


    private record NativeState (double width, RectangleCall clip) { }

    private record NativeStroke (double x1, double y1, double x2, double y2, double width, RectangleCall clip) { }


    private record RectangleCall (double x, double y, double width, double height)
    {
        // Intentionally empty.
    }


    private record TextCall (String text, double width, double fontSize)
    {
        double measuredWidth ()
        {
            return this.text.length () * this.fontSize * 0.6;
        }
    }
}
