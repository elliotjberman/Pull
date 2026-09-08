// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.EditingPageState;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.ui.page.EditingPageRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/** The same observed editing fixtures feed the catalog and renderer regressions. */
public final class EditingPageGallery
{
    public record Example (String id, String title, EditingPageState state)
    {
        public ControllerDisplayScene display () { return EditingPageRenderer.render (this.state); }
    }
    private static final List<Boolean> TOUCHED = List.of (true, false, false, false, true, false, false, false);
    private EditingPageGallery () { }
    public static List<Example> examples ()
    {
        final List<Example> result = new ArrayList<> ();
        final var tracks = IntStream.range (0, 8).mapToObj (i -> new EditingPageState.Track (i < 7, i == 0 ? "Selected synthesizer" : "Track " + (i + 1), "INSTRUMENT", new RgbColor (62, 160, 255), i == 0, i != 6, false, false)).toList ();
        result.add (new Example ("clip-settings", "Clip · loop, timing and track footer", new EditingPageState.Clip (true, true, 0, 32, 4, 16, true, false, 25, 4, tracks, TOUCHED, false)));
        result.add (new Example ("clip-unavailable", "Clip · unavailable", new EditingPageState.Clip (false, false, 0, 0, 0, 0, false, false, 0, 4, List.of (), List.of (), false)));
        final var data = new EditingPageState.NoteData (0.75, false, 0.85, 0.2, 0.6, true, 0.75, true, "NOT_PREV_CHANNEL", true, 5, 21, 0.7, -0.3, 7, 0.35, 0.25, true, 3, -0.25, 0.4, -0.1);
        for (final String page: List.of ("NOTE", "EXPRESSIONS", "REPEAT", "RECCURRENCE_PATTERN"))
            result.add (new Example ("note-editor-" + page.toLowerCase (java.util.Locale.ROOT), "Note · " + switch (page) { case "NOTE" -> "common"; case "RECCURRENCE_PATTERN" -> "recurrence pattern"; default -> page.toLowerCase (java.util.Locale.ROOT); }, new EditingPageState.Note (true, page, 1, 2, 60, 4, 96, false, TOUCHED, data)));
        result.add (new Example ("note-recurrence-presets", "Note · recurrence presets", new EditingPageState.Note (true, "RECCURRENCE_PATTERN", 4, 2, 60, 4, 96, true, TOUCHED, data)));
        result.add (new Example ("note-unavailable", "Note · waiting for observed selection", new EditingPageState.Note (false, "NOTE", 1, 2, 60, 4, 96, false, TOUCHED, data)));
        result.add (new Example ("quantize", "Quantize · record grid and amount", new EditingPageState.Quantize (true, 2, true, 85)));
        result.add (new Example ("quantize-unavailable", "Quantize · no track", new EditingPageState.Quantize (false, 2, true, 100)));
        result.add (new Example ("groove", "Groove · shuffle and accent", new EditingPageState.Groove (true, List.of (
            new EditingPageState.Parameter (true, "Amount", .4, "40 %", false), new EditingPageState.Parameter (true, "Rate", .5, "1/16", true), new EditingPageState.Parameter (true, "Amount", .6, "60 %", false), new EditingPageState.Parameter (true, "Phase", .25, "25 %", false), new EditingPageState.Parameter (true, "Rate", .75, "1/8", false)))));
        return List.copyOf (result);
    }
}
