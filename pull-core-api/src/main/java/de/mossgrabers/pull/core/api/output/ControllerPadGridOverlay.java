// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.output;

import java.util.Map;
import java.util.Objects;


/**
 * Temporary sparse overlay for Push's complete pad grid.
 *
 * <p>While active, the stable shell freezes the underlying grid frame. Positions in
 * {@link #colors()} replace that frozen frame; absent positions continue showing the frozen
 * color. Deactivating the overlay reveals the latest stable frame again.</p>
 *
 * <p>This is an exceptional full-surface transition plane, currently used by the cross-project
 * Play wave. Ordinary view content must claim its pad regions and compose through normal view
 * output instead of using an overlay to bypass surface ownership.</p>
 *
 * @param active True while the stable frame must remain frozen
 * @param colors Sparse overlay colors by physical grid position
 */
public record ControllerPadGridOverlay (boolean active, Map<PadGridPosition, RgbColor> colors)
{
    private static final ControllerPadGridOverlay INACTIVE = new ControllerPadGridOverlay (false, Map.of ());


    /** Validate and copy the bounded overlay. */
    public ControllerPadGridOverlay
    {
        colors = Map.copyOf (Objects.requireNonNull (colors, "colors"));
        if (!active && !colors.isEmpty ())
            throw new IllegalArgumentException ("An inactive pad-grid overlay cannot contain colors");
        if (colors.size () > 64)
            throw new IllegalArgumentException ("A pad-grid overlay cannot contain more than 64 positions");
    }


    /** Get the inactive overlay. */
    public static ControllerPadGridOverlay inactive ()
    {
        return INACTIVE;
    }
}
