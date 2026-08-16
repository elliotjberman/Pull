// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import de.mossgrabers.pull.core.api.output.RgbColor;

import java.util.Collections;
import java.util.List;
import java.util.Objects;


/** Authoritative Bitwig manual-mapping feedback for the four Drum Controller control pads. */
public record MappedPadLightsSnapshot (boolean available, List<Pad> pads)
{
    /** Fixed control-pad capacity installed during extension initialization. */
    public static final int CAPACITY = 4;

    private static final Pad UNMAPPED = new Pad (false, new RgbColor (0, 0, 0));
    private static final MappedPadLightsSnapshot EMPTY = new MappedPadLightsSnapshot (false, Collections.nCopies (CAPACITY, UNMAPPED));


    /** Validate and copy one complete bounded grid sample. */
    public MappedPadLightsSnapshot
    {
        pads = List.copyOf (Objects.requireNonNull (pads, "pads"));
        if (pads.size () != CAPACITY)
            throw new IllegalArgumentException ("mapped pad lights must contain exactly four values");
    }


    /** Get one control pad's feedback by zero-based slot. */
    public Pad controlPad (final int slot)
    {
        if (slot < 0 || slot >= CAPACITY)
            throw new IllegalArgumentException ("control-pad slot must be between zero and three");
        return this.pads.get (slot);
    }


    /** Get an unavailable grid sample. */
    public static MappedPadLightsSnapshot empty ()
    {
        return EMPTY;
    }


    /** One Bitwig-owned mapping-light sample. */
    public record Pad (boolean mapped, RgbColor color)
    {
        /** Validate the immutable color. */
        public Pad
        {
            color = Objects.requireNonNull (color, "color");
        }
    }
}
