// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;


/**
 * Declared Session track/scene window required by a controller view.
 *
 * @param tracks Number of visible track columns
 * @param scenes Number of visible scene rows
 */
public record SessionBankShape (int tracks, int scenes)
{
    private static final SessionBankShape EMPTY = new SessionBankShape (0, 0);


    /**
     * Validate the shape. Zero-by-zero represents no Session bank request.
     */
    public SessionBankShape
    {
        if (tracks < 0 || scenes < 0 || (tracks == 0) != (scenes == 0))
            throw new IllegalArgumentException ("Session bank shape must be empty or have positive track and scene counts");
    }


    /**
     * Get the empty shape.
     *
     * @return Empty shape
     */
    public static SessionBankShape empty ()
    {
        return EMPTY;
    }


    /**
     * Test whether this value requests a Session bank.
     *
     * @return True for a non-empty shape
     */
    public boolean isPresent ()
    {
        return this.tracks > 0;
    }
}
