// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.graphics;

import de.mossgrabers.framework.controller.color.ColorEx;


/**
 * Interface to color and font configurations for drawing.
 *
 * @author Jürgen Moßgraber
 */
public interface IGraphicsConfiguration
{
    /**
     * Get the text color of an element.
     *
     * @return The text color of an element.
     */
    ColorEx getColorText ();


    /**
     * Get the background color of an element.
     *
     * @return The background color of an element.
     */
    ColorEx getColorBackground ();


    /**
     * Get the background darker color of an element.
     *
     * @return The background color of an element.
     */
    ColorEx getColorBackgroundDarker ();


    /**
     * Get the background lighter color of an element.
     *
     * @return The background color of an element.
     */
    ColorEx getColorBackgroundLighter ();


    /**
     * Get the border color of an element.
     *
     * @return The border color of an element.
     */
    ColorEx getColorBorder ();


    /**
     * Should anti-aliasing be applied?
     *
     * @return True if enabled
     */
    boolean isAntialiasEnabled ();
}
