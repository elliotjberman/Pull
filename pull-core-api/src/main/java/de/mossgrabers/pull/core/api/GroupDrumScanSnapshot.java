// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

import java.util.List;

/** Bounded child-track observations and the current shared scanner's acknowledged result. */
public record GroupDrumScanSnapshot (long selectionGeneration, String selectionId, long revision,
    List<Child> children, boolean limited, String scannedTrackId, boolean complete, boolean hasDrums)
{
    public static final int CAPACITY = 8;
    public record Child (String channelId, String name, boolean canHoldNotes) { }
    public GroupDrumScanSnapshot
    {
        java.util.Objects.requireNonNull (selectionId);
        java.util.Objects.requireNonNull (scannedTrackId);
        children = List.copyOf (children);
        if (children.size () > CAPACITY) throw new IllegalArgumentException ("Child window exceeds capacity");
    }
    public static GroupDrumScanSnapshot empty () { return new GroupDrumScanSnapshot (0, "", 0, List.of (), false, "", false, false); }
}
