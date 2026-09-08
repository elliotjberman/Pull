// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.EditingPageState;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.testing.EditingPageGallery;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class EditingPageRendererTest
{
    @Test
    void noteEditorKeepsTheExistingMidiOctaveNames ()
    {
        final var expected = java.util.Map.of (0, "C-2", 60, "C3", 127, "G8");
        expected.forEach ((key, name) -> {
            final var note = new EditingPageState.Note (true, "NOTE", 1, 0, key, 4, 96, false, List.of (), EditingPageState.NoteData.empty ());
            assertTrue (EditingPageRenderer.render (note).commands ().stream ().anyMatch (command -> command instanceof DisplayCommand.TextBox text && name.equals (text.text ())));
        });
    }

    @Test
    void clipFooterRetainsHybridIconsAndNoteOccurrenceLabelsAreFormattedInCore ()
    {
        final var hybrid = new EditingPageState.Track (true, "Hybrid", "HYBRID", new de.mossgrabers.pull.core.api.output.RgbColor (62, 160, 255), true, true, false, false);
        final var clip = new EditingPageState.Clip (true, false, 0, 4, 0, 4, true, false, 25, 4, List.of (hybrid), List.of (), false);
        assertTrue (EditingPageRenderer.render (clip).commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.Icon icon && icon.icon () == de.mossgrabers.pull.core.api.output.DisplayIcon.HYBRID_TRACK));
        final var note = EditingPageGallery.examples ().stream ().filter (example -> example.id ().equals ("note-editor-note")).findFirst ().orElseThrow ();
        assertTrue (note.display ().commands ().stream ().anyMatch (command -> command instanceof final DisplayCommand.TextBox text && text.text ().equals ("Without Prev Channel")));
    }

    @Test
    void unavailableNoteDoesNotDisplayRetainedValuesAndQuantizeDoesNotInventATrackSelection ()
    {
        final var retained = (EditingPageState.Note) EditingPageGallery.examples ().stream ().filter (example -> example.id ().equals ("note-unavailable")).findFirst ().orElseThrow ().state ();
        final var text = EditingPageRenderer.render (retained).commands ().stream ().filter (DisplayCommand.TextBox.class::isInstance).map (DisplayCommand.TextBox.class::cast).map (DisplayCommand.TextBox::text).toList ();
        assertEquals (List.of ("Waiting for note read-back…"), text);
        final var quantize = EditingPageProjector.project (new EditingPageState.Quantize (false, 2, true, 85));
        assertTrue (quantize.lower ().stream ().noneMatch (choice -> choice.available ()));
        assertEquals ("85%", quantize.controls ().get (0).value (), "global edit preference remains available");
    }
}
