// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

/**
 * Stable identifiers for capabilities shared by the shell and reloadable core.
 */
public final class CoreCapabilities
{
    /** Core-owned page projection and ordered frozen legacy selection inbox. */
    public static final String CONTROLLER_PAGES = "controller.pages";

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

    /** Hardware-independent RGB light output; v7 adds a second pad colour and hardware blink rate. */
    public static final String OUTPUT_RGB_LIGHT = "output.rgb-light";

    /** Replayable projection onto bounded semantic host-learnable controller endpoints. */
    public static final String OUTPUT_CONTROLLER_MAPPING = "output.controller-mapping";

    /** Atomic composition of fixed view facets, Note layout, selected-track routing, and native translation. */
    public static final String OUTPUT_CONTROLLER_STATE = "output.controller-state";

    /** Selected-target-fenced persistence of one controller Note-view preference. */
    public static final String EFFECT_NOTE_VIEW_PREFERENCE = "effect.note-view-preference";

    /** Replayable lease over the stable note-repeat engine. */
    public static final String OUTPUT_NOTE_REPEAT = "output.note-repeat";

    /** Complete touch-strip hardware mode and 14-bit position with explicit output ownership. */
    public static final String OUTPUT_TOUCH_STRIP = "output.touch-strip";

    /** Normalized bounded Push controller inputs. */
    public static final String INPUT_CONTROLLER = "input.controller";

    /** Replayable input ownership; v8 freezes declared edge/motion companions through physical release. */
    public static final String ROUTING_CONTROLLER_INPUT = "routing.controller-input";

    /** Common bounded controller state; v16 combines Session preferences and raw attached-controller hardware identity. */
    public static final String SNAPSHOT_CONTROLLER_BRIDGE = "snapshot.controller-bridge";

    /** Replayable selection of bounded bridge-state domains to publish and sample. */
    public static final String SUBSCRIPTION_CONTROLLER_BRIDGE = "subscription.controller-bridge";

    /** Native DAW notification presentation with core-authored bounded text. */
    public static final String EFFECT_HOST_NOTIFICATION = "effect.host-notification";

    /** Absolute transport state/value, automation-write, and project-fenced native tap effects. */
    public static final String EFFECT_TRANSPORT = "effect.transport";

    /** Generation-fenced selected-track state, value, and action effects. */
    public static final String EFFECT_SELECTED_TRACK = "effect.selected-track";

    /** Generation-fenced actions against the active bounded Session bank; v4 adds exact launcher locations and primitive clip/scene operations. */
    public static final String EFFECT_SESSION_BANK = "effect.session-bank";
    /** Exact bounded current-bank track actions and main-bank parent navigation. */
    public static final String EFFECT_CURRENT_TRACK_BANK = "effect.current-track-bank";

    /** Generation-fenced selection of an installed controller mode. */
    public static final String EFFECT_CONTROLLER_MODE = "effect.controller-mode";

    /** Absolute installed controller-preference writes. */
    public static final String EFFECT_CONTROLLER_SETTINGS = "effect.controller-settings";

    /** Bounded application layout, panel, Arranger, and Mixer UI operations. */
    public static final String EFFECT_APPLICATION_UI = "effect.application-ui";

    /** Mechanical consumption of a stable compatibility button release; v4 also admits Browse and Stop Clip gesture consumption. */
    public static final String EFFECT_CONTROLLER_BUTTON_CONSUMPTION = "effect.controller-button-consumption";

    /** Generation-fenced drum-pad state, selection, and absolute bank-position effects. */
    public static final String EFFECT_DRUM_PAD = "effect.drum-pad";

    /** Stateful raw MIDI sent through Bitwig's ordinary permanent controller note input. */
    public static final String EFFECT_NOTE_INPUT_MIDI = "effect.note-input-midi";

    /** Pre-mutation events and bounded parameter-target snapshots; v5 reports shell-owned touch leases. */
    public static final String SNAPSHOT_PARAMETER_TARGETS = "snapshot.parameter-targets";

    /** Replayable exact parameter leases and generation-fenced absolute effects. */
    public static final String EFFECT_PARAMETER_TARGET = "effect.parameter-target";

    /** Authoritative Bitwig Boolean feedback for bounded semantic controller mappings. */
    public static final String SNAPSHOT_CONTROLLER_MAPPING_FEEDBACK = "snapshot.controller-mapping-feedback";

    /** Compare-and-set access to the installed bounded document storage slot. */
    public static final String EFFECT_CONTROLLER_MAPPING_STORAGE = "effect.controller-mapping-storage";

    /** Authoritative current-project and master-track snapshot. */
    public static final String SNAPSHOT_MASTER = "snapshot.master";

    /** Serialized project/file/history/engine commands and stable-owned exact cross-project transport. */
    public static final String EFFECT_MASTER = "effect.master";

    /** Complete replayable controller display; v3 projects composed scenes and v4 adds channel icons. */
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
