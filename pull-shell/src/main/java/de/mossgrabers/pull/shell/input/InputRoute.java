// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.input;

/**
 * Ownership requested by the reloadable controller core for a physical input.
 */
public enum InputRoute
{
    /** Stable-owned controller behavior receives the input. */
    NONE,
    /** Stable-owned behavior runs and the reloadable consumer observes the input. */
    OBSERVE,
    /** Core observes the input now; stable dispatch waits for an explicit release barrier. */
    DEFER_STABLE,
    /** Core observes the input while the established stable mutation is temporarily suppressed. */
    SUPPRESS_STABLE,
    /** Only the reloadable consumer receives the input. */
    EXCLUSIVE
}
