// (c) 2026
// Licensed under LGPLv3 - http://www.gnu.org/licenses/lgpl-3.0.txt

package de.mossgrabers.pull.core.api.event;

/**
 * Phase of one normalized controller input.
 */
public enum InputPhase
{
    /** Begin a discrete or touched gesture. */
    BEGIN,

    /** Update a continuous gesture. */
    UPDATE,

    /** Continue a discrete or touched gesture beyond the shell's long-press threshold. */
    LONG,

    /** End a discrete or touched gesture. */
    END
}
