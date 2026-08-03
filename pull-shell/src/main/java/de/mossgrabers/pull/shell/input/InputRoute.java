// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.shell.input;

/**
 * Ownership requested by the reloadable controller core for a physical input.
 */
public enum InputRoute
{
    /** The stable legacy command receives the input. */
    NONE,
    /** The stable legacy command runs and the reloadable consumer observes the input. */
    OBSERVE,
    /** Only the reloadable consumer receives the input. */
    EXCLUSIVE
}
