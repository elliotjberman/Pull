// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.ui.component;

/** Bounds fitting work for dense displays while retaining the full text in presentation data. */
public final class TextContent
{
    private TextContent () { }
    public static String candidate (final String value, final int maximum)
    {
        if (value == null) return "";
        if (value.length () <= maximum) return value;
        int end = maximum - 3;
        if (Character.isHighSurrogate (value.charAt (end - 1))) end--;
        return value.substring (0, end) + "...";
    }
}
