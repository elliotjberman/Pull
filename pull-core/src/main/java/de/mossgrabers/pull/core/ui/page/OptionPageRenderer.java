// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.OptionPageState;
import de.mossgrabers.pull.core.api.output.ControllerDisplayScene;
import de.mossgrabers.pull.core.api.output.RgbColor;
import de.mossgrabers.pull.core.ui.component.TextList;
import java.util.List;
import java.util.stream.IntStream;
import static de.mossgrabers.pull.core.ui.PageStyle.WHITE;

/** Core-owned formatting and layout projection of the subscribed option-page observation. */
public final class OptionPageRenderer
{
    private static final List<String> LENGTHS = List.of ("1 Beat", "2 Beat", "1 Bar", "2 Bars", "4 Bars", "8 Bars", "16 Bars", "32 Bars");
    private static final List<String> RESOLUTIONS = List.of ("1/4", "1/4t", "1/8", "1/8t", "1/16", "1/16t", "1/32", "1/32t");
    private static final double[] TIMES = {1, 2.0 / 3, 0.5, 1.0 / 3, 0.25, 1.0 / 6, 0.125, 1.0 / 12};
    private static final RgbColor ORANGE = new RgbColor (255, 128, 0);
    private static final List<AddTrackPagePresentation.Kind> KINDS = List.of (
        new AddTrackPagePresentation.Kind (0, "Instrument", new RgbColor (255, 255, 0)), new AddTrackPagePresentation.Kind (1, "Audio", new RgbColor (0, 255, 0)),
        new AddTrackPagePresentation.Kind (2, "Effect", new RgbColor (0, 0, 255)), new AddTrackPagePresentation.Kind (4, "Device", ORANGE));
    private OptionPageRenderer () { }
    public static ControllerDisplayScene render (final OptionPageState state)
    {
        return switch (state)
        {
            case final OptionPageState.Empty ignored -> OptionPageLayout.scene (OptionPageLayout.background ());
            case final OptionPageState.FixedLength value -> FixedLengthPageRenderer.render (new FixedLengthPagePresentation (LENGTHS, value.selected ()));
            case final OptionPageState.Scales value -> ScalesPageRenderer.render (new ScalesPagePresentation (value.names (), value.selectedScale (), value.roots (), value.selectedRoot (), value.chromatic (), value.range ()));
            case final OptionPageState.ScaleLayout value -> ScaleLayoutPageRenderer.render (new ScaleLayoutPagePresentation (IntStream.range (0, value.names ().size ()).filter (index -> index % 2 == 0).mapToObj (index -> value.names ().get (index).replace (" ^", "")).toList (), value.selected () / 2, value.selected () % 2 != 0));
            case final OptionPageState.AddTrack value -> addTrack (value);
            case final OptionPageState.Browser value -> browser (value);
            case final OptionPageState.NoteRepeat value -> repeat (value);
        };
    }
    private static ControllerDisplayScene addTrack (final OptionPageState.AddTrack state)
    {
        final int index = switch (state.mode ()) { case "AUDIO" -> 1; case "EFFECT" -> 2; case "DEVICE" -> 3; default -> 0; };
        final var kind = KINDS.get (index);
        return AddTrackPageRenderer.render (new AddTrackPagePresentation (KINDS, kind.label (), index == 3 ? "Browse" : "Empty", kind.color (), state.shortcuts ()));
    }
    private static ControllerDisplayScene browser (final OptionPageState.Browser state)
    {
        final var mode = state.selectionMode () == 1 ? BrowserPagePresentation.Mode.RESULTS : state.selectionMode () == 2 ? BrowserPagePresentation.Mode.FILTER : BrowserPagePresentation.Mode.OVERVIEW;
        final List<BrowserPagePresentation.Column> columns = state.columns ().stream ().map (column -> new BrowserPagePresentation.Column (column.name (), column.cursorExists () && !column.cursorName ().equals (column.wildcard ()) ? column.cursorName () : "", column.exists (), column.cursorExists () && !column.cursorName ().equals (column.wildcard ()))).toList ();
        final boolean emptyResults = mode == BrowserPagePresentation.Mode.RESULTS && (state.items ().isEmpty () || !state.items ().get (0).exists ());
        final List<TextList.Item> items = emptyResults ? List.of () : state.items ().stream ().map (item -> new TextList.Item (item.exists () ? item.name () : "",
            item.exists () && mode == BrowserPagePresentation.Mode.FILTER && !item.name ().isEmpty () ? "(" + item.hits () + ")" : "", item.selected (), WHITE)).toList ();
        return BrowserPageRenderer.render (new BrowserPagePresentation (state.active (), mode, state.info (), state.selectedResult (), state.contentType (), state.selectedColumn (), state.preview () ? ORANGE : new RgbColor (128, 128, 128), columns, items));
    }
    private static ControllerDisplayScene repeat (final OptionPageState.NoteRepeat state)
    {
        final var mode = new NoteRepeatPagePresentation.Parameter ("Mode", state.modeName (), state.modes ().size () > 1 ? state.modeIndex () / (double) (state.modes ().size () - 1) : 0, state.modeTouched ());
        final var octaves = new NoteRepeatPagePresentation.Parameter ("Octaves", Integer.toString (state.octaves ()), state.octaves () / 8.0, state.octavesTouched ());
        final var shuffle = state.shuffleAmount ();
        final var amount = new NoteRepeatPagePresentation.Parameter (shuffle.name (), shuffle.displayedValue (), shuffle.upperBound () > 0 ? shuffle.value () / (double) shuffle.upperBound () : 0, shuffle.touched ());
        return NoteRepeatPageRenderer.render (new NoteRepeatPagePresentation (state.available (), RESOLUTIONS, resolution (state.period ()), resolution (state.length ()), state.latch (), state.pressure (), !state.freeRunning (), state.shuffle (), "Groove " + state.groove ().displayedValue (), state.groove ().value () != 0, mode, octaves, amount));
    }
    private static int resolution (final double value)
    {
        double nearest = 1;
        int selected = 0;
        for (int index = 0; index < TIMES.length; index++)
        {
            final double distance = Math.abs (TIMES[index] - value);
            if (distance < nearest) { nearest = distance; selected = index; }
        }
        return selected;
    }
}
