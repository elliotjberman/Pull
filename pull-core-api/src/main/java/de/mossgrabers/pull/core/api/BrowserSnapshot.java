// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

/** Raw Bitwig browser activity; generation advances on each observed activity transition. */
public record BrowserSnapshot (long generation, boolean active)
{
    private static final BrowserSnapshot EMPTY = new BrowserSnapshot (0, false);
    public BrowserSnapshot
    {
        if (generation < 0 || generation == 0 && active) throw new IllegalArgumentException ("Browser state needs a positive observation generation");
    }
    public boolean available () { return this.generation > 0; }
    public static BrowserSnapshot empty () { return EMPTY; }
}
