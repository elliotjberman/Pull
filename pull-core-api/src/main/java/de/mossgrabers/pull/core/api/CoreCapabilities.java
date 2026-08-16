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

    /** Hardware-independent RGB light output; v4 admits the four drum user-control pads. */
    public static final String OUTPUT_RGB_LIGHT = "output.rgb-light";

    /** Atomic composition of fixed view facets, Note layout, and selected-track note routing. */
    public static final String OUTPUT_CONTROLLER_STATE = "output.controller-state";

    /** Selected-target-fenced persistence of one controller Note-view preference. */
    public static final String EFFECT_NOTE_VIEW_PREFERENCE = "effect.note-view-preference";

    /** Replayable lease over the stable note-repeat engine. */
    public static final String OUTPUT_NOTE_REPEAT = "output.note-repeat";

    /** Normalized bounded Push controller inputs. */
    public static final String INPUT_CONTROLLER = "input.controller";

    /** Replayable core ownership of normalized controller inputs; v3 admits drum user controls. */
    public static final String ROUTING_CONTROLLER_INPUT = "routing.controller-input";

    /** Common bounded controller state; v6 adds the user-control bank domain. */
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

    /** Pre-mutation events and authoritative bounded parameter-target snapshots. */
    public static final String SNAPSHOT_PARAMETER_TARGETS = "snapshot.parameter-targets";

    /** Replayable exact parameter leases and generation-fenced absolute effects. */
    public static final String EFFECT_PARAMETER_TARGET = "effect.parameter-target";

    /** Four authoritative Bitwig user-control values. */
    public static final String SNAPSHOT_USER_CONTROLS = "snapshot.user-controls";

    /** Absolute writes to the bounded Bitwig user-control bank. */
    public static final String EFFECT_USER_CONTROL = "effect.user-control";

    /** Authoritative current-project and master-track snapshot. */
    public static final String SNAPSHOT_MASTER = "snapshot.master";

    /** Serialized project/file/engine commands and stable-owned exact cross-project transport. */
    public static final String EFFECT_MASTER = "effect.master";

    /** Complete replayable controller display; v2 adds bounded declarative vector scenes. */
    public static final String OUTPUT_CONTROLLER_DISPLAY = "output.controller-display";

    /** Temporary sparse overlay over a frozen Push pad-grid frame. */
    public static final String OUTPUT_PAD_GRID_OVERLAY = "output.pad-grid-overlay";

    /** Temporary complete scene composed above the current Push display page. */
    public static final String OUTPUT_DISPLAY_OVERLAY = "output.display-overlay";

    /** Pure child-core rendering of authoritative stable-adapter mixer data. */
    public static final String RENDER_MIXER_CONTROLS = "render.mixer-controls";

    private CoreCapabilities ()
    {
        // Utility class
    }
}
