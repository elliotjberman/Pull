// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

/**
 * Absolute boolean transport states exposed by the stable bridge.
 */
public enum TransportState
{
    /** Playback state. */
    PLAYING,

    /** Arranger recording state. */
    RECORDING,

    /** Arranger overdub state. */
    ARRANGER_OVERDUB,

    /** Clip-launcher overdub state. */
    LAUNCHER_OVERDUB,

    /** Arranger loop state. */
    LOOP,

    /** Metronome state. */
    METRONOME,

    /** Global fill-mode state. */
    FILL_MODE
}
