// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt
package de.mossgrabers.pull.core.api;

/** One descendant scan request, fenced by actual selection and the observed child catalog. */
public record DesiredDrumTarget (long selectionGeneration, String selectionId, long childrenRevision, String trackId)
{
    public DesiredDrumTarget
    {
        java.util.Objects.requireNonNull (selectionId);
        java.util.Objects.requireNonNull (trackId);
        if (selectionGeneration < 0 || childrenRevision < 0 || !trackId.isEmpty () && (selectionGeneration == 0 || selectionId.isEmpty ()))
            throw new IllegalArgumentException ("A child target requires its selected group identity");
    }
    public boolean active () { return !this.trackId.isEmpty (); }
    public static DesiredDrumTarget inactive () { return new DesiredDrumTarget (0, "", 0, ""); }
}
