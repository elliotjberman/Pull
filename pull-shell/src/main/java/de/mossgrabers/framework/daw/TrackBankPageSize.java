// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.daw;


/**
 * Fixed page dimensions for an eagerly installed track bank.
 *
 * @param tracks Number of tracks
 * @param scenes Number of scenes
 */
public record TrackBankPageSize (int tracks, int scenes)
{
    /**
     * Validate the page size.
     */
    public TrackBankPageSize
    {
        if (tracks <= 0 || scenes <= 0)
            throw new IllegalArgumentException ("Track-bank page dimensions must be positive");
    }
}
