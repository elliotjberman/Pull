// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.List;
import java.util.stream.IntStream;


/** Stable semantic mapping endpoints installed by the parent shell. */
public final class CoreControllerMappings
{
    /** Legacy shared endpoints, retained by the shell without physical matchers. */
    public static final List<ControllerMappingId> DRUM_CONTROL_PADS = List.of (
        new ControllerMappingId ("drum-controller.control.1"),
        new ControllerMappingId ("drum-controller.control.2"),
        new ControllerMappingId ("drum-controller.control.3"),
        new ControllerMappingId ("drum-controller.control.4"));

    /** Maximum historical track identities retained per document. */
    public static final int TRACK_BANK_COUNT = 128;

    /** Permanent semantic endpoints per track bank. */
    public static final int CONTROLS_PER_TRACK = 4;

    /** Complete initialization-owned per-track endpoint inventory in bank/slot order. */
    public static final List<ControllerMappingId> TRACK_CONTROL_PADS = IntStream.range (0, TRACK_BANK_COUNT * CONTROLS_PER_TRACK)
        .mapToObj (index -> new ControllerMappingId ("drum-controller.track." + (index / CONTROLS_PER_TRACK + 1) + ".control." + (index % CONTROLS_PER_TRACK + 1)))
        .toList ();


    /** Get one zero-based permanent track bank in left-to-right pad order. */
    public static List<ControllerMappingId> trackBank (final int bank)
    {
        if (bank < 0 || bank >= TRACK_BANK_COUNT)
            throw new IllegalArgumentException ("controller mapping track bank is out of range");
        return TRACK_CONTROL_PADS.subList (bank * CONTROLS_PER_TRACK, (bank + 1) * CONTROLS_PER_TRACK);
    }


    private CoreControllerMappings ()
    {
        // Utility class
    }
}
