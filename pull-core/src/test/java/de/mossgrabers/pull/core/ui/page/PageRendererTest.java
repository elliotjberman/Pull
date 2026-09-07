// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.AutomationWriteMode;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.*;
import org.junit.jupiter.api.Test;
import de.mossgrabers.pull.core.ui.component.ResponseCurve;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

/** Renderers consume immutable presentation values without constructing a host or controller. */
class PageRendererTest
{
    @Test
    void unavailableFrameCannotRenderInventedOptionsEvenWithSelectedPresentationFlags ()
    {
        final var output = FramePageRenderer.render (new FramePagePresentation (false, FramePagePresentation.Layout.ARRANGE, Collections.nCopies (8, Boolean.TRUE)));
        assertEquals (List.of (new DisplayCommand.Rectangle (0, 0, 960, 160, new RgbColor (0, 0, 0))), output.display ().commands ());
        assertEquals (16, output.lights ().size ());
        assertTrue (output.lights ().values ().stream ().allMatch (new RgbColor (0, 0, 0)::equals));
    }

    @Test
    void unknownWritingModeDoesNotPretendThatReadOrAnotherWriteModeIsSelected ()
    {
        final var output = SettingsPageRenderer.render (new SettingsPagePresentation.Automation (true, true, AutomationWriteMode.UNKNOWN));
        for (int index = 1; index <= 4; index++)
            assertEquals (new RgbColor (30, 30, 30), output.lights ().get (PushControlIds.button ("ROW1_" + index)));
    }

    @Test
    void setupKeepsParameterTextInItsPhysicalColumnsAndTheCompleteCurveStrokeInsideItsRegion ()
    {
        final PageVisuals output = SetupPageRenderer.render (new SetupPagePresentation (true, 0, 100, 0, 10, 10, Set.of (1, 2, 4, 5, 6),
            java.util.stream.IntStream.range (0, 128).map (value -> value < 64 ? 1 : 127).boxed ().toList ()));
        final List<DisplayCommand.TextBox> text = output.display ().commands ().stream ().filter (DisplayCommand.TextBox.class::isInstance).map (DisplayCommand.TextBox.class::cast).toList ();
        final List<String> labels = List.of ("Brightness", "Display", "LEDs", "Pads", "Sensitivity", "Gain", "Dynamics");
        for (int column = 0; column < labels.size (); column++)
        {
            final String label = labels.get (column);
            final DisplayCommand.TextBox field = text.stream ().filter (value -> value.text ().equals (label)).findFirst ().orElseThrow ();
            assertTrue (field.x () >= column * 120 && field.x () + field.width () <= (column + 1) * 120, label);
        }
        for (final DisplayCommand.TextBox field: text)
        {
            final int column = (int) (field.x () / 120);
            assertTrue (field.x () + field.width () <= (column + 1) * 120, field.text ());
            assertTrue (field.y () >= 0 && field.y () + field.height () <= 160, field.text ());
        }
        final List<DisplayCommand.Line> curve = output.display ().commands ().stream ().filter (DisplayCommand.Line.class::isInstance).map (DisplayCommand.Line.class::cast).toList ();
        assertFalse (curve.isEmpty ());
        assertStrokeContained (curve, 380, 84, 90, 34);
    }

    @Test
    void responseCurveCanReachBothExtremesWithoutItsStrokeEscapingTheSuppliedBounds ()
    {
        final List<DisplayCommand> commands = new ArrayList<> ();
        ResponseCurve.append (commands, List.of (0.0, 1.0, 0.0), 12, 8, new RgbColor (62, 160, 255), new ResponseCurve.Style (90, 34, 6));
        final List<DisplayCommand.Line> lines = commands.stream ().map (DisplayCommand.Line.class::cast).toList ();
        assertFalse (lines.isEmpty ());
        assertStrokeContained (lines, 12, 8, 90, 34);
        assertTrue (lines.stream ().anyMatch (line -> Math.min (line.y1 (), line.y2 ()) - line.width () / 2 == 8));
        assertTrue (lines.stream ().anyMatch (line -> Math.max (line.y1 (), line.y2 ()) + line.width () / 2 == 42));
    }

