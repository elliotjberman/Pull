// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import de.mossgrabers.pull.core.api.EditingPageState;
import de.mossgrabers.pull.core.api.output.DisplayIcon;
import de.mossgrabers.pull.core.ui.component.ChoiceCell;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import static de.mossgrabers.pull.core.ui.page.EditingPagePresentation.Kind.*;

/** Formatting and layout policy for immutable editing observations. */
final class EditingPageProjector
{
    private EditingPageProjector () { }
    static EditingPagePresentation project (final EditingPageState state)
    {
        final List<ChoiceCell> upper = new ArrayList<> (Collections.nCopies (8, new ChoiceCell ("", false, false)));
        final List<ChoiceCell> lower = new ArrayList<> (upper);
        final List<EditingPagePresentation.Control> controls = new ArrayList<> ();
        final List<EditingPagePresentation.Label> labels = new ArrayList<> ();
        final List<TrackFooterPresentation.Cell> footer = new ArrayList<> ();
        String message = "";
        if (state instanceof final EditingPageState.Clip clip)
        {
            if (!clip.exists ()) message = "Please select a clip…";
            else
            {
                choice (upper, 0, "Pin clip", clip.pinned ()); choice (upper, 7, "Select color", false);
                controls.add (control (0, "Play Start", measures (clip.quartersPerMeasure (), clip.playStart (), 1, false), -1, VALUE, clip.touched ()));
                controls.add (control (1, "Play End", measures (clip.quartersPerMeasure (), clip.playEnd (), 1, false), -1, VALUE, clip.touched ()));
                controls.add (control (2, "Loop Start", measures (clip.quartersPerMeasure (), clip.loopStart (), 1, false), -1, VALUE, clip.touched ()));
                controls.add (control (3, "Loop Length", measures (clip.quartersPerMeasure (), clip.loopLength (), 0, false), -1, VALUE, clip.touched ()));
                controls.add (control (4, "Loop", onOff (clip.loopEnabled ()), clip.loopEnabled () ? 1 : 0, TOGGLE, clip.touched ()));
                controls.add (control (6, "Shuffle", onOff (clip.shuffle ()), clip.shuffle () ? 1 : 0, TOGGLE, clip.touched ()));
                controls.add (control (7, "Accent", Math.round (clip.accent ()) + "%", -1, VALUE, clip.touched ()));
                for (int i = 0; i < clip.tracks ().size (); i++)
                {
                    final var track = clip.tracks ().get (i);
                    if (track.exists ()) footer.add (new TrackFooterPresentation.Cell (i, track.name (), icon (track), track.color (), track.selected (), track.active (), false));
                }
            }
        }
        else if (state instanceof final EditingPageState.Quantize quantize)
        {
            choice (upper, 0, "Quantize", true); choice (upper, 1, "Groove", false);
            final List<String> grids = List.of ("Off", "1/32", "1/16", "1/8", "1/4");
            for (int i = 0; i < grids.size (); i++) lower.set (i, new ChoiceCell (grids.get (i), quantize.trackExists (), quantize.trackExists () && i == quantize.grid ()));
            lower.set (6, new ChoiceCell (onOff (quantize.noteLength ()), quantize.trackExists (), quantize.trackExists () && quantize.noteLength ()));
            labels.add (new EditingPagePresentation.Label (0, false, "Record Quantization"));
            labels.add (new EditingPagePresentation.Label (6, false, "Note Length"));
            controls.add (control (7, "Quantize Amount", quantize.amount () + "%", quantize.amount () / 100.0, RING, List.of ()));
        }
        else if (state instanceof final EditingPageState.Groove groove)
        {
            choice (upper, 0, "Quantize", false); choice (upper, 1, "Groove", true);
            choice (lower, 0, onOff (groove.enabled ()), groove.enabled ());
            labels.add (new EditingPagePresentation.Label (1, false, "Shuffle"));
            final int[] columns = {2, 3, 5, 6, 7};
            for (int i = 0; i < groove.parameters ().size (); i++)
            {
                final var parameter = groove.parameters ().get (i);
                if (parameter.exists ()) controls.add (new EditingPagePresentation.Control (columns[i], parameter.name (), parameter.displayedValue (), parameter.value (), RING, parameter.touched ()));
            }
            if (groove.parameters ().size () > 2 && groove.parameters ().get (2).exists ()) labels.add (new EditingPagePresentation.Label (4, false, "Accent"));
        }
        else if (state instanceof final EditingPageState.Note note)
        {
            if (!note.exists ()) message = note.count () == 0 ? "Please select a note to edit…" : "Waiting for note read-back…";
            else note (note, upper, lower, controls, labels);
        }
        return new EditingPagePresentation (upper, lower, controls, new TrackFooterPresentation (footer), labels, message);
    }

