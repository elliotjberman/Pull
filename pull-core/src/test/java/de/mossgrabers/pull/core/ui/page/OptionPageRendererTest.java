// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.OptionPageState;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.testing.ListAndOptionCatalogFixtures;
import de.mossgrabers.pull.core.testing.DevicePageGallery;
import de.mossgrabers.pull.core.testing.EditingPageGallery;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class OptionPageRendererTest
{
    @Test
    void galleryPagesKeepTextWithinTheNativeDisplayAndLeaveClippingToTheCompiler ()
    {
        final var scenes = Stream.of (
            ListAndOptionCatalogFixtures.examples ().stream ().map (ListAndOptionCatalogFixtures.Example::scene),
            DevicePageGallery.examples ().stream ().map (DevicePageGallery.Example::display),
            EditingPageGallery.examples ().stream ().map (EditingPageGallery.Example::display)).flatMap (stream -> stream).toList ();
        for (final var scene: scenes)
            for (final var command: scene.commands ())
            {
                assertFalse (command instanceof DisplayCommand.PushClip || command instanceof DisplayCommand.PopClip, "view compiler owns clipping");
                if (command instanceof final DisplayCommand.TextBox text)
                    assertTrue (text.x () >= 0 && text.y () >= 0 && text.x () + text.width () <= 960 && text.y () + text.height () <= 160.00001, text.toString ());
            }
    }

    @Test
    void denseBrowserWindowPreservesAll48RowsWithoutExceedingTheDisplayWorkBudget ()
    {
        final String fullName = "A long browser name with many characters ".repeat (24);
        final var entries = IntStream.range (0, 48).mapToObj (index -> new OptionPageState.BrowserItem (true, fullName, index == 47, 123456)).toList ();
        final var state = new OptionPageState.Browser (true, 2, 0, "", "", "", false, List.of (), entries);
        final var output = OptionPageRenderer.render (state);
        assertEquals (48, output.commands ().stream ().filter (command -> command instanceof final DisplayCommand.TextBox text && text.text ().equals ("(123456)")).count (), "every long-name row retains its separate hit count");
        assertEquals (96, output.commands ().stream ().filter (DisplayCommand.TextBox.class::isInstance).count ());
        assertTrue (output.commands ().stream ().filter (DisplayCommand.Rectangle.class::isInstance).map (DisplayCommand.Rectangle.class::cast)
            .anyMatch (marker -> marker.x () == 840 && marker.y () > 130 && marker.width () == 3 && marker.color ().red () == 255));
    }
    @Test
    void browserUnavailableClearsRetainedResultsAndEmptyResultsHaveAnExplicitMessage ()
    {
        final var retained = new OptionPageState.Browser (false, 1, -1, "", "", "", false, List.of (), List.of (new OptionPageState.BrowserItem (true, "Retained", true, 5)));
        assertEquals (1, OptionPageRenderer.render (retained).commands ().size ());
        final var empty = new OptionPageState.Browser (true, 1, -1, "", "", "", false, List.of (), List.of ());
        assertTrue (OptionPageRenderer.render (empty).commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && text.text ().contains ("No results")));
    }
    @Test
    void changingObservedFixedLengthMovesSelectionWithoutChangingTheCreateActionRow ()
    {
        final var before = OptionPageRenderer.render (new OptionPageState.FixedLength (1));
        final var after = OptionPageRenderer.render (new OptionPageState.FixedLength (7));
        assertEquals (before.commands ().stream ().filter (command -> command instanceof final DisplayCommand.TextBox text && text.y () < 35).toList (),
            after.commands ().stream ().filter (command -> command instanceof final DisplayCommand.TextBox text && text.y () < 35).toList ());
        assertNotEquals (before, after);
    }

    @Test
    void repeatUsesObservedModeAndIndependentSyncShuffleAndGrooveState ()
    {
        final var state = new OptionPageState.NoteRepeat (true, 0.11, 0.17, false, true, true, true,
            List.of ("All", "Up"), 0, "Down", 2, false, false,
            new OptionPageState.Parameter ("Shuffle", 64, 128, "50 %", false), new OptionPageState.Parameter ("Groove", 0, 128, "Off", false));
        final var output = OptionPageRenderer.render (state);
        final RgbColor selected = new RgbColor (255, 255, 255);
        final RgbColor unselected = new RgbColor (128, 128, 128);
        assertEquals (selected, text (output, "1/32", 1).color (), "period uses the nearest available resolution");
        assertEquals (selected, text (output, "1/16t", 3).color ());
        assertEquals (unselected, text (output, "Sync", 6).color (), "free-running disables Sync");
        assertEquals (selected, text (output, "Shuffle", 7).color ());
        assertEquals (unselected, text (output, "Groove Off", 7).color (), "Shuffle does not imply global Groove is enabled");
        assertNotNull (text (output, "Down", 5), "the displayed mode comes from read-back, independent of a clamped configured index");
    }

    private static DisplayCommand.TextBox text (final ControllerDisplayScene scene, final String label, final int column)
    {
        return scene.commands ().stream ().filter (DisplayCommand.TextBox.class::isInstance).map (DisplayCommand.TextBox.class::cast)
            .filter (text -> text.text ().equals (label) && text.x () >= column * 120 && text.x () < (column + 1) * 120).findFirst ().orElseThrow ();
    }
}
