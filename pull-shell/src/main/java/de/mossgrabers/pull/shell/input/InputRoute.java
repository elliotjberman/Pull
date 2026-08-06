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
    /** Only the reloadable consumer receives the input. */
    EXCLUSIVE
}