    @Test
    void unavailableSetupKeepsNavigationButCannotRenderRetainedSettingsTouchOrCurve ()
    {
        final PageVisuals output = SetupPageRenderer.render (new SetupPagePresentation (false, 100, 100, 10, 10, 10, Set.of (1, 2, 4, 5, 6), Collections.nCopies (128, 127)));
        final List<String> text = output.display ().commands ().stream ().filter (DisplayCommand.TextBox.class::isInstance).map (DisplayCommand.TextBox.class::cast).map (DisplayCommand.TextBox::text).toList ();
        assertEquals (List.of ("Info", "Setup", "Waiting for Push settings"), text);
        assertTrue (output.display ().commands ().stream ().noneMatch (command -> command instanceof DisplayCommand.Line || command instanceof DisplayCommand.DottedArc));
        final RgbColor black = new RgbColor (0, 0, 0);
        assertEquals (new RgbColor (255, 255, 255), output.lights ().get (PushControlIds.button ("ROW2_2")));
        assertNotEquals (black, output.lights ().get (PushControlIds.button ("ROW2_1")));
        for (int index = 1; index <= 8; index++)
        {
            assertEquals (black, output.lights ().get (PushControlIds.button ("ROW1_" + index)));
            if (index > 2) assertEquals (black, output.lights ().get (PushControlIds.button ("ROW2_" + index)));
        }
    }

    @Test
    void ribbonReflectsObservedSelectionsWhileNumericCcAndQuickActionsKeepTheirDistinctLightMeaning ()
    {
        final RgbColor black = new RgbColor (0, 0, 0);
        final RgbColor white = new RgbColor (255, 255, 255);
        final RgbColor dim = new RgbColor (30, 30, 30);
        final PageVisuals output = RibbonPageRenderer.render (new RibbonPagePresentation (true, 5, 64, 2));
        assertEquals (black, output.lights ().get (PushControlIds.button ("ROW2_1")));
        for (int index = 2; index <= 5; index++)
            assertEquals (dim, output.lights ().get (PushControlIds.button ("ROW2_" + index)));
        for (int index = 1; index <= 8; index++)
            assertEquals (index == 6 ? white : index > 6 ? black : dim, output.lights ().get (PushControlIds.button ("ROW1_" + index)));
        for (int index = 6; index <= 8; index++)
            assertEquals (index == 8 ? white : dim, output.lights ().get (PushControlIds.button ("ROW2_" + index)));
        final List<DisplayCommand.TextBox> text = output.display ().commands ().stream ().filter (DisplayCommand.TextBox.class::isInstance).map (DisplayCommand.TextBox.class::cast).toList ();
        final DisplayCommand.TextBox cc = text.stream ().filter (field -> field.text ().equals ("64")).findFirst ().orElseThrow ();
        assertEquals (white, cc.color ());
        assertTrue (output.display ().commands ().stream ().filter (DisplayCommand.Rectangle.class::isInstance).map (DisplayCommand.Rectangle.class::cast)
            .anyMatch (box -> box.color ().equals (white) && box.x () + box.width () < cc.x () && box.y () <= cc.y () && box.y () + box.height () >= cc.y () + cc.height ()));
        assertEquals (new RgbColor (128, 128, 128), text.stream ().filter (field -> field.text ().equals ("Sustain")).findFirst ().orElseThrow ().color ());
        for (final DisplayCommand.TextBox field: text)
            assertTrue (field.x () >= 0 && field.y () >= 0 && field.x () + field.width () <= 960 && field.y () + field.height () <= 160, field.text ());
    }

    @Test
    void unavailableRibbonCannotShowRetainedSelectionsOrQuickActions ()
    {
        final PageVisuals output = RibbonPageRenderer.render (new RibbonPagePresentation (false, 5, 127, 2));
        assertTrue (output.display ().commands ().stream ().noneMatch (DisplayCommand.TextBox.class::isInstance));
        assertEquals (16, output.lights ().size ());
        assertTrue (output.lights ().values ().stream ().allMatch (new RgbColor (0, 0, 0)::equals));
    }

    private static void assertStrokeContained (final List<DisplayCommand.Line> lines, final double left, final double top, final double width, final double height)
    {
        for (final DisplayCommand.Line line: lines)
        {
            final double radius = line.width () / 2;
            assertTrue (Math.min (line.x1 (), line.x2 ()) - radius >= left && Math.max (line.x1 (), line.x2 ()) + radius <= left + width, line.toString ());
            assertTrue (Math.min (line.y1 (), line.y2 ()) - radius >= top && Math.max (line.y1 (), line.y2 ()) + radius <= top + height, line.toString ());
        }
    }

}
