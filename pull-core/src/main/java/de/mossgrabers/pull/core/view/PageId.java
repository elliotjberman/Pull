// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.view;

import java.util.Objects;

/** Core-owned semantic page identity. Adding a page never extends a parent-loaded enum. */
public record PageId (String value)
{
    public static final PageId PROJECT_MACROS = new PageId ("project-macros");
    public static final PageId TRACK = new PageId ("track");
    public static final PageId MASTER = new PageId ("master");
    public static final PageId VOLUME = new PageId ("volume");
    public static final PageId PAN = new PageId ("pan");
    public static final PageId METRONOME = new PageId ("metronome");
    public static final PageId AUTOMATION = new PageId ("automation");
    public static final PageId ACCENT = new PageId ("accent");
    public static final PageId SETUP = new PageId ("setup");
    public static final PageId RIBBON = new PageId ("ribbon");
    public static final PageId INFO = new PageId ("info");
    public static final PageId FRAME = new PageId ("frame");

    public PageId
    {
        value = Objects.requireNonNull (value, "value");
        if (!value.matches ("[a-z][a-z0-9-]{0,63}"))
            throw new IllegalArgumentException ("Page ID must be a bounded lowercase semantic identifier");
    }

    public static PageId send (final int index)
    {
        if (index < 0 || index >= 8)
            throw new IllegalArgumentException ("Send page index must be between 0 and 7");
        return new PageId ("send-" + (index + 1));
    }
}
