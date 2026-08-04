// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

/**
 * One-shot selected-track actions.
 */
public enum SelectedTrackAction
{
    /** Stop clip playback on the selected track. */
    STOP,

    /** Return the selected track to Arrangement playback. */
    RETURN_TO_ARRANGEMENT,

    /** Create and launch a new clip using the controller's configured default length. */
    CREATE_NEW_CLIP
}
