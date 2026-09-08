// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.graphics;

/**
 * Default implementation of pre-calculated grid dimensions.
 *
 * @author Jürgen Moßgraber
 */
public class DefaultGraphicsDimensions implements IGraphicsDimensions
{
    /** The size to use for separator spacing. */
    private static final double SEPARATOR_SIZE = 2.0;

    private final int           width;
    private final int           height;

    /** The height of the menu on top. */
    private final double        menuHeight;


    /**
     * Constructor.
     *
     * @param width The full width of the drawing area
     * @param height The full height of the drawing area
     */
    public DefaultGraphicsDimensions (final int width, final int height)
    {
        this.width = width;
        this.height = height;

        this.menuHeight = height / 12.0 + 2.0 * SEPARATOR_SIZE;

    }


    /** {@inheritDoc} */
    @Override
    public int getWidth ()
    {
        return this.width;
    }


    /** {@inheritDoc} */
    @Override
    public int getHeight ()
    {
        return this.height;
    }


    /** {@inheritDoc} */
    @Override
    public double getSeparatorSize ()
    {
        return SEPARATOR_SIZE;
    }


    /** {@inheritDoc} */
    @Override
    public double getMenuHeight ()
    {
        return this.menuHeight;
    }


}
