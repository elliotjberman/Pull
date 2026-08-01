// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.effect;

/**
 * Selects which session-configured release action a clip-launch hold invokes.
 */
public enum ClipReleaseTrigger
{
    /** Invoke the clip's Main release action. */
    MAIN,

    /** Invoke the clip's ALT release action. */
    ALTERNATE
}
