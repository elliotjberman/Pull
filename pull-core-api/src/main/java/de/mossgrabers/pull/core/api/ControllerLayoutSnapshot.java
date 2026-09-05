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
 * @param activeModeId Underlying mode, ignoring the manager's one temporary slot
 * @param previousModeId Previous underlying mode, or empty when unavailable
 * @param temporaryMode True when the visible mode occupies the temporary slot
 */
public record ControllerLayoutSnapshot (long generation, String viewId, String modeId, boolean drumLayoutActive, boolean drumControllerEngaged, int drumBaseMidiNote, GridPressureConfiguration gridPressure, DesiredNoteInputTranslation appliedNoteTranslation, String activeModeId, String previousModeId, boolean temporaryMode)
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
        appliedNoteTranslation = Objects.requireNonNull (appliedNoteTranslation, "appliedNoteTranslation");
        activeModeId = Objects.requireNonNull (activeModeId, "activeModeId");
        previousModeId = Objects.requireNonNull (previousModeId, "previousModeId");
    }


    public ControllerLayoutSnapshot (final long generation, final String viewId, final String modeId, final boolean drumLayoutActive, final boolean drumControllerEngaged, final int drumBaseMidiNote, final GridPressureConfiguration gridPressure)
    {
        this (generation, viewId, modeId, drumLayoutActive, drumControllerEngaged, drumBaseMidiNote, gridPressure, DesiredNoteInputTranslation.unowned ());
    }


    /** Compatibility construction for an ordinary visible mode without temporary metadata. */
    public ControllerLayoutSnapshot (final long generation, final String viewId, final String modeId, final boolean drumLayoutActive, final boolean drumControllerEngaged, final int drumBaseMidiNote, final GridPressureConfiguration gridPressure, final DesiredNoteInputTranslation appliedNoteTranslation)
    {
        this (generation, viewId, modeId, drumLayoutActive, drumControllerEngaged, drumBaseMidiNote, gridPressure, appliedNoteTranslation, modeId, "", false);
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
