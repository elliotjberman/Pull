// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

/**
 * Stable identifiers for capabilities shared by the shell and reloadable core.
 */
public final class CoreCapabilities
{
    /** Normalized input for the drum-fill control. */
    public static final String INPUT_DRUM_FILL = "input.drum-fill";

    /** Immutable ordered clip catalogs for the selected track. */
    public static final String SNAPSHOT_SELECTED_TRACK_CLIPS = "snapshot.selected-track-clips";

    /** Persistent logical-control to clip-target binding requests. */
    public static final String BINDING_CLIP_TARGET = "binding.clip-target";

    /** Owner-scoped momentary clip launch and release effects with a frozen launch policy. */
    public static final String EFFECT_CLIP_LAUNCH_HOLD = "effect.clip-launch-hold";

    /** Hardware-independent RGB light output. */
    public static final String OUTPUT_RGB_LIGHT = "output.rgb-light";


    private CoreCapabilities ()
    {
        // Utility class
    }
}
