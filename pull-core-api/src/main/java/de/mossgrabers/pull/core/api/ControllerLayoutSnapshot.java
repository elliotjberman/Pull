// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

import java.util.Objects;

/**
 * Stable-shell layout and applicability state, kept separate from selected-track capability.
 *
 * @param viewId Stable visible-view identifier, or empty when unavailable
 * @param modeId Stable visible-mode identifier, or empty when unavailable
 * @param drumLayoutActive True while the visible layout owns the drum controls
 * @param drumControllerEngaged True after the shell has reconciled drum-controller engagement
 */
public record ControllerLayoutSnapshot (String viewId, String modeId, boolean drumLayoutActive, boolean drumControllerEngaged)
{
    private static final ControllerLayoutSnapshot EMPTY = new ControllerLayoutSnapshot ("", "", false, false);


    /**
     * Validate layout state.
     */
    public ControllerLayoutSnapshot
    {
        viewId = Objects.requireNonNull (viewId, "viewId");
        modeId = Objects.requireNonNull (modeId, "modeId");
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
