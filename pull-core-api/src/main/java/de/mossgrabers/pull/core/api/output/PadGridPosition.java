// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;


/**
 * One position on Push's bounded eight-by-eight pad grid.
 *
 * @param column Zero-based column from the left
 * @param row Zero-based row from the bottom
 */
public record PadGridPosition (int column, int row)
{
    /** Validate the fixed Push grid bounds. */
    public PadGridPosition
    {
        if (column < 0 || column >= 8)
            throw new IllegalArgumentException ("column must be between 0 and 7");
        if (row < 0 || row >= 8)
            throw new IllegalArgumentException ("row must be between 0 and 7");
    }
}
