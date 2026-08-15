// Written by Jürgen Moßgraber - mossgrabers.de
// (c) 2017-2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.framework.controller.grid;

import java.util.Objects;


/**
 * An unresolved pad lighting state. A null blink color means that blinking is disabled.
 *
 * @param color The main pad color
 * @param blinkColor The optional blink color
 * @param fast Blink fast if true
 */
public record PadLight (PadColor color, PadColor blinkColor, boolean fast)
{
    /** Validate the main color. */
    public PadLight
    {
        Objects.requireNonNull (color, "color");
    }


    /**
     * Create a non-blinking pad light.
     *
     * @param color The main pad color
     */
    public PadLight (final PadColor color)
    {
        this (color, null, false);
    }
}
