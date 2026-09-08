// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.page;

import java.util.List;

/** Repeat settings, visible timing choices and parameter values, supplied after read-back. */
public record NoteRepeatPagePresentation (boolean available, List<String> resolutions, int period, int length, boolean latch, boolean pressure, boolean synced, boolean shuffle,
                                         String grooveLabel, boolean grooveEnabled, Parameter mode, Parameter octaves, Parameter shuffleAmount)
{
    public record Parameter (String name, String text, double value, boolean touched) { }
    public NoteRepeatPagePresentation { resolutions = List.copyOf (resolutions); if (resolutions.size () > 128) throw new IllegalArgumentException ("Too many repeat resolutions"); }
}
