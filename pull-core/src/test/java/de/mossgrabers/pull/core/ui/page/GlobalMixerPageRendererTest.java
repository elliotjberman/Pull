// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.PushControlIds;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.DisplayTextAlignment;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.testing.UiLibraryCompletionFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GlobalMixerPageRendererTest
{
    @Test
    void aFullBankOfLongHostNamesAndValuesFitsTheRenderBudgetAndRetainsUnits ()
    {
        final String hostName = "A long observed send name ".repeat (40);
        final String hostValue = "1234567890".repeat (100) + " dB";
        final var menu = java.util.stream.IntStream.range (0, 8).mapToObj (index -> new GlobalMixerPagePresentation.MenuItem (hostName, index == 0, false)).toList ();
        for (final var widget: GlobalMixerPagePresentation.Widget.values ())
        {
            final var controls = java.util.stream.IntStream.range (0, 8).mapToObj (index -> new GlobalMixerPagePresentation.Control (index, widget, hostValue, 1, true, UiLibraryCompletionFixtures.BLUE, 1, 1)).toList ();
            final var page = new GlobalMixerPagePresentation (menu, controls);
            final var scene = assertDoesNotThrow (() -> GlobalMixerPageRenderer.render (page).display (), widget.name ());
            assertTrue (scene.commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && text.text ().equals ("dB")));
        }
    }

    @Test
    void menuSelectionUsesTheSharedMarkerWithoutChangingObservedButtonLights ()
    {
        final PageVisuals visuals = GlobalMixerPageRenderer.render (UiLibraryCompletionFixtures.globalMixer ());
        final RgbColor white = new RgbColor (255, 255, 255);
        assertEquals (white, visuals.lights ().get (PushControlIds.button ("ROW2_1")));
        assertEquals (new RgbColor (0, 0, 0), visuals.lights ().get (PushControlIds.button ("ROW2_2")));
        assertEquals (white, visuals.lights ().get (PushControlIds.button ("ROW2_7")));
        assertTrue (visuals.display ().commands ().contains (new DisplayCommand.Rectangle (0, 0, 3, GlobalMixerPageStyle.MENU.height (), white)));
        assertFalse (visuals.display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.Rectangle rectangle && rectangle.y () == 0 && rectangle.color ().equals (white) && rectangle.width () > 3));
        final List<DisplayCommand.TextBox> labels = visuals.display ().commands ().stream ().filter (DisplayCommand.TextBox.class::isInstance).map (DisplayCommand.TextBox.class::cast).toList ();
        for (final DisplayCommand.TextBox label: labels)
        {
            final double columnRight = (1 + Math.floor (label.x () / 120)) * 120;
            assertTrue (label.x () + label.width () <= columnRight, label.text ());
            assertEquals (DisplayTextAlignment.LEFT, label.alignment ());
        }
        assertTrue (labels.stream ().anyMatch (label -> label.text ().equals ("-123.456789")));
        assertTrue (labels.stream ().anyMatch (label -> label.text ().equals ("dB")));
    }

    @Test
    void vuExtentsComeFromObservedLevelsIndependentlyOfTheFaderValue ()
    {
        final var scene = GlobalMixerPageRenderer.render (UiLibraryCompletionFixtures.globalMixer ()).display ();
        final double top = GlobalMixerPageStyle.VOLUME_TOP;
        final double height = GlobalMixerPageStyle.VOLUME_METER.height ();
        assertTrue (scene.commands ().contains (new DisplayCommand.Rectangle (10, top, 18, height, UiLibraryCompletionFixtures.BLUE)));
        assertTrue (scene.commands ().contains (new DisplayCommand.Rectangle (31, top + height * 0.75, 18, height * 0.25, UiLibraryCompletionFixtures.BLUE)));
        assertTrue (scene.commands ().contains (new DisplayCommand.Rectangle (63, top + height, 13, 1, UiLibraryCompletionFixtures.BLUE)));
    }
}
