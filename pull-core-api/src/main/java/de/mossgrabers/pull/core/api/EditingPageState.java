// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.output.RgbColor;
import java.util.List;

/** Observed clip/note and editing preferences. No submitted values or drawing recipes. */
public sealed interface EditingPageState extends ControllerPageDisplayState
{
    record Empty () implements EditingPageState { }
    static EditingPageState empty () { return new Empty (); }

    record Track (boolean exists, String name, String type, RgbColor color, boolean selected, boolean active, boolean expanded, boolean pinned) { }
    record Clip (boolean exists, boolean pinned, double playStart, double playEnd, double loopStart, double loopLength,
                 boolean loopEnabled, boolean shuffle, double accent, int quartersPerMeasure,
                 List<Track> tracks, List<Boolean> touched, boolean pianoRoll) implements EditingPageState
    {
        public Clip { tracks = window (tracks); touched = window (touched); }
    }
    record Quantize (boolean trackExists, int grid, boolean noteLength, int amount) implements EditingPageState { }
    record Parameter (boolean exists, String name, double value, String displayedValue, boolean touched) { }
    record Groove (boolean enabled, List<Parameter> parameters) implements EditingPageState
    {
        public Groove
        {
            parameters = List.copyOf (parameters);
            if (parameters.size () > 5) throw new IllegalArgumentException ("Groove has five parameter slots");
        }
    }
    record Note (boolean exists, String page, int count, int step, int key, int quartersPerMeasure, double transposeRange,
                 boolean shift, List<Boolean> touched, NoteData data) implements EditingPageState
    {
        public Note { touched = window (touched); java.util.Objects.requireNonNull (data); }
    }
    record NoteData (double duration, boolean muted, double velocity, double velocitySpread, double releaseVelocity,
                     boolean chanceEnabled, double chance, boolean occurrenceEnabled, String occurrence,
                     boolean recurrenceEnabled, int recurrenceLength, int recurrenceMask,
                     double gain, double pan, double transpose, double timbre, double pressure,
                     boolean repeatEnabled, int repeatCount, double repeatCurve, double repeatVelocityCurve, double repeatVelocityEnd)
    {
        public static NoteData empty () { return new NoteData (0, false, 0, 0, 0, false, 0, false, "", false, 0, 0, 0, 0, 0, 0, 0, false, 0, 0, 0, 0); }
    }
    private static <T> List<T> window (final List<T> data)
    {
        if (data.size () > 8) throw new IllegalArgumentException ("Editing window exceeds eight slots");
        return List.copyOf (data);
    }
}
