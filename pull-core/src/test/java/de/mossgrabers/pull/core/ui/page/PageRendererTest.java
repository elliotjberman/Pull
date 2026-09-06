// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.AutomationWriteMode;
import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.*;
import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Renderers consume immutable presentation values without constructing a host or controller. */
class PageRendererTest
{
    @Test
    void accentDrawsTheObservedValueAndExactTouchedRingGeometry ()
    {
        final PageVisuals output = AccentPageRenderer.render (new AccentPagePresentation (true, 64, 0.5, true));
        assertEquals (List.of (
            new DisplayCommand.Rectangle (0, 0, 960, 160, new RgbColor (0, 0, 0)),
            new DisplayCommand.TextAt ("Accent", 848, 31, new RgbColor (190, 235, 247), 12.5),
            new DisplayCommand.TextAt ("64", 848, 64, new RgbColor (190, 235, 247), 30),
            new DisplayCommand.DottedArc (873, 105, 25, 220, -260, 220, 1.1, new RgbColor (20, 54, 65)),
            new DisplayCommand.DottedArc (873, 105, 25, 220, -130, 110, 1.1, new RgbColor (132, 214, 255))), output.display ().commands ());
        assertEquals (16, output.lights ().size ());
        assertTrue (output.lights ().values ().stream ().allMatch (new RgbColor (0, 0, 0)::equals));
    }

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
    void macroPresentationOwnsItsValuesAndRendererNeedsNoController ()
    {
        final var controls = new java.util.ArrayList<MacroPagePresentation.Control> ();
        controls.add (new MacroPagePresentation.Control (0, "Cutoff", "440 Hz", 0.5, MacroPagePresentation.Widget.RING, true));
        final MacroPagePresentation page = new MacroPagePresentation (controls);
        controls.clear ();
        final ControllerDisplayScene scene = MacroPageRenderer.render (page);
        assertEquals (1, page.controls ().size ());
        assertTrue (scene.commands ().contains (new DisplayCommand.TextAt ("Cutoff", 8, 31, MacroPageStyle.METER_TEXT, 12.5)));
        assertTrue (scene.commands ().contains (new DisplayCommand.DottedArc (33, 105, 25, 220, -130, 110, 1.1, MacroPageStyle.METER_ON)));
        assertThrows (UnsupportedOperationException.class, () -> page.controls ().clear ());
        assertThrows (IllegalArgumentException.class, () -> new MacroPagePresentation (List.of (page.controls ().getFirst (), page.controls ().getFirst ())));
    }

    @Test
    void mixerMenuLightsAndDisplayShareOnePresentation ()
    {
        final var menu = java.util.stream.IntStream.range (0, 8).mapToObj (column ->
            new GlobalMixerPagePresentation.MenuItem (column == 2 ? "Pan" : "", column == 2, false)).toList ();
        final PageVisuals output = GlobalMixerPageRenderer.render (new GlobalMixerPagePresentation (menu, List.of ()));
        assertEquals (PageStyle.WHITE, output.lights ().get (PushControlIds.button ("ROW2_3")));
        assertEquals (PageStyle.BLACK, output.lights ().get (PushControlIds.button ("ROW2_2")));
        assertTrue (output.display ().commands ().stream ().anyMatch (command -> command instanceof DisplayCommand.TextBox text && "Pan".equals (text.text ()) && PageStyle.BLACK.equals (text.color ())));
    }

}
