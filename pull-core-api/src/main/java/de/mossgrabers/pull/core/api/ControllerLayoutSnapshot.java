// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/**
 * Stable-shell layout and applicability state, kept separate from selected-track capability.
 *
 * @param generation Monotonic generation advanced when the stable visible layout changes
 * @param viewId Stable visible-view identifier, or empty when unavailable
 * @param modeId Stable visible-mode identifier, or empty when unavailable
 * @param drumLayoutActive True while the visible layout owns the drum controls
 * @param drumControllerEngaged True after the shell has reconciled drum-controller engagement
 * @param drumBaseMidiNote First MIDI note in the active drum-controller mapping
 * @param gridPressure User-selected routing for grid pressure
 */
public record ControllerLayoutSnapshot (long generation, String viewId, String modeId, boolean drumLayoutActive, boolean drumControllerEngaged, int drumBaseMidiNote, GridPressureConfiguration gridPressure)
{
    private static final ControllerLayoutSnapshot EMPTY = new ControllerLayoutSnapshot (0, "", "", false, false, 0, GridPressureConfiguration.OFF);


    /**
     * Validate layout state.
     */
    public ControllerLayoutSnapshot
    {
        if (generation < 0)
            throw new IllegalArgumentException ("generation must not be negative");
        viewId = Objects.requireNonNull (viewId, "viewId");
        modeId = Objects.requireNonNull (modeId, "modeId");
        if (drumBaseMidiNote < 0 || drumBaseMidiNote > 127)
            throw new IllegalArgumentException ("drumBaseMidiNote must be between 0 and 127");
        gridPressure = Objects.requireNonNull (gridPressure, "gridPressure");
    }


    /**
     * Get unavailable layout state.
     *
     * @return Empty layout state
     */
    public static ControllerLayoutSnapshot empty ()
    {
        return EMPTY;
    }
}
