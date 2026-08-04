// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

/**
 * Host-independent clip playback modes used when launching.
 */
public enum ClipLaunchMode
{
    /** Use the host/session default launch mode. */
    DEFAULT,

    /** Start at the clip's configured beginning. */
    TRIGGER_FROM_START,

    /** Continue from the playing clip's phase, falling back to the target clip's start. */
    LEGATO_FROM_CLIP_OR_START,

    /** Continue from the playing clip's phase, falling back to the project transport. */
    LEGATO_FROM_CLIP_OR_PROJECT,

    /** Start at the phase of the project transport. */
    LEGATO_FROM_PROJECT
}
