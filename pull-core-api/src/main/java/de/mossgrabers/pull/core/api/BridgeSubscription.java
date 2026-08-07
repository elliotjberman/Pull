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
    /** Common transport state, including rate-limited playback position. */
    TRANSPORT,

    /** State of the private selection-following track target. */
    SELECTED_TRACK,

    /** Current visible view/mode and reconciled drum-layout state. */
    CONTROLLER_LAYOUT,

    /** The selected track's bounded 64-pad drum window and playing velocities. */
    DRUM_PADS,

    /** Current bounded parameter targets and authoritative values. */
    PARAMETERS,

    /** Current project, audio-engine, and master-track state. */
    MASTER,

    /** Lightweight current-project identity, engine, navigation, and command state. */
    PROJECT
}