    private static void note (final EditingPageState.Note note, final List<ChoiceCell> upper, final List<ChoiceCell> lower, final List<EditingPagePresentation.Control> controls, final List<EditingPagePresentation.Label> labels)
    {
        choice (upper, 0, "Common", "NOTE".equals (note.page ())); choice (upper, 1, "Expressions", "EXPRESSIONS".equals (note.page ()));
        choice (upper, 2, "Repeat", "REPEAT".equals (note.page ())); choice (upper, 7, "Recurrence", "RECCURRENCE_PATTERN".equals (note.page ()));
        final var value = note.data ();
        if ("RECCURRENCE_PATTERN".equals (note.page ()))
        {
            final List<String> presets = List.of ("First", "Not first", "", "Last", "Not last", "", "Odd", "Even");
            for (int i = 0; i < 8; i++)
            {
                final boolean on = (value.recurrenceMask () & 1 << i) != 0;
                lower.set (i, note.shift () ? new ChoiceCell (presets.get (i), !presets.get (i).isBlank (), false) : new ChoiceCell (onOff (on), i < value.recurrenceLength (), on));
            }
            controls.add (control (7, "Recurrence", value.recurrenceLength () < 2 ? "Off" : Integer.toString (value.recurrenceLength ()), (value.recurrenceLength () - 1) / 7.0, RING, note.touched ()));
            return;
        }
        controls.add (control (0, "Length", measures (note.quartersPerMeasure (), value.duration (), 0, true), -1, VALUE, note.touched ()));
        controls.add (control (1, "Muted", value.muted () ? "Yes" : "No", value.muted () ? 1 : 0, TOGGLE, note.touched ()));
        labels.add (new EditingPagePresentation.Label (0, true, note.count () == 1 ? "Step: " + (note.step () + 1) : "Notes: " + note.count ()));
        labels.add (new EditingPagePresentation.Label (1, true, note.count () == 1 ? noteName (note.key ()) : "*"));
        switch (note.page ())
        {
            case "NOTE" -> {
                add (controls, note, 2, "Velocity", value.velocity ()); add (controls, note, 3, "Vel. Spread", value.velocitySpread ()); add (controls, note, 4, "Release Vel.", value.releaseVelocity ()); add (controls, note, 5, "Chance", value.chance ());
                controls.add (control (6, "Occurrence", occurrence (value.occurrence ()), -1, VALUE, note.touched ()));
                controls.add (control (7, "Recurrence", value.recurrenceLength () < 2 ? "Off" : Integer.toString (value.recurrenceLength ()), (value.recurrenceLength () - 1) / 7.0, RING, note.touched ()));
                choice (lower, 5, onOff (value.chanceEnabled ()), value.chanceEnabled ()); choice (lower, 6, onOff (value.occurrenceEnabled ()), value.occurrenceEnabled ()); choice (lower, 7, onOff (value.recurrenceEnabled ()), value.recurrenceEnabled ());
            }
            case "EXPRESSIONS" -> {
                add (controls, note, 3, "Gain", value.gain ()); bipolar (controls, note, 4, "Pan", value.pan ());
                controls.add (control (5, "Pitch", String.format (Locale.ROOT, "%.1f", value.transpose ()), (value.transpose () + note.transposeRange ()) / (2 * Math.max (1, note.transposeRange ())), RING, note.touched ()));
                bipolar (controls, note, 6, "Timbre", value.timbre ()); add (controls, note, 7, "Pressure", value.pressure ());
            }
            case "REPEAT" -> {
                final int count = value.repeatCount ();
                controls.add (control (3, "Count", count == 0 ? "Off" : count < 0 ? "1/" + Math.abs (count - 1) : Integer.toString (count + 1), (count + 127) / 254.0, RING, note.touched ()));
                choice (lower, 3, onOff (value.repeatEnabled ()), value.repeatEnabled ());
                bipolar (controls, note, 4, "Curve", value.repeatCurve ()); bipolar (controls, note, 5, "Vel. Curve", value.repeatVelocityCurve ()); bipolar (controls, note, 6, "Vel. End", value.repeatVelocityEnd ());
            }
            default -> { }
        }
    }
    private static void add (final List<EditingPagePresentation.Control> controls, final EditingPageState.Note note, final int column, final String name, final double value) { controls.add (control (column, name, percent (value), value, RING, note.touched ())); }
    private static void bipolar (final List<EditingPagePresentation.Control> controls, final EditingPageState.Note note, final int column, final String name, final double value) { controls.add (control (column, name, percent (value), (value + 1) / 2, RING, note.touched ())); }
    private static EditingPagePresentation.Control control (final int column, final String name, final String value, final double position, final EditingPagePresentation.Kind kind, final List<Boolean> touched) { return new EditingPagePresentation.Control (column, name, value, Math.max (0, Math.min (1, position)), kind, column < touched.size () && touched.get (column)); }
    private static void choice (final List<ChoiceCell> row, final int column, final String name, final boolean selected) { row.set (column, new ChoiceCell (name, true, selected)); }
    private static String onOff (final boolean value) { return value ? "On" : "Off"; }
    private static String occurrence (final String value)
    {
        return switch (value)
        {
            case "ALWAYS" -> "Always"; case "FIRST" -> "On First"; case "NOT_FIRST" -> "Never First";
            case "PREV" -> "With Previous"; case "NOT_PREV" -> "Without Previous";
            case "PREV_CHANNEL" -> "With Prev Channel"; case "NOT_PREV_CHANNEL" -> "Without Prev Channel";
            case "PREV_KEY" -> "With Prev Key"; case "NOT_PREV_KEY" -> "Without Prev Key";
            case "FILL" -> "Fill On"; case "NOT_FILL" -> "Fill Off"; default -> "";
        };
    }
    private static String percent (final double value)
    {
        return new java.text.DecimalFormat ("0.#", java.text.DecimalFormatSymbols.getInstance (Locale.ROOT)).format (value * 100) + "%";
    }
    private static String noteName (final int key) { return List.of ("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B").get (Math.floorMod (key, 12)) + (key / 12 - 2); }
    private static DisplayIcon icon (final EditingPageState.Track track)
    {
        if (track.pinned ()) return DisplayIcon.PIN;
        return switch (track.type ()) { case "AUDIO" -> DisplayIcon.AUDIO_TRACK; case "INSTRUMENT" -> DisplayIcon.INSTRUMENT_TRACK; case "HYBRID" -> DisplayIcon.HYBRID_TRACK; case "GROUP" -> track.expanded () ? DisplayIcon.GROUP_TRACK_OPEN : DisplayIcon.GROUP_TRACK; case "EFFECT" -> DisplayIcon.RETURN_TRACK; case "MASTER" -> DisplayIcon.MASTER; default -> null; };
    }
    static String measures (final int quarters, final double beats, final int offset, final boolean frames)
    {
        final int bar = (int) Math.floor (beats / Math.max (1, quarters));
        final double rest = beats - bar * quarters;
        final int beat = (int) Math.floor (rest);
        final int fraction = (int) Math.floor ((rest - beat) / 0.25);
        final String result = (bar + offset) + "." + (beat + offset) + "." + (fraction + offset);
        return frames ? result + String.format (Locale.ROOT, ":%03d", (int) Math.floor ((rest - beat - fraction * 0.25) * 400)) : result;
    }
}
