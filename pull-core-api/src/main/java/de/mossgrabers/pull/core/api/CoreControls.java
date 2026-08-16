// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.List;

/**
 * Logical controller identifiers whose physical mapping is owned by the stable shell.
 */
public final class CoreControls
{
    /** First momentary drum-fill slot. */
    public static final ControlId DRUM_FILL_1 = new ControlId ("drum.fill.1");

    /** Second momentary drum-fill slot. */
    public static final ControlId DRUM_FILL_2 = new ControlId ("drum.fill.2");

    /** Third momentary drum-fill slot. */
    public static final ControlId DRUM_FILL_3 = new ControlId ("drum.fill.3");

    /** Fourth momentary drum-fill slot. */
    public static final ControlId DRUM_FILL_4 = new ControlId ("drum.fill.4");

    /** Fifth momentary drum-fill slot. */
    public static final ControlId DRUM_FILL_5 = new ControlId ("drum.fill.5");

    /** Sixth momentary drum-fill slot. */
    public static final ControlId DRUM_FILL_6 = new ControlId ("drum.fill.6");

    /** Seventh momentary drum-fill slot. */
    public static final ControlId DRUM_FILL_7 = new ControlId ("drum.fill.7");

    /** Eighth momentary drum-fill slot. */
    public static final ControlId DRUM_FILL_8 = new ControlId ("drum.fill.8");

    /** All drum-fill slots in physical display order. */
    public static final List<ControlId> DRUM_FILLS = List.of (
        DRUM_FILL_1,
        DRUM_FILL_2,
        DRUM_FILL_3,
        DRUM_FILL_4,
        DRUM_FILL_5,
        DRUM_FILL_6,
        DRUM_FILL_7,
        DRUM_FILL_8);

    /** Four Bitwig-mappable Boolean control pads in left-to-right order. */
    public static final List<ControlId> DRUM_USER_CONTROLS = List.of (
        PushControlIds.pad (29),
        PushControlIds.pad (30),
        PushControlIds.pad (31),
        PushControlIds.pad (32));

    /** Four physical drum-rate pads in left-to-right order. */
    public static final List<ControlId> DRUM_RATES = List.of (
        PushControlIds.pad (5),
        PushControlIds.pad (6),
        PushControlIds.pad (7),
        PushControlIds.pad (8));


    private CoreControls ()
    {
        // Utility class
    }


    /**
     * Get the drum-fill controls in physical display order.
     *
     * @return The immutable ordered controls
     */
    public static List<ControlId> drumFills ()
    {
        return DRUM_FILLS;
    }
}
