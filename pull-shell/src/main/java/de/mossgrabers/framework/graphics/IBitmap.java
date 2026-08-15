// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.graphics;

/**
 * An interface to a bitmap.
 *
 * @author Jürgen Moßgraber
 */
public interface IBitmap
{
    /**
     * Render the content of the bitmap.
     *
     * @param enableAntialias True to enable anti aliasing
     * @param renderer The renderer to draw on the bitmap
     */
    void render (boolean enableAntialias, IRenderer renderer);


    /**
     * Encode the bitmap data into a different format.
     *
     * @param encoder The encoder to use
     */
    void encode (IEncoder encoder);
}
