// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.testing;

import de.mossgrabers.pull.core.api.OptionPageState;
import de.mossgrabers.pull.core.api.ColorPaletteSnapshot;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.DisplayCommand;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.api.output.PadGridPosition;
import de.mossgrabers.pull.core.ui.ColorPaletteRenderer;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import de.mossgrabers.pull.core.ui.component.OptionColumn;
import de.mossgrabers.pull.core.ui.component.TextList;
import de.mossgrabers.pull.core.ui.page.OptionPageRenderer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Shared raw observation fixtures for the central catalog and renderer contract tests. */
public final class ListAndOptionCatalogFixtures
{
    public record Example (String id, String family, String title, String description, ControllerDisplayScene scene) { }
    public record State (String id, String family, String title, OptionPageState value) { }
    private static final RgbColor BLUE = new RgbColor (62, 160, 255);
    private ListAndOptionCatalogFixtures () { }
    public static List<State> states ()
    {
        final List<OptionPageState.BrowserColumn> columns = List.of (
            new OptionPageState.BrowserColumn (true, "Device Type", true, "Instrument", "*"), new OptionPageState.BrowserColumn (true, "Category", true, "Synthesizer", "*"),
            new OptionPageState.BrowserColumn (true, "Creator", true, "*", "*"), new OptionPageState.BrowserColumn (false, "", false, "", ""));
        final List<OptionPageState.BrowserItem> items = IntStream.range (0, 48).mapToObj (index -> new OptionPageState.BrowserItem (true, index == 13 ? "A very long device or preset name with a distinctive ending" : "Preset " + (index + 1), index == 13, 17 + index)).toList ();
        final List<String> roots = List.of ("C", "G", "D", "A", "E", "B", "F", "Bb", "Eb", "Ab", "Db", "Gb");
        final List<String> scales = List.of ("Major", "Minor", "Dorian", "Mixolydian", "Lydian", "Phrygian", "Locrian", "Harmonic Minor", "Melodic Minor");
        final List<String> layouts = List.of ("4th ^", "4th >", "3rd ^", "3rd >", "Seqent ^", "Seqent >", "8th ^", "8th >", "8th ^ centered", "8th > centered", "Staggered ^", "Staggered >");
        return List.of (
            new State ("browser-overview", "Browser", "Browser · overview and active filters", new OptionPageState.Browser (true, 0, 1, "Browse instruments and presets", "Warm analog lead", "Presets", true, columns, List.of ())),
            new State ("browser-results", "Browser", "Browser · 48 preset results", new OptionPageState.Browser (true, 1, -1, "", "", "Presets", false, columns, items)),
            new State ("browser-filter", "Browser", "Browser · filter hit counts", new OptionPageState.Browser (true, 2, 1, "", "", "Presets", false, columns, items)),
            new State ("browser-empty", "Browser", "Browser · no results", new OptionPageState.Browser (true, 1, -1, "", "", "Presets", false, columns, List.of ())),
            new State ("scales", "Scales", "Scales · root, range and selected scale", new OptionPageState.Scales (scales, 7, roots, 8, false, "C1 – G8")),
            new State ("scale-layout", "Scales", "Scales · layout and orientation", new OptionPageState.ScaleLayout (layouts, 9)),
            new State ("fixed-length", "Fixed length", "Fixed length · create and stored length", new OptionPageState.FixedLength (3)),
            new State ("note-repeat", "Note repeat", "Note repeat · timing, latch, pressure and groove", new OptionPageState.NoteRepeat (true, 0.25, 1.0 / 6, true, true, false, true,
                List.of ("All", "Up", "Down", "Random"), 3, "Random", 2, true, false, new OptionPageState.Parameter ("Shuffle Amount", 512, 1024, "50.0 %", true), new OptionPageState.Parameter ("Groove", 1023, 1024, "On", false))),
            new State ("add-track", "Add track", "Add track · instrument shortcuts", new OptionPageState.AddTrack ("INSTRUMENT", List.of ("Polymer", "Phase-4", "A very long favorite synthesizer name", "", "Sampler", "Drum Machine", "Organ"))),
            new State ("add-device", "Add track", "Add device · browse and shortcuts", new OptionPageState.AddTrack ("DEVICE", List.of ("EQ+", "Compressor", "Delay+"))),
            new State ("option-page-unavailable", "Settings", "Option pages · unavailable observation", OptionPageState.empty ()));
    }
    public static List<Example> examples ()
    { return states ().stream ().map (state -> new Example (state.id (), state.family (), state.title (), "Production renderer with supplied observation data; actions and hardware lights are outside this display preview.", OptionPageRenderer.render (state.value ()))).toList (); }
    public static List<Example> components ()
    {
        final List<DisplayCommand> list = new ArrayList<> (List.of (new DisplayCommand.Rectangle (0, 0, 120, 160, new RgbColor (0, 0, 0))));
        TextList.append (list, List.of (new TextList.Item ("First item", false, BLUE), new TextList.Item ("Selected item", true, BLUE), new TextList.Item ("A very long name", false, BLUE)), 0, 0, 120, 160, 6);
        final List<DisplayCommand> options = new ArrayList<> (List.of (new DisplayCommand.Rectangle (0, 0, 120, 160, new RgbColor (0, 0, 0))));
        new OptionColumn (new ChoiceCell ("Available", true, false), new ChoiceCell ("Selected", true, true), null, null).append (options, 0, 0, 160, new ChoiceCell.Style (118, 34, 6, 0, 17, 10));
        final List<DisplayCommand> pads = new ArrayList<> (List.of (new DisplayCommand.Rectangle (0, 0, 160, 160, new RgbColor (0, 0, 0))));
        colorPalette ().forEach ((position, color) -> pads.add (new DisplayCommand.Rectangle (position.column () * 20 + 2, (7 - position.row ()) * 20 + 2, 16, 16, color)));
        return List.of (new Example ("component-list", "Components", "Text list", "One bounded column with six rows, source colors and observed selection.", new ControllerDisplayScene (120, 160, list)),
            new Example ("component-option-column", "Components", "Option column", "Two shared choices at the edges of one column; no filled selection background.", new ControllerDisplayScene (120, 160, options)),
            new Example ("component-color-palette", "Components", "Color picker · physical pads", "The physical eight-by-eight pad grid, with the first host color at bottom left. This is a pad preview, not an LCD page.", new ControllerDisplayScene (160, 160, pads)));
    }

    public static Map<PadGridPosition, RgbColor> colorPalette ()
    {
        final int[][] observed = {{128,128,128},{84,84,84},{122,122,122},{128,128,128},{201,201,201},{134,137,172},{163,121,67},{198,159,112},
            {87,97,198},{132,138,224},{149,73,203},{217,56,113},{217,46,36},{255,87,6},{217,157,16},{67,210,185},{115,152,20},{0,157,71},
            {68,200,255},{188,118,240},{225,102,145},{236,97,87},{255,131,62},{228,183,78},{160,192,76},{0,166,148},{62,187,98},{0,153,217}};
        return ColorPaletteRenderer.pads (new ColorPaletteSnapshot (0, java.util.Arrays.stream (observed).map (color -> new RgbColor (color[0], color[1], color[2])).toList ()));
    }
}
