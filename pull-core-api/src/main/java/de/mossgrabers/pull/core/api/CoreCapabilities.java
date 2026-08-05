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

    /** Frozen targets and authoritative active owner of the shell-managed clip-launch session. */
    public static final String SNAPSHOT_CLIP_LAUNCH_SESSION = "snapshot.clip-launch-session";

    /** Single-active momentary clip-session effects with a frozen launch policy per target. */
    public static final String EFFECT_CLIP_LAUNCH_HOLD = "effect.clip-launch-hold";

    /** Hardware-independent RGB light output. */
    public static final String OUTPUT_RGB_LIGHT = "output.rgb-light";

    /** Complete selection of fixed Push 2 view facets. */
    public static final String OUTPUT_CONTROLLER_WORKSPACE = "output.controller-workspace";

    /** Normalized bounded Push controller inputs. */
    public static final String INPUT_CONTROLLER = "input.controller";

    /** Replayable core ownership of normalized controller inputs. */
    public static final String ROUTING_CONTROLLER_INPUT = "routing.controller-input";

    /** Common bounded transport, selected-track, layout, and drum state. */
    public static final String SNAPSHOT_CONTROLLER_BRIDGE = "snapshot.controller-bridge";

    /** Replayable selection of bounded bridge-state domains to publish and sample. */
    public static final String SUBSCRIPTION_CONTROLLER_BRIDGE = "subscription.controller-bridge";

    /** Absolute transport state and value effects. */
    public static final String EFFECT_TRANSPORT = "effect.transport";

    /** Generation-fenced selected-track state, value, and action effects. */
    public static final String EFFECT_SELECTED_TRACK = "effect.selected-track";

    /** Generation-fenced drum-pad state and selection effects. */
    public static final String EFFECT_DRUM_PAD = "effect.drum-pad";

    /** Stateful raw MIDI sent through Bitwig's ordinary permanent controller note input. */
    public static final String EFFECT_NOTE_INPUT_MIDI = "effect.note-input-midi";

    private CoreCapabilities ()
    {
        // Utility class
    }
}
