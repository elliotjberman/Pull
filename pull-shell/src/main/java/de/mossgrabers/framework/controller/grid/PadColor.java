// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller.grid;

import java.util.Objects;

import de.mossgrabers.framework.controller.color.ColorEx;


/**
 * A pad color before it is resolved to a controller-specific palette index.
 */
public sealed interface PadColor permits PadColor.Indexed, PadColor.Registered, PadColor.Rgb
{
    /**
     * Create an already resolved controller color.
     *
     * @param index The controller color index
     * @return The pad color
     */
    static PadColor indexed (final int index)
    {
        return new Indexed (index);
    }


    /**
     * Create a registered semantic color.
     *
     * @param id The registered color ID
     * @return The pad color
     */
    static PadColor registered (final String id)
    {
        return new Registered (id);
    }


    /**
     * Create an exact RGB color.
     *
     * @param color The RGB color
     * @return The pad color
     */
    static PadColor rgb (final ColorEx color)
    {
        return new Rgb (color);
    }


    /**
     * Create an RGB color whose zero value represents a disabled output, as used by core-owned
     * controller lights. DAW black must use {@link #rgb(ColorEx)} so it remains visibly distinct
     * from off.
     *
     * @param color The RGB color
     * @return Off for zero RGB, otherwise the exact RGB color
     */
    static PadColor rgbOrOff (final ColorEx color)
    {
        Objects.requireNonNull (color, "color");
        return color.getRed () == 0.0 && color.getGreen () == 0.0 && color.getBlue () == 0.0 ? registered (IPadGrid.GRID_OFF) : rgb (color);
    }


    /** An already resolved controller color index. */
    record Indexed (int index) implements PadColor
    {
        /** Validate the controller color index. */
        public Indexed
        {
            if (index < 0 || index > 127)
                throw new IllegalArgumentException ("index must be in the range of 0..127");
        }
    }


    /** A registered semantic color ID. */
    record Registered (String id) implements PadColor
    {
        /** Validate the registered color ID. */
        public Registered
        {
            Objects.requireNonNull (id, "id");
        }
    }


    /** An exact RGB color. */
    record Rgb (ColorEx color) implements PadColor
    {
        /** Validate the RGB color. */
        public Rgb
        {
            Objects.requireNonNull (color, "color");
        }
    }
}
