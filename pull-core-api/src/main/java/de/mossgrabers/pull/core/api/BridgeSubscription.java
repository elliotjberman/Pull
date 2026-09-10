// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api;

/**
 * One bounded stable-shell state domain requested by the active reloadable core.
 *
 * <p>The Bitwig proxies and interested values are still created during extension initialization.
 * A subscription controls publication and sampling cost at the shell/core boundary; it does not
 * create new Bitwig API topology.</p>
 */
public enum BridgeSubscription
{
    /** Bounded compatibility page requests; selection and history are core-local state. */
    BROWSER,
    CONTROLLER_PAGES,

    /** Native application panel layout and Arranger/Mixer options. */
    APPLICATION_UI,

    /** Common transport state, including rate-limited playback position. */
    TRANSPORT,

    /** Project metronome tick and pre-roll settings. */
    TRANSPORT_SETTINGS,

    /** Unified Automation Write read-back and touch-release preference. */
    AUTOMATION,
    /** Raw encoder calibration and user sensitivity preferences. */
    ENCODER_CONFIGURATION,

    /** Controller preferences and the existing model-cursor send metadata window. */
    CONTROLLER_SETTINGS,

    /** Raw identity read-back for the one attached controller surface. */
    CONTROLLER_HARDWARE,

    /** Active legacy page observations, sampled only while its display is selected. */
    CONTROLLER_PAGE_DISPLAY,

    /** State of the private selection-following track target. */
    SELECTED_TRACK,

    /** Core-requested page of the private selected-track clip scanner. */
    SELECTED_TRACK_CLIPS,

    /** The active bounded Session bank and its visible track identities. */
    SESSION_BANK,

    /** Eight tracks in the current main/effect bank and its navigation cursor. */
    CURRENT_TRACK_BANK,

    /** The active Session bank clip slots and scenes. */
    SESSION_CLIPS,

    /** Current visible view/mode and reconciled drum-layout state. */
    CONTROLLER_LAYOUT,

    /** Selected-target-fenced per-track note-view preference. */
    NOTE_VIEW,

    /** Live note-repeat read-back and drum-roll setting. */
    NOTE_REPEAT,

    /** The selected track's bounded 64-pad drum window and playing velocities. */
    DRUM_PADS,

    /** Current bounded parameter targets and authoritative values. */
    PARAMETERS,

    /** Bitwig Boolean feedback for installed semantic controller-mapping endpoints. */
    CONTROLLER_MAPPING_FEEDBACK,

    /** Current project, audio-engine, and master-track state. */
    MASTER,

    /** Lightweight current-project identity, engine, navigation, and command state. */
    PROJECT
}
