// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.graphics;

/**
 * Interface to pre-calculated grid dimensions.
 *
 * @author Jürgen Moßgraber
 */
public interface IGraphicsDimensions
{
    /**
     * Get the width of the graphics.
     *
     * @return The width
     */
    int getWidth ();


    /**
     * Get the height of the graphics.
     *
     * @return The height
     */
    int getHeight ();


    /**
     * Get the size of separators.
     *
     * @return The size
     */
    double getSeparatorSize ();


    /**
     * Get the height of a menu.
     *
     * @return The height
     */
    double getMenuHeight ();


}
