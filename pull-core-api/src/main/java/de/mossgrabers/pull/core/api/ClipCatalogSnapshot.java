// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable selected-track clip catalog with a generation fence for targeted effects.
 *
 * @param generation Monotonic shell generation for the catalog
 * @param clips Clips observed on requested pages, in deterministic scene order
 * @param scan Later read-back of the requested bounded window
 */
public record ClipCatalogSnapshot (long generation, List<CatalogClip> clips, ClipScanSnapshot scan)
{
    private static final ClipCatalogSnapshot EMPTY = new ClipCatalogSnapshot (0, List.of ());


    /**
     * Validate and copy the clip catalog.
     */
    public ClipCatalogSnapshot
    {
        scan = Objects.requireNonNull (scan, "scan");
        if (generation < 0)
            throw new IllegalArgumentException ("generation must not be negative");
        clips = List.copyOf (Objects.requireNonNull (clips, "clips"));

        final Set<ClipTargetId> targetIds = new HashSet<> ();
        for (final CatalogClip clip: clips)
        {
            if (!targetIds.add (clip.targetId ()))
                throw new IllegalArgumentException ("clips must have unique target IDs");
        }
    }


    /** Catalog without an active scan observation. */
    public ClipCatalogSnapshot (final long generation, final List<CatalogClip> clips)
    {
        this (generation, clips, ClipScanSnapshot.empty ());
    }


    /**
     * Get the initial empty clip catalog.
     *
     * @return The empty catalog
     */
    public static ClipCatalogSnapshot empty ()
    {
        return EMPTY;
    }
}
