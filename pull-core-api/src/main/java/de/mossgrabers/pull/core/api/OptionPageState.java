// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import java.util.List;

/** Bounded observed data for legacy option/list pages; no drawing or input recipes cross this seam. */
public sealed interface OptionPageState extends ControllerPageDisplayState
{
    record Empty () implements OptionPageState { }
    static OptionPageState empty () { return new Empty (); }

    record Scales (List<String> names, int selectedScale, List<String> roots, int selectedRoot, boolean chromatic, String range) implements OptionPageState
    {
        public Scales { names = bounded (names, 128); roots = bounded (roots, 12); }
    }
    record ScaleLayout (List<String> names, int selected) implements OptionPageState
    {
        public ScaleLayout { names = bounded (names, 12); }
    }
    record FixedLength (int selected) implements OptionPageState { }
    record AddTrack (String mode, List<String> shortcuts) implements OptionPageState
    {
        public AddTrack { shortcuts = bounded (shortcuts, 7); }
    }
    record BrowserColumn (boolean exists, String name, boolean cursorExists, String cursorName, String wildcard) { }
    record BrowserItem (boolean exists, String name, boolean selected, int hits) { }
    record Browser (boolean active, int selectionMode, int selectedColumn, String info, String selectedResult, String contentType, boolean preview,
                    List<BrowserColumn> columns, List<BrowserItem> items) implements OptionPageState
    {
        public Browser { columns = bounded (columns, 7); items = bounded (items, 48); }
    }
    record Parameter (String name, int value, int upperBound, String displayedValue, boolean touched) { }
    record NoteRepeat (boolean available, double period, double length, boolean latch, boolean pressure, boolean freeRunning, boolean shuffle,
                       List<String> modes, int modeIndex, String modeName, int octaves, boolean modeTouched, boolean octavesTouched, Parameter shuffleAmount, Parameter groove) implements OptionPageState
    {
        public NoteRepeat { modes = bounded (modes, 128); }
    }
    private static <T> List<T> bounded (final List<T> values, final int maximum)
    {
        if (values.size () > maximum) throw new IllegalArgumentException ("Option page data exceeds capacity " + maximum);
        return List.copyOf (values);
    }
}
